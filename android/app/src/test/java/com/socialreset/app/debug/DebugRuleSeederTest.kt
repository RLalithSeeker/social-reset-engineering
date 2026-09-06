package com.socialreset.app.debug

import com.socialreset.app.rules.BlockedApp
import com.socialreset.app.rules.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRuleSeederTest {
    @Test
    fun seedSettingsRuleCreatesAlwaysActiveSettingsRule() = runTest {
        val rules = FakeRuleRepository()
        val seeder = DebugRuleSeeder(rules)

        val seeded = seeder.seedSettingsRule()

        assertEquals("com.android.settings", seeded.packageName)
        assertEquals("debug-settings-v1", seeded.policyId)
        assertEquals(120, seeded.unlockSeconds)
        assertTrue(seeded.schedules.isEmpty())
        assertEquals(seeded, rules.snapshot().single())
    }

    @Test
    fun seedYouTubeRuleCreatesAlwaysActiveYouTubeRule() = runTest {
        val rules = FakeRuleRepository()
        val seeder = DebugRuleSeeder(rules)

        val seeded = seeder.seedYouTubeRule()

        assertEquals("com.google.android.youtube", seeded.packageName)
        assertEquals("debug-youtube-v1", seeded.policyId)
        assertEquals(120, seeded.unlockSeconds)
        assertTrue(seeded.schedules.isEmpty())
        assertEquals(seeded, rules.snapshot().single())
    }

    @Test
    fun seedYouTubeRuleRemovesOldSettingsDebugRule() = runTest {
        val rules = FakeRuleRepository()
        val seeder = DebugRuleSeeder(rules)
        seeder.seedSettingsRule()

        seeder.seedYouTubeRule()

        assertEquals(listOf("com.google.android.youtube"), rules.snapshot().map { it.packageName })
    }

    @Test
    fun clearSettingsRuleRemovesTheSeededPackage() = runTest {
        val rules = FakeRuleRepository()
        val seeder = DebugRuleSeeder(rules)
        seeder.seedSettingsRule()

        seeder.clearSettingsRule()

        assertTrue(rules.snapshot().isEmpty())
    }

    @Test
    fun clearYouTubeRuleRemovesTheSeededPackage() = runTest {
        val rules = FakeRuleRepository()
        val seeder = DebugRuleSeeder(rules)
        seeder.seedYouTubeRule()

        seeder.clearYouTubeRule()

        assertTrue(rules.snapshot().isEmpty())
    }

    private class FakeRuleRepository : RuleRepository {
        private val apps = mutableListOf<BlockedApp>()

        override fun observe(): Flow<List<BlockedApp>> = flowOf(snapshot())

        override fun snapshot(): List<BlockedApp> = apps.toList()

        override suspend fun upsert(app: BlockedApp) {
            apps.removeAll { it.packageName == app.packageName }
            apps += app
        }

        override suspend fun remove(packageName: String) {
            apps.removeAll { it.packageName == packageName }
        }
    }
}
