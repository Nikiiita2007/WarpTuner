package com.example.warptuner

interface SettingsStore {
    var onboarded: Boolean
    var watchdog: Boolean
    var tuneMode: String
}

internal class StoreSettingsAdapter(private val store: Store) : SettingsStore {
    override var onboarded: Boolean
        get() = store.onboarded
        set(value) { store.onboarded = value }

    override var watchdog: Boolean
        get() = store.watchdog
        set(value) { store.watchdog = value }

    override var tuneMode: String
        get() = store.tuneMode
        set(value) { store.tuneMode = value }
}
