package com.smartguard.prototype.profile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AgeCalculatorTest {

    @Test
    fun testSuggestedRoleForChild() {
        val childDob = LocalDate.now().minusYears(8)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val role = AgeCalculator.suggestedRole(childDob)
        assertEquals(Role.CHILD, role)
    }

    @Test
    fun testSuggestedRoleForTeen() {
        val teenDob = LocalDate.now().minusYears(15)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val role = AgeCalculator.suggestedRole(teenDob)
        assertEquals(Role.TEEN, role)
    }

    @Test
    fun testSuggestedRoleForAdult() {
        val adultDob = LocalDate.now().minusYears(30)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val role = AgeCalculator.suggestedRole(adultDob)
        assertEquals(Role.ADULT, role)
    }
}
