package com.example.warptuner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelOwnershipTest {
    private lateinit var app: Application
    private lateinit var prefs: SharedPreferences
    private lateinit var store: Store
    private lateinit var vm: MainViewModel
    private var previousCfg: String? = null
    private var previousWatch = ""
    private var previousTune: TuneUi? = null
    private var recoveryToken: Any? = null
    private var reservation: String? = null
    private val savedProfile = "synthetic-saved-profile"
    private val editedProfile = "synthetic-edited-profile"
    private val watchMarker = "synthetic-watchdog-recovery"

    private class MemorySettings : SettingsStore {
        override var onboarded = true
        override var watchdog = false
        override var tuneMode = TuneMode.NORMAL.name
    }

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        prefs = app.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        previousCfg = prefs.getString("cfg", null)
        previousWatch = WatchState.text.value
        previousTune = TuneState.ui.value
        store = Store(app)
        store.cfg = savedProfile
        onMain {
            TuneState.update { it.copy(running = false) }
            WatchState.set(watchMarker)
            vm = MainViewModel(app, store, MainPreferences(MemorySettings()))
            vm.setCfg(editedProfile)
        }
    }

    @After fun restore() {
        reservation?.let { tuneLaunchHandoff.cancel(it) }
        recoveryToken?.let { tunnelOperationCoordinator.release(it) }
        if (::prefs.isInitialized) {
            prefs.edit().apply {
                if (previousCfg == null) remove("cfg") else putString("cfg", previousCfg)
            }.commit()
        }
        onMain {
            WatchState.set(previousWatch)
            previousTune?.let { saved -> TuneState.update { saved } }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    private fun ownTunnelAsRecovery() {
        val snapshot = checkNotNull(tunnelOperationCoordinator.idleSnapshot())
        recoveryToken = checkNotNull(tunnelOperationCoordinator.tryAcquire(snapshot))
    }

    @Test fun rejectedTuneLaunchDoesNotSaveEditedProfileOrStopWatchdog() {
        ownTunnelAsRecovery()
        onMain {
            vm.startTune()
            assertEquals(savedProfile, store.cfg)
            assertEquals(editedProfile, vm.state.value.cfg)
            assertEquals(watchMarker, WatchState.text.value)
            assertEquals("Туннель занят другой операцией. Подбор не запущен.", vm.state.value.notice)
            assertFalse(TuneState.ui.value.running)
            assertNull(vm.state.value.operation)
        }
    }

    @Test fun rejectedDisconnectDoesNotEnterForegroundOperationOrStopWatchdog() {
        ownTunnelAsRecovery()
        onMain {
            vm.disconnect()
            assertEquals(watchMarker, WatchState.text.value)
            assertEquals(savedProfile, store.cfg)
            assertEquals("Туннель занят другой операцией. Повторите попытку.", vm.state.value.notice)
            assertNull(vm.state.value.operation)
        }
    }

    @Test fun pendingTuneReservationBlocksProfileEditingBeforeServiceStarts() {
        reservation = checkNotNull(tuneLaunchHandoff.reserve())
        onMain {
            assertFalse(TuneState.ui.value.running)
            assertTrue(vm.configEditingBlocked())
            assertEquals("Дождитесь завершения текущей операции.", vm.saveManualConfig(editedProfile))
            assertEquals(savedProfile, store.cfg)
        }
        tuneLaunchHandoff.cancel(checkNotNull(reservation))
        reservation = null
        onMain { assertFalse(vm.configEditingBlocked()) }
    }
}
