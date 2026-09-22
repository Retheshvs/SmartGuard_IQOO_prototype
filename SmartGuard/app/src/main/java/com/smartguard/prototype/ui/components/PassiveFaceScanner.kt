package com.smartguard.prototype.ui.components

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.smartguard.prototype.identity.FaceDetectionEvent
import java.util.concurrent.Executors

private const val TAG = "PassiveFaceScanner"

/**
 * Headless CameraX component that runs ML Kit face detection in the background
 * without rendering a live camera preview.
 *
 * Used by [UnlockScreen] to provide a passive, non-intrusive verification experience.
 */
@Composable
fun PassiveFaceScanner(
    onFaceDetectionEvent: (FaceDetectionEvent) -> Unit,
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mlKitDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var released = false

        cameraProviderFuture.addListener({
            if (released) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()

                // Bind only ImageAnalysis — NO Preview use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { proxy ->
                            processFrame(proxy, mlKitDetector, onFaceDetectionEvent)
                        }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
                Log.d(TAG, "Passive face scanning active (Headless CameraX bound)")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                onCameraError(e.message ?: "Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            released = true
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
            try { mlKitDetector.close() } catch (_: Exception) {}
            Log.d(TAG, "Passive face scanning released")
        }
    }
}

@ExperimentalGetImage
private fun processFrame(
    imageProxy: ImageProxy,
    detector: FaceDetector,
    onResult: (FaceDetectionEvent) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    val inputImage = try {
        InputImage.fromMediaImage(mediaImage, rotation)
    } catch (e: Exception) {
        imageProxy.close()
        return
    }

    detector.process(inputImage)
        .addOnSuccessListener { faces ->
            onResult(
                FaceDetectionEvent(
                    faceCount = faces.size,
                    boundingBoxes = faces.map { it.boundingBox },
                    faces = faces,
                    imageWidth = imageProxy.width,
                    imageHeight = imageProxy.height
                )
            )
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Face detection failed: ${e.message}")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
