package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPreferencesTest {
    private class MemorySettings : SettingsStore {
        override var onboarded = false
        override var watchdog = true
        override var tuneMode = TuneMode.NORMAL.name
    }

    @Test
    fun firstLaunchOpensOnboarding() {
        assertEquals(AppScreen.ONBOARDING, MainPreferences(MemorySettings()).initialScreen)
    }

    @Test
    fun finishingOnboardingPersistsAcrossInstances() {
        val store = MemorySettings()
        assertEquals(AppScreen.MAIN, MainPreferences(store).finishOnboarding())
        assertTrue(store.onboarded)
        assertEquals(AppScreen.MAIN, MainPreferences(store).initialScreen)
    }

    @Test
    fun selectingModePersistsEnumName() {
        val store = MemorySettings()
        MainPreferences(store).setTuneMode(TuneMode.FAST)
        assertEquals(TuneMode.FAST.name, store.tuneMode)
    }
}
