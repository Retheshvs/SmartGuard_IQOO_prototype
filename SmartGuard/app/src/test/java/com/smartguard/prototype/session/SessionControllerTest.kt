package com.smartguard.prototype.session

import com.smartguard.prototype.identity.IdentityResult
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionControllerTest {

    private lateinit var controller: SessionController
    private val testProfileAlex = Profile(
        id = "alex_001",
        name = "Alex",
        dateOfBirth = 573609600000L,
        role = Role.ADULT,
        faceImagePath = null,
        faceEmbedding = FloatArray(128) { 0.1f },
        fingerprintEnrolled = false,
        childPinHash = null,
        isAdmin = true,
        screenTimeBudgetMinutes = 999,
        remainingScreenTimeMinutes = 999
    )

    private val testProfileJamie = Profile(
        id = "jamie_001",
        name = "Jamie",
        dateOfBirth = 1469059200000L,
        role = Role.CHILD,
        faceImagePath = null,
        faceEmbedding = FloatArray(128) { 0.2f },
        fingerprintEnrolled = false,
        childPinHash = null,
        isAdmin = false,
        screenTimeBudgetMinutes = 60,
        remainingScreenTimeMinutes = 60
    )

    @Before
    fun setUp() {
        controller = SessionController()
    }

    @Test
    fun testTwoConsecutiveMatchesRequiredToConfirm() {
        val matchResult = IdentityResult.Matched(testProfileAlex, 0.95f)

        // First frame: should be Confirming (1/2)
        val decision1 = controller.processResult(matchResult)
        assertTrue(decision1 is SessionDecision.Confirming)
        assertEquals(1, (decision1 as SessionDecision.Confirming).currentCount)

        // Second consecutive frame with same identity: should be Confirmed!
        val decision2 = controller.processResult(matchResult)
        assertTrue(decision2 is SessionDecision.Confirmed)
        assertEquals(testProfileAlex.id, (decision2 as SessionDecision.Confirmed).result.identityKey())
    }

    @Test
    fun testConflictingFramesResetBuffer() {
        val alexResult = IdentityResult.Matched(testProfileAlex, 0.95f)
        val jamieResult = IdentityResult.Matched(testProfileJamie, 0.90f)

        // First frame: Alex (1/2)
        val decision1 = controller.processResult(alexResult)
        assertTrue(decision1 is SessionDecision.Confirming)

        // Second frame: Jamie (conflict -> resets and counts Jamie as 1/2)
        val decision2 = controller.processResult(jamieResult)
        assertTrue(decision2 is SessionDecision.Confirming)
        assertEquals(1, (decision2 as SessionDecision.Confirming).currentCount)

        // Third frame: Jamie again -> Confirmed!
        val decision3 = controller.processResult(jamieResult)
        assertTrue(decision3 is SessionDecision.Confirmed)
    }

    @Test
    fun testMultipleFacesResetsBuffer() {
        val alexResult = IdentityResult.Matched(testProfileAlex, 0.95f)
        controller.processResult(alexResult)

        // Multiple faces seen -> resets buffer
        val decision = controller.processResult(IdentityResult.MultipleFaces)
        assertTrue(decision is SessionDecision.Confirming)
        assertEquals(0, (decision as SessionDecision.Confirming).currentCount)
    }
}
