package com.example.warptuner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelTest {
    private class MemorySettings : SettingsStore {
        override var onboarded = false
        override var watchdog = true
        override var tuneMode = TuneMode.NORMAL.name
    }

    private fun createViewModel(settings: MemorySettings): MainViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return MainViewModel(app, Store(app), MainPreferences(settings))
    }

    @Test
    fun initialScreenReflectsSavedOnboardingFlag() {
        val settings = MemorySettings()
        assertEquals(AppScreen.ONBOARDING, createViewModel(settings).state.value.screen)
        settings.onboarded = true
        assertEquals(AppScreen.MAIN, createViewModel(settings).state.value.screen)
    }

    @Test
    fun finishingOnboardingUpdatesScreenAndSettings() {
        val settings = MemorySettings()
        val vm = createViewModel(settings)
        vm.finishOnboarding()
        assertEquals(AppScreen.MAIN, vm.state.value.screen)
        assertTrue(settings.onboarded)
    }

    @Test
    fun selectingTuneModeUpdatesStateAndSettings() {
        val settings = MemorySettings()
        val vm = createViewModel(settings)
        val selected = TuneMode.FAST.name
        vm.setTuneMode(selected)
        assertEquals(selected, vm.state.value.tuneMode)
        assertEquals(selected, settings.tuneMode)
        assertEquals(selected, createViewModel(settings).state.value.tuneMode)
    }
}
