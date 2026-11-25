package com.example.warptuner

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RussianAppBypassSettingsTest {
    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences
    private var previousEnabled: Boolean? = null
    private var previousOverrides: Set<String>? = null
    private val app = "com.vkontakte.android"
    private val other = "ru.yandex.taxi"

    @Before fun prepare() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences("russian_app_bypass", Context.MODE_PRIVATE)
        previousEnabled = if (prefs.contains("enabled")) prefs.getBoolean("enabled", true) else null
        previousOverrides = if (prefs.contains("not_automatic")) {
            prefs.getStringSet("not_automatic", emptySet()).orEmpty().toSet()
        } else null
        check(prefs.edit().remove("enabled").remove("not_automatic").commit())
    }

    @After fun restore() {
        if (!::prefs.isInitialized) return
        val editor = prefs.edit()
        val enabled = previousEnabled
        if (enabled == null) editor.remove("enabled") else editor.putBoolean("enabled", enabled)
        val overrides = previousOverrides
        if (overrides == null) editor.remove("not_automatic") else editor.putStringSet("not_automatic", overrides)
        check(editor.commit())
    }

    @Test fun defaultsEnablePolicyWithoutPerAppOverrides() {
        val settings = RussianAppBypassSettings(ctx)
        assertTrue(settings.enabled)
        assertTrue(settings.overrides.isEmpty())
    }

    @Test fun globalSwitchSurvivesSettingsRecreationAndDisablesTransformation() {
        RussianAppBypassSettings(ctx).enabled = false
        assertFalse(RussianAppBypassSettings(ctx).enabled)
        val original = "synthetic unchanged configuration"
        assertEquals(original, russianAppBypassConfig(ctx, original))
        RussianAppBypassSettings(ctx).enabled = true
        assertTrue(RussianAppBypassSettings(ctx).enabled)
    }

    @Test fun perAppOverridesPersistWithoutChangingOtherApplications() {
        val settings = RussianAppBypassSettings(ctx)
        settings.setAutomatic(app, false)
        settings.setAutomatic(other, false)
        assertEquals(setOf(app, other), RussianAppBypassSettings(ctx).overrides)
        settings.setAutomatic(app, true)
        assertEquals(setOf(other), RussianAppBypassSettings(ctx).overrides)
        assertTrue(settings.enabled)
    }

    @Test fun togglingGlobalPolicyPreservesIndependentOverrideSnapshots() {
        val settings = RussianAppBypassSettings(ctx)
        settings.setAutomatic(app, false)
        val before = settings.overrides
        settings.enabled = false
        settings.enabled = true
        settings.setAutomatic(other, false)
        assertEquals(setOf(app), before)
        assertEquals(setOf(app, other), RussianAppBypassSettings(ctx).overrides)
    }
}
