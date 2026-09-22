package com.smartguard.prototype.identity

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility for generating, comparing, and normalizing 128-dimensional face embedding vectors.
 *
 * Provides:
 * - Cosine similarity calculation (recommended for normalized embeddings)
 * - Euclidean distance calculation
 * - L2 vector normalization
 * - High-resolution geometric landmark embedding extraction as on-device fallback
 */
@Singleton
class FaceEmbeddingExtractor @Inject constructor() {

    companion object {
        const val EMBEDDING_SIZE = 128
    }

    /**
     * Extracts a normalized 128-float embedding from ML Kit facial landmarks.
     */
    fun extract(face: Face): FloatArray {
        val embedding = FloatArray(EMBEDDING_SIZE) { 0f }
        val box = face.boundingBox
        val width = box.width().toFloat().coerceAtLeast(1f)
        val height = box.height().toFloat().coerceAtLeast(1f)

        val landmarkTypes = listOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK
        )

        // 1. Normalized Coordinates (2 values per landmark = 20 total)
        var index = 0
        val points = mutableListOf<PointF?>()

        for (type in landmarkTypes) {
            val landmark = face.getLandmark(type)
            if (landmark != null) {
                val p = landmark.position
                points.add(p)
                embedding[index++] = (p.x - box.left) / width
                embedding[index++] = (p.y - box.top) / height
            } else {
                points.add(null)
                embedding[index++] = 0.5f // Default center
                embedding[index++] = 0.5f
            }
        }

        // 2. Pairwise Distances normalized by inter-eye distance
        val pLeftEye = points[0]
        val pRightEye = points[1]
        val pNose = points[2]
        val pMouthL = points[3]
        val pMouthR = points[4]
        val pMouthB = points[5]
        
        val interEyeDist = if (pLeftEye != null && pRightEye != null) {
            distance(pLeftEye, pRightEye).coerceAtLeast(0.05f)
        } else {
            width * 0.35f
        }

        // Add specific facial ratios for better distinctness
        if (pLeftEye != null && pRightEye != null && pNose != null) {
            val eyeToNoseL = distance(pLeftEye, pNose) / interEyeDist
            val eyeToNoseR = distance(pRightEye, pNose) / interEyeDist
            embedding[index++] = eyeToNoseL
            embedding[index++] = eyeToNoseR
        } else {
            index += 2
        }

        if (pMouthL != null && pMouthR != null && pNose != null) {
            val mouthWidth = distance(pMouthL, pMouthR) / interEyeDist
            val noseToMouth = distance(pNose, pMouthB ?: pMouthL) / interEyeDist
            embedding[index++] = mouthWidth
            embedding[index++] = noseToMouth
        } else {
            index += 2
        }

        // Fill remaining with all-pairs distances
        for (i in points.indices) {
            val p1 = points[i] ?: continue
            for (j in i + 1 until points.size) {
                if (index >= EMBEDDING_SIZE) break
                val p2 = points[j] ?: continue
                embedding[index++] = distance(p1, p2) / interEyeDist
            }
        }

        return normalize(embedding)
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    /**
     * Normalizes a vector to unit length (L2 norm = 1.0).
     */
    fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v)
        }
        val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-6f)
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / norm
        }
        return normalized
    }

    /**
     * Calculates Cosine Similarity between two embeddings.
     * Returns a score between -1.0 and 1.0 (1.0 = identical direction).
     */
    fun cosineSimilarity(e1: FloatArray, e2: FloatArray): Float {
        if (e1.size != e2.size || e1.isEmpty()) return -1f
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in e1.indices) {
            dot += e1[i] * e2[i]
            norm1 += e1[i] * e1[i]
            norm2 += e2[i] * e2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        if (denom < 1e-6f) return 0f
        return (dot / denom).coerceIn(-1f, 1f)
    }

    /**
     * Calculates the Euclidean distance between two embeddings.
     * Lower distance = higher similarity.
     */
    fun compare(e1: FloatArray, e2: FloatArray): Float {
        if (e1.size != e2.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in e1.indices) {
            sum += (e1[i] - e2[i]).pow(2)
        }
        return sqrt(sum)
    }

    /**
     * Averages multiple normalized embeddings into a single mean embedding and re-normalizes it.
     */
    fun average(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(EMBEDDING_SIZE)
        val mean = FloatArray(EMBEDDING_SIZE)
        for (e in embeddings) {
            for (i in e.indices) {
                mean[i] += e[i]
            }
        }
        for (i in mean.indices) {
            mean[i] /= embeddings.size.toFloat()
        }
        return normalize(mean)
    }
}
