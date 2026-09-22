package com.smartguard.prototype.identity

import android.util.Log
import com.smartguard.prototype.debug.DebugStateManager
import com.smartguard.prototype.debug.SimulatedPersona
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real face profile matcher for SmartGuard.
 *
 * Automatically compares live face embeddings against all enrolled profiles in Room DB
 * using Cosine Similarity (with fallback to Euclidean distance).
 *
 * ## Match Pipeline:
 * 1. Face Detection Check: Rejects 0 or >1 faces
 * 2. Embedding Extraction: Runs live face through [FaceRecognitionPipeline]
 * 3. Database Scan: Queries all enrolled [Profile] records in Room
 * 4. Multi-Profile Comparison: Computes similarity against all stored embeddings
 * 5. Threshold Verification: Confirms best match clears [MATCH_THRESHOLD]
 * 6. Decision Resolution: Produces [IdentityResult.Matched] or [IdentityResult.Unknown]
 */
@Singleton
class ProfileMatcher @Inject constructor(
    private val debugStateManager: DebugStateManager,
    private val profileRepository: ProfileRepository,
    private val embeddingExtractor: FaceEmbeddingExtractor,
    private val recognitionPipeline: FaceRecognitionPipeline
) {
    companion object {
        private const val TAG = "ProfileMatcher"

        /**
         * Named, easily tunable threshold for face matching.
         * Using Cosine Similarity on normalized vectors:
         * - Score range: -1.0 to 1.0 (1.0 = identical face embedding)
         * - 0.55f - 0.70f is the recommended range for on-device embedding verification.
         */
        const val MATCH_THRESHOLD = 0.55f
    }

    /**
     * Resolves the identity for a live face detection event.
     * Logs each stage of the pipeline to Logcat.
     */
    suspend fun match(event: FaceDetectionEvent): IdentityResult {
        val faceCount = event.faceCount
        val faces = event.faces

        // Stage 1: Face Detection Check
        if (faceCount == 0 || faces.isEmpty()) {
            Log.d(TAG, "[Stage 1] No face detected in camera frame.")
            return IdentityResult.NoFaceDetected
        }

        if (faceCount > 1) {
            Log.w(TAG, "[Stage 1] Multiple faces detected ($faceCount) — refusing match for security.")
            return IdentityResult.MultipleFaces
        }

        val liveFace = faces[0]
        Log.d(TAG, "[Stage 1] Single face detected at bounds: ${liveFace.boundingBox}")

        // Stage 2: Embedding Generation
        val liveEmbedding = recognitionPipeline.getEmbedding(liveFace)
        Log.d(TAG, "[Stage 2] Face embedding generated (${liveEmbedding.size}-d vector).")

        // Stage 3 & 4: Multi-Profile Comparison against Room DB
        val allProfiles = profileRepository.getAllProfiles()
        val enrolledWithEmbeddings = allProfiles.filter { it.faceEmbedding != null }
        Log.d(TAG, "[Stage 3] Comparing live face against ${enrolledWithEmbeddings.size} enrolled profile(s) in Room DB (Total profiles: ${allProfiles.size}).")

        var bestMatch: Profile? = null
        var bestSimilarity = -1.0f
        
        var secondBestMatch: Profile? = null
        var secondBestSimilarity = -1.0f

        for (profile in enrolledWithEmbeddings) {
            val storedEmbedding = profile.faceEmbedding!!
            val similarity = embeddingExtractor.cosineSimilarity(liveEmbedding, storedEmbedding)
            
            Log.v(
                TAG,
                "[Stage 4] Detail: '${profile.name}' score = ${"%.4f".format(similarity)}"
            )

            if (similarity > bestSimilarity) {
                secondBestSimilarity = bestSimilarity
                secondBestMatch = bestMatch
                
                bestSimilarity = similarity
                bestMatch = profile
            } else if (similarity > secondBestSimilarity) {
                secondBestSimilarity = similarity
                secondBestMatch = profile
            }
        }

        // Stage 5 & 6: Threshold Check & Decision
        if (bestMatch != null) {
            val passedThreshold = bestSimilarity >= MATCH_THRESHOLD
            Log.i(
                TAG,
                "[Stage 5] MATCH ATTEMPT: " +
                        "Winner: '${bestMatch.name}' (${"%.4f".format(bestSimilarity)}), " +
                        "Runner-up: '${secondBestMatch?.name ?: "None"}' (${"%.4f".format(secondBestSimilarity)}). " +
                        "Threshold: ${"%.2f".format(MATCH_THRESHOLD)} -> Result: ${if (passedThreshold) "PASS" else "FAIL"}"
            )

            if (passedThreshold) {
                Log.i(TAG, "[Stage 6] FINAL DECISION: MATCHED -> '${bestMatch.name}'")
                return IdentityResult.Matched(bestMatch, confidence = bestSimilarity.coerceIn(0f, 1f))
            }
        }
else {
            Log.w(TAG, "[Stage 5] No enrolled profiles with face embeddings found in database.")
        }

        // Isolated Debug Fallback (Only active if Demo Mode is explicitly enabled in Debug Panel)
        if (debugStateManager.isDemoModeActive.value) {
            val simulated = resolveDebugSimulationFallback()
            if (simulated is IdentityResult.Matched) {
                Log.w(TAG, "[Stage 6] DEMO MODE FALLBACK: Simulated identity '${simulated.profile.name}' applied.")
                return simulated
            }
        }

        Log.i(TAG, "[Stage 6] FINAL DECISION: UNKNOWN face (Did not match any enrolled profile above threshold).")
        return IdentityResult.Unknown
    }

    private suspend fun resolveDebugSimulationFallback(): IdentityResult {
        val persona = debugStateManager.selectedPersona.value
        if (persona == SimulatedPersona.UNKNOWN) return IdentityResult.Unknown

        return when (persona) {
            SimulatedPersona.PARENT_ALEX -> {
                val profile = profileRepository.getAdminProfile()
                if (profile != null) IdentityResult.Matched(profile, 0.95f) else IdentityResult.Unknown
            }
            SimulatedPersona.CHILD_JAMIE -> {
                val profile = profileRepository.getChildProfile()
                if (profile != null) IdentityResult.Matched(profile, 0.95f) else IdentityResult.Unknown
            }
            SimulatedPersona.UNKNOWN -> IdentityResult.Unknown
        }
    }
}

