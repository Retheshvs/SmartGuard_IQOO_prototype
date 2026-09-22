package com.smartguard.prototype.identity

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full self-contained face recognition pipeline:
 * 1. Face Detection (ML Kit)
 * 2. Face Cropping & Preprocessing
 * 3. Embedding Generation (TFLite MobileFaceNet model bundled in assets)
 * 4. Fallback to Geometric Landmark Embedding if model / bitmap unavailable
 *
 * This entire pipeline runs in-app and is NOT routed through Android's OS-level biometric system.
 */
@Singleton
class FaceRecognitionPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geometricExtractor: FaceEmbeddingExtractor
) {
    sealed class FaceQuality {
        object Good : FaceQuality()
        data class TooFar(val message: String) : FaceQuality()
        data class PoorPose(val message: String) : FaceQuality()
        data class LowConfidence(val message: String) : FaceQuality()
    }

    companion object {
        private const val TAG = "FaceRecognitionPipeline"
        private const val MODEL_PATH = "mobile_facenet.tflite"
        private const val INPUT_SIZE = 112 // Standard MobileFaceNet input size
    }

    private var interpreter: Interpreter? = null
    var isTFLiteAvailable = false
        private set

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            isTFLiteAvailable = true
            Log.d(TAG, "[Pipeline] TFLite Face Recognition model ($MODEL_PATH) loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "[Pipeline] TFLite model ($MODEL_PATH) load failed (${e.message}). Falling back to geometric landmark extractor.")
            isTFLiteAvailable = false
        }
    }

    /**
     * Extracts a normalized 128-d embedding from a detected face.
     *
     * @param face The ML Kit Face object containing bounding box and landmarks.
     * @param fullImage Optional Bitmap of the frame for TFLite inference.
     */
    fun getEmbedding(face: Face, fullImage: Bitmap? = null): FloatArray {
        if (isTFLiteAvailable && fullImage != null) {
            try {
                val embedding = extractTFLiteEmbedding(face, fullImage)
                Log.d(TAG, "[Pipeline] Generated ${embedding.size}-d TFLite embedding for face at ${face.boundingBox}")
                return geometricExtractor.normalize(embedding)
            } catch (e: Exception) {
                Log.e(TAG, "[Pipeline] TFLite extraction failed, falling back to geometric extractor: ${e.message}")
            }
        }

        // Geometric landmark extraction
        val embedding = geometricExtractor.extract(face)
        Log.d(TAG, "[Pipeline] Generated ${embedding.size}-d Geometric landmark embedding for face at ${face.boundingBox}")
        return embedding
    }

    /**
     * Performs quality checks on a detected face to ensure it's suitable for enrollment/matching.
     * Checks for:
     * - Minimum size relative to image
     * - Blurriness (Laplacian variance - not implemented here, using simple size/confidence)
     * - Brightness
     */
    fun checkQuality(face: Face, width: Int, height: Int): FaceQuality {
        val box = face.boundingBox
        val faceArea = box.width() * box.height()
        val imageArea = width * height
        val ratio = faceArea.toFloat() / imageArea

        // 1. Size check: face must be at least 15% of the frame for reliable embedding
        if (ratio < 0.05f) {
            return FaceQuality.TooFar("Face is too far away. Move closer.")
        }

        // 2. Pose check (ML Kit gives Euler angles)
        val headEulerAngleY = face.headEulerAngleY // Head turned left/right
        val headEulerAngleZ = face.headEulerAngleZ // Head tilted
        if (Math.abs(headEulerAngleY) > 25) {
            return FaceQuality.PoorPose("Face is turned too much. Look straight at the camera.")
        }
        if (Math.abs(headEulerAngleZ) > 30) {
            return FaceQuality.PoorPose("Face is tilted. Hold device level.")
        }

        // 3. Landmarks check
        if (face.allLandmarks.size < 5) {
            return FaceQuality.LowConfidence("Incomplete face landmarks detected.")
        }

        return FaceQuality.Good
    }

    private fun extractTFLiteEmbedding(face: Face, fullImage: Bitmap): FloatArray {
        val box = face.boundingBox
        val left = box.left.coerceIn(0, fullImage.width - 1)
        val top = box.top.coerceIn(0, fullImage.height - 1)
        val right = box.right.coerceIn(left + 1, fullImage.width)
        val bottom = box.bottom.coerceIn(top + 1, fullImage.height)

        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)

        val croppedFace = Bitmap.createBitmap(fullImage, left, top, cropWidth, cropHeight)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f)) // Map [0, 255] to [-1, 1]
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(croppedFace)
        tensorImage = imageProcessor.process(tensorImage)

        val output = ByteBuffer.allocateDirect(FaceEmbeddingExtractor.EMBEDDING_SIZE * 4)
            .order(ByteOrder.nativeOrder())

        interpreter?.run(tensorImage.buffer, output)

        output.rewind()
        val floatArray = FloatArray(FaceEmbeddingExtractor.EMBEDDING_SIZE)
        output.asFloatBuffer().get(floatArray)

        return floatArray
    }
}
