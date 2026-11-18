package com.example.warptuner

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { ONBOARDING, MAIN, SETTINGS, APPS }

data class MainUiState(
    val screen: AppScreen,
    val cfg: String,
    val notice: String = "",
    val creating: Boolean = false,
    val watchOn: Boolean = true,
    val tuneMode: String = TuneMode.NORMAL.name
)

/**
 * Holds the screen state and the user actions (connect, disconnect, account creation,
 * tuning, tunnel watchdog). Anything that needs an Activity (VPN permission dialog,
 * notification permission, clipboard) stays in the composables.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val ctx: Application get() = getApplication()

    private val _state = MutableStateFlow(
        MainUiState(
            screen = if (store.onboarded) AppScreen.MAIN else AppScreen.ONBOARDING,
            cfg = store.cfg,
            watchOn = store.watchdog,
            tuneMode = store.tuneMode
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

    fun navigate(screen: AppScreen) {
        _state.update { it.copy(screen = screen) }
    }

    fun finishOnboarding() {
        store.onboarded = true
        navigate(AppScreen.MAIN)
    }

    fun setCfg(cfg: String) {
        _state.update { it.copy(cfg = cfg) }
    }

    fun notify(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun setWatch(on: Boolean) {
        store.watchdog = on
        _state.update { it.copy(watchOn = on) }
        if (!on) WatchdogService.stop(ctx)
    }

    fun setTuneMode(name: String) {
        store.tuneMode = name
        _state.update { it.copy(tuneMode = name) }
    }

    fun createAccount() {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, notice = "Регистрация…") }
        viewModelScope.launch {
            try {
                val c = createAccountSmart(ctx, store)
                _state.update { it.copy(cfg = c, notice = "Конфиг создан") }
            } catch (e: Throwable) {
                _state.update { it.copy(notice = friendlyError(e)) }
            } finally {
                _state.update { it.copy(creating = false) }
            }
        }
    }

    fun connect(text: String) {
        viewModelScope.launch {
            try {
                WatchdogService.stop(ctx)
                notify("Подключаюсь…")
                val st = withContext(Dispatchers.IO) {
                    Engine.up(ctx, applySplit(text, store, ctx.packageName))
                }
                notify("Туннель поднят ($st), проверяю связь…")
                val ok = withContext(Dispatchers.IO) { Prober.waitReady(12000) }
                if (ok) {
                    store.activeCfg = text
                    notify("Подключено: трафик идёт через WARP ✓")
                    if (store.watchdog) WatchdogService.start(ctx)
                } else {
                    notify(
                        "Туннель поднят, но трафик не идёт через WARP (warp=off).\n" +
                                "Возможные причины: конфиг не подходит вашей сети (запустите подбор), " +
                                "нестабильный Wi-Fi или приложение исключено из туннеля в разделе «Приложения». " +
                                "Нажмите «Подключить» ещё раз."
                    )
                }
            } catch (e: Throwable) {
                notify("Ошибка: ${e.message}")
            }
        }
    }

    fun disconnect() {
        WatchdogService.stop(ctx)
        viewModelScope.launch {
            try {
                val st = withContext(Dispatchers.IO) { Engine.down(ctx) }
                notify("Состояние туннеля: $st")
            } catch (e: Throwable) {
                notify("Ошибка: ${e.message}")
            }
        }
    }

    fun trace() {
        viewModelScope.launch {
            try {
                notify(withContext(Dispatchers.IO) { Engine.trace() })
            } catch (e: Throwable) {
                notify("Проверка не прошла: ${e.message}")
            }
        }
    }

    fun startTune() {
        WatchdogService.stop(ctx)
        store.cfg = _state.value.cfg
        ContextCompat.startForegroundService(ctx, Intent(ctx, TuneService::class.java))
    }

    fun stopTune() {
        ctx.startService(Intent(ctx, TuneService::class.java).setAction(TuneService.ACTION_STOP))
    }
}
