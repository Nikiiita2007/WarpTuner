package com.example.warptuner

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { ONBOARDING, MAIN, SETTINGS, APPS }
enum class ForegroundOperation { REGISTERING, CONNECTING, DISCONNECTING, CHECKING }

data class MainUiState(
    val screen: AppScreen,
    val cfg: String,
    val notice: String = "",
    val creating: Boolean = false,
    val watchOn: Boolean = true,
    val tuneMode: String = TuneMode.NORMAL.name,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val operation: ForegroundOperation? = null
)

class MainViewModel internal constructor(
    app: Application,
    val store: Store,
    private val preferences: MainPreferences
) : AndroidViewModel(app) {
    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == MainViewModel::class.java) {
                        "Unsupported ViewModel: ${modelClass.name}"
                    }
                    val store = Store(app)
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(app, store, MainPreferences(StoreSettingsAdapter(store))) as T
                }
            }
    }

    private val ctx: Application get() = getApplication()
    private val operationGate = ForegroundOperationGate()
    private val themePreference = ThemePreference(app)
    private val _state = MutableStateFlow(
        MainUiState(
            screen = preferences.initialScreen,
            cfg = store.cfg,
            watchOn = preferences.watchdog,
            tuneMode = preferences.tuneMode,
            themeMode = themePreference.mode
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        if (TuneState.ui.value.outcome == null && store.lastOutcome.isNotBlank()) {
            outcomeFromJson(store.lastOutcome)?.let { o ->
                TuneState.update { it.copy(outcome = o, status = "Последний результат") }
            }
        }
    }

    private fun runOperation(operation: ForegroundOperation, block: suspend () -> Unit) {
        if (TuneState.ui.value.running || !operationGate.tryAcquire()) return
        _state.update { it.copy(operation = operation, creating = operation == ForegroundOperation.REGISTERING) }
        val job = viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(operation = null, creating = false) }
            }
        }
        // Completion also runs when the scope was cancelled before the body started.
        job.invokeOnCompletion {
            _state.update { it.copy(operation = null, creating = false) }
            operationGate.release()
        }
    }

    fun navigate(screen: AppScreen) { _state.update { it.copy(screen = screen) } }
    fun finishOnboarding() { navigate(preferences.finishOnboarding()) }
    fun setCfg(cfg: String) { _state.update { it.copy(cfg = cfg) } }
    fun notify(message: String) { _state.update { it.copy(notice = message) } }

    fun setWatch(on: Boolean) {
        if (operationGate.busy || TuneState.ui.value.running) return
        preferences.watchdog = on
        _state.update { it.copy(watchOn = on) }
        if (!on) WatchdogService.stop(ctx)
    }

    fun setTuneMode(name: String) {
        if (operationGate.busy || TuneState.ui.value.running) return
        preferences.setTuneMode(name)
        _state.update { it.copy(tuneMode = name) }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreference.mode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun createAccount() = runOperation(ForegroundOperation.REGISTERING) {
        notify("Регистрация…")
        try {
            val c = createAccountSmart(ctx, store)
            _state.update { it.copy(cfg = c, notice = "Конфиг создан") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify(friendlyError(e))
        }
    }

    fun connect(text: String) = runOperation(ForegroundOperation.CONNECTING) {
        connectUsingI1(text)
    }

    fun disconnect() = runOperation(ForegroundOperation.DISCONNECTING) {
        try {
            WatchdogService.stop(ctx)
            val st = withContext(Dispatchers.IO) { Engine.down(ctx) }
            notify("Состояние туннеля: $st")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify("Ошибка: ${e.message}")
        }
    }

    fun trace() = runOperation(ForegroundOperation.CHECKING) {
        try {
            notify("Проверяю соединение…")
            notify(withContext(Dispatchers.IO) { Engine.trace() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify("Проверка не прошла: ${e.message}")
        }
    }

    fun startTune() {
        if (operationGate.busy || TuneState.ui.value.running) return
        try {
            store.cfg = _state.value.cfg
        } catch (_: SecretStorageException) {
            notify("Не удалось безопасно сохранить конфиг. Подбор не запущен.")
            return
        }
        WatchdogService.stop(ctx)
        ContextCompat.startForegroundService(ctx, Intent(ctx, TuneService::class.java))
    }

    fun stopTune() {
        ctx.startService(Intent(ctx, TuneService::class.java).setAction(TuneService.ACTION_STOP))
    }
}
