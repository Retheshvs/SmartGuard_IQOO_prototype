package com.smartguard.prototype.identity

import android.graphics.Rect
import com.google.mlkit.vision.face.Face

/**
 * Raw output from the ML Kit face detection pipeline for a single camera frame.
 *
 * @param faceCount    Number of faces detected in this frame
 * @param boundingBoxes Bounding rects for each detected face (image coordinate space)
 * @param faces        The raw ML Kit Face objects
 * @param imageWidth   Width of the analyzed image in pixels
 * @param imageHeight  Height of the analyzed image in pixels
 */
data class FaceDetectionEvent(
    val faceCount: Int,
    val boundingBoxes: List<Rect> = emptyList(),
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 480,
    val imageHeight: Int = 640
)

