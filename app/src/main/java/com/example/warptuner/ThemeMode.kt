package com.example.warptuner

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(name: String?): ThemeMode = values().firstOrNull { it.name == name } ?: SYSTEM
    }
}

internal class ThemePreference(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY, null))
        set(value) { prefs.edit().putString(KEY, value.name).apply() }

    private companion object {
        const val KEY = "theme_mode"
    }
}
