package com.smartguard.prototype.ui.components

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.smartguard.prototype.identity.FaceDetectionEvent
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"

/**
 * Composable that renders a CameraX preview and continuously runs ML Kit face detection
 * on each frame via [ImageAnalysis].
 *
 * ## Lifecycle guarantees
 * - Camera is bound in [DisposableEffect]; released in the `onDispose` callback.
 * - [onDispose] fires on composition leave (navigation pop, screen off, app background) —
 *   satisfying the "camera released within ~1s" acceptance criterion.
 * - A single-thread [Executors.newSingleThreadExecutor] runs image analysis off the main thread.
 * - [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] prevents frame queue buildup.
 *
 * ## Face detection
 * Results from ML Kit are delivered via [onFaceDetectionEvent] callback.
 * Only face counts and bounding boxes are forwarded — no embedding work here.
 * Identity resolution is done in [ProfileMatcher] after this callback fires.
 */
private var frameCounter = 0

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFaceDetectionEvent: (FaceDetectionEvent) -> Unit,
    onCameraError: (String) -> Unit = {}
) {
    Log.d(TAG, "CHECKPOINT 3: CameraPreview composed")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

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
                Log.d(TAG, "CHECKPOINT 4: ProcessCameraProvider resolved")

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "CHECKPOINT 5: Camera use cases bound to lifecycle (Preview + ImageAnalysis)")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                onCameraError(e.message ?: "Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            released = true
            try {
                cameraProvider?.unbindAll()
                Log.d(TAG, "Camera released — onDispose")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing camera in onDispose", e)
            }
            analysisExecutor.shutdown()
            try { mlKitDetector.close() } catch (_: Exception) {}
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun processFrame(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    onResult: (FaceDetectionEvent) -> Unit
) {
    val currentFrame = ++frameCounter
    if (currentFrame == 1 || currentFrame % 30 == 0) {
        Log.d(TAG, "CHECKPOINT 6: Analyzer receiving frames (frame #$currentFrame, size: ${imageProxy.width}x${imageProxy.height})")
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val imageWidth  = imageProxy.width
    val imageHeight = imageProxy.height
    val rotation    = imageProxy.imageInfo.rotationDegrees

    val inputImage = try {
        InputImage.fromMediaImage(mediaImage, rotation)
    } catch (e: Exception) {
        imageProxy.close()
        Log.w(TAG, "Failed to create InputImage: ${e.message}")
        return
    }

    detector.process(inputImage)
        .addOnSuccessListener { faces ->
            onResult(
                FaceDetectionEvent(
                    faceCount    = faces.size,
                    boundingBoxes = faces.map { it.boundingBox },
                    faces        = faces,
                    imageWidth   = imageWidth,
                    imageHeight  = imageHeight
                )
            )
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "ML Kit face detection failed: ${e.message}")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
