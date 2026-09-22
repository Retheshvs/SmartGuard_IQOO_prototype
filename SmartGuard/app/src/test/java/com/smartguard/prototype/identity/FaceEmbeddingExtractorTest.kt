package com.smartguard.prototype.identity

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class FaceEmbeddingExtractorTest {

    private val extractor = FaceEmbeddingExtractor()

    @Test
    fun testNormalizeVector() {
        val vector = floatArrayOf(3f, 4f)
        val normalized = extractor.normalize(vector)
        assertEquals(2, normalized.size)
        assertEquals(0.6f, normalized[0], 0.0001f)
        assertEquals(0.8f, normalized[1], 0.0001f)

        // Verify L2 norm is 1.0
        val sumSquares = normalized[0] * normalized[0] + normalized[1] * normalized[1]
        assertEquals(1.0f, sqrt(sumSquares), 0.0001f)
    }

    @Test
    fun testCosineSimilarityIdentical() {
        val v1 = floatArrayOf(0.6f, 0.8f)
        val v2 = floatArrayOf(0.6f, 0.8f)
        val similarity = extractor.cosineSimilarity(v1, v2)
        assertEquals(1.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarityOrthogonal() {
        val v1 = floatArrayOf(1f, 0f)
        val v2 = floatArrayOf(0f, 1f)
        val similarity = extractor.cosineSimilarity(v1, v2)
        assertEquals(0.0f, similarity, 0.0001f)
    }

    @Test
    fun testCosineSimilarityOpposite() {
        val v1 = floatArrayOf(1f, 0f)
        val v2 = floatArrayOf(-1f, 0f)
        val similarity = extractor.cosineSimilarity(v1, v2)
        assertEquals(-1.0f, similarity, 0.0001f)
    }

    @Test
    fun testEuclideanDistance() {
        val v1 = floatArrayOf(0f, 0f)
        val v2 = floatArrayOf(3f, 4f)
        val dist = extractor.compare(v1, v2)
        assertEquals(5.0f, dist, 0.0001f)
    }
}
