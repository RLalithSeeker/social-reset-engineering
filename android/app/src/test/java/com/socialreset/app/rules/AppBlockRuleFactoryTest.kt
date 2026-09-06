package com.socialreset.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockRuleFactoryTest {
    @Test
    fun newRuleBuildsEnabledAlwaysOnRule() {
        val rule = AppBlockRuleFactory.newRule("com.example.app", "Example")

        assertEquals("com.example.app", rule.packageName)
        assertEquals("Example", rule.label)
        assertEquals(AppBlockRuleFactory.DEFAULT_UNLOCK_SECONDS, rule.unlockSeconds)
        assertTrue(rule.enabled)
        assertTrue(rule.schedules.isEmpty())
        assertTrue(rule.policyId.startsWith("local-"))
    }

    @Test
    fun blankLabelFallsBackToPackageName() {
        val rule = AppBlockRuleFactory.newRule("com.example.app", "")

        assertEquals("com.example.app", rule.label)
    }
}
