package com.example.warptuner

internal class MainPreferences(private val settings: SettingsStore) {
    val initialScreen: AppScreen
        get() = if (settings.onboarded) AppScreen.MAIN else AppScreen.ONBOARDING

    fun finishOnboarding(): AppScreen {
        settings.onboarded = true
        return AppScreen.MAIN
    }

    fun setTuneMode(mode: TuneMode) {
        settings.tuneMode = mode.name
    }
}
