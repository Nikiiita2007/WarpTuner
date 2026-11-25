package com.example.warptuner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class I1FallbackControlsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Application
    private lateinit var prefs: SharedPreferences
    private lateinit var store: Store
    private lateinit var repository: I1FallbackRepository
    private lateinit var previous: Map<String, String?>
    private val keys = listOf("cfg", "account", "i1_fallback")
    private val account = "synthetic-ui-account"
    private val profile = "synthetic-current-profile"
    private val backup = "<r 32>"

    private class MemorySettings : SettingsStore {
        override var onboarded = true
        override var watchdog = false
        override var tuneMode = TuneMode.NORMAL.name
    }

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        prefs = app.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        previous = keys.associateWith { prefs.getString(it, null) }
        store = Store(app)
        repository = I1FallbackRepository(app)
        repository.clear()
        store.account = account
        store.cfg = profile
    }

    @After fun restore() {
        if (!::prefs.isInitialized || !::previous.isInitialized) return
        prefs.edit().apply {
            previous.forEach { (key, value) ->
                if (value == null) remove(key) else putString(key, value)
            }
        }.commit()
    }

    private fun show(busy: Boolean = false) {
        val vm = MainViewModel(app, store, MainPreferences(MemorySettings()))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    I1FallbackControls(vm, busy)
                }
            }
        }
    }

    private fun openDisableDialog() {
        compose.onNodeWithText("Отключить автоподбор").performScrollTo().performClick()
        compose.onNodeWithText("Отключить автоподбор?").assertExists()
    }

    @Test fun importedProfileExplainsUnavailableControls() {
        store.account = ""
        show()
        compose.onNodeWithText("Недоступен для импортированного или ручного профиля.").assertExists()
        compose.onNodeWithText("Выбрать рабочий .conf").assertIsNotEnabled()
        compose.onNodeWithText("Отключить автоподбор").assertIsNotEnabled()
        assertEquals(profile, store.cfg)
    }

    @Test fun busyOperationDisablesSelectionAndRemoval() {
        repository.save(account, backup)
        show(busy = true)
        compose.onNodeWithText("Выбрать другой рабочий файл").assertIsNotEnabled()
        compose.onNodeWithText("Отключить автоподбор").assertIsNotEnabled()
        compose.onNodeWithText("Настройка временно недоступна: выполняется другая операция.").assertExists()
        assertEquals(backup, repository.read(account))
    }

    @Test fun cancellingDisableKeepsBackupAndProfile() {
        repository.save(account, backup)
        show()
        openDisableDialog()
        compose.onNodeWithText("Оставить включённым").performClick()
        compose.onNodeWithText("Отключить автоподбор?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(backup, repository.read(account))
            assertEquals(profile, store.cfg)
            assertEquals(account, store.account)
        }
    }

    @Test fun confirmingDisableClearsOnlyBackup() {
        repository.save(account, backup)
        show()
        openDisableDialog()
        compose.onNodeWithText("Отключить", substring = false).performClick()
        compose.onNodeWithText("Отключить автоподбор?").assertDoesNotExist()
        compose.onNodeWithText("Не настроен. Подключение использует текущий профиль.").assertExists()
        compose.runOnIdle {
            assertEquals(null, repository.read(account))
            assertEquals(profile, store.cfg)
            assertEquals(account, store.account)
        }
    }

    @Test fun confirmationDialogSurvivesSavedStateRestoration() {
        repository.save(account, backup)
        val vm = MainViewModel(app, store, MainPreferences(MemorySettings()))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    I1FallbackControls(vm, busy = false)
                }
            }
        }
        openDisableDialog()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Отключить автоподбор?").assertExists()
        compose.onNodeWithText("Оставить включённым").performClick()
        compose.runOnIdle {
            assertEquals(backup, repository.read(account))
            assertEquals(profile, store.cfg)
        }
    }
}
