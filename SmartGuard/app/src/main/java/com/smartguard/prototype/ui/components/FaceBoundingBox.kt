package com.smartguard.prototype.ui.components

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws face bounding boxes over the camera preview.
 *
 * Coordinates from ML Kit are in image space; we transform them to canvas
 * space accounting for:
 * - 90° rotation (portrait device, landscape sensor)
 * - Horizontal mirror for front camera
 */
@Composable
fun FaceBoundingBoxOverlay(
    faces: List<Rect>,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean = true,
    modifier: Modifier = Modifier,
    boxColor: Color = Color(0xFF4CAF50),
    cornerLength: Dp = 20.dp,
    strokeWidth: Dp = 3.dp
) {
    Canvas(modifier = modifier) {
        if (faces.isEmpty() || imageWidth == 0 || imageHeight == 0) return@Canvas

        // Portrait device: image is rotated 90°, so imageHeight maps to canvas width
        val scaleX = size.width  / imageHeight.toFloat()
        val scaleY = size.height / imageWidth.toFloat()
        val sw     = strokeWidth.toPx()
        val cl     = cornerLength.toPx()

        faces.forEach { face ->
            // Transform from image space (rotated) → canvas space
            val rawL = face.top    * scaleX
            val rawR = face.bottom * scaleX
            val rawT = face.left   * scaleY
            val rawB = face.right  * scaleY

            // Mirror horizontally for front camera
            val l = if (isFrontCamera) (size.width - rawR).coerceIn(0f, size.width)  else rawL.coerceIn(0f, size.width)
            val r = if (isFrontCamera) (size.width - rawL).coerceIn(0f, size.width)  else rawR.coerceIn(0f, size.width)
            val t = rawT.coerceIn(0f, size.height)
            val b = rawB.coerceIn(0f, size.height)
            val w = (r - l).coerceAtLeast(0f)
            val h = (b - t).coerceAtLeast(0f)

            // Full rect (thin)
            drawRect(boxColor.copy(alpha = 0.4f), topLeft = Offset(l, t), size = Size(w, h), style = Stroke(sw * 0.5f))

            // Corner accents (thick)
            val cs = sw * 1.5f
            drawLine(boxColor, Offset(l,     t), Offset(l + cl, t),     cs)
            drawLine(boxColor, Offset(l,     t), Offset(l,     t + cl), cs)
            drawLine(boxColor, Offset(r,     t), Offset(r - cl, t),     cs)
            drawLine(boxColor, Offset(r,     t), Offset(r,     t + cl), cs)
            drawLine(boxColor, Offset(l,     b), Offset(l + cl, b),     cs)
            drawLine(boxColor, Offset(l,     b), Offset(l,     b - cl), cs)
            drawLine(boxColor, Offset(r,     b), Offset(r - cl, b),     cs)
            drawLine(boxColor, Offset(r,     b), Offset(r,     b - cl), cs)
        }
    }
}
