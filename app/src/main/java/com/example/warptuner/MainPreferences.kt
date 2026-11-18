package com.example.warptuner

internal class MainPreferences(private val settings: SettingsStore) {
    val initialScreen: AppScreen
        get() = if (settings.onboarded) AppScreen.MAIN else AppScreen.ONBOARDING

    var watchdog: Boolean
        get() = settings.watchdog
        set(value) { settings.watchdog = value }

    val tuneMode: String
        get() = settings.tuneMode

    fun finishOnboarding(): AppScreen {
        settings.onboarded = true
        return AppScreen.MAIN
    }

    fun setTuneMode(mode: TuneMode) = setTuneMode(mode.name)

    fun setTuneMode(name: String) {
        settings.tuneMode = name
    }
}
