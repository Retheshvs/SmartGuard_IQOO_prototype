package com.smartguard.prototype.identity

import com.google.mlkit.vision.face.FaceDetectorOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides canonical ML Kit [FaceDetectorOptions] for the face-detection pipeline.
 *
 * The CameraX image capture + frame routing is handled in [CameraPreview] composable.
 * This class acts as the single source of truth for detector configuration and
 * prototype constants (timeout, required consecutive detections).
 *
 * In a production build, this class would also manage face embedding models,
 * embedding storage, and the actual comparison logic. For this prototype,
 * "matching" is delegated entirely to [ProfileMatcher] which uses the simulated
 * persona selection from [com.smartguard.prototype.debug.DebugStateManager].
 */
@Singleton
class FaceDetector @Inject constructor() {

    val detectorOptions: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setMinFaceSize(MIN_FACE_SIZE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    companion object {
        /** Minimum face size relative to image width */
        const val MIN_FACE_SIZE = 0.15f

        /**
         * Number of consecutive matching detections required before committing
         * to a mode switch. Prevents single-frame glitches from switching users.
         */
        const val REQUIRED_CONSECUTIVE_DETECTIONS = 2

        /**
         * Camera verification timeout in milliseconds (7 seconds).
         * If no conclusive result in this window → timeout state.
         */
        const val IDENTITY_CONFIRMATION_TIMEOUT_MS = 7_000L
        const val VERIFICATION_TIMEOUT_MS = IDENTITY_CONFIRMATION_TIMEOUT_MS
    }
}
