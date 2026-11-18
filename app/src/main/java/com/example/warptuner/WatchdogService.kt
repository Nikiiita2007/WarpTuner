package com.example.warptuner

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WatchState {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    fun set(v: String) { _text.value = v }
}

internal fun watchdogDisconnect(down: () -> String, saveActive: (String) -> Unit): Boolean {
    val disconnected = try {
        down() == "DOWN"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
    if (disconnected) saveActive("")
    return disconnected
}

internal suspend fun watchdogTryCandidate(
    config: String,
    disconnect: () -> Boolean,
    connect: suspend (String) -> Boolean,
    verify: suspend () -> Boolean,
    saveActive: (String) -> Unit,
): Boolean {
    if (!disconnect()) return false
    var verified = false
    try {
        if (connect(config) && verify()) {
            saveActive(config)
            verified = true
        }
        return verified
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return false
    } finally {
        if (!verified) disconnect()
    }
}

class WatchdogService : Service() {
    companion object {
        private const val CHANNEL = "watch"
        private const val ALERT_CHANNEL = "watch_alert"
        private const val NOTIF_ID = 2
        private const val ALERT_ID = 3
        const val INTERVAL_MS = 60_000L
        const val FAILS_TO_ACT = 3
        const val MAX_CANDIDATES = 4

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WatchdogService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WatchdogService::class.java))
            WatchState.set("")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        val n = notification("Слежу за туннелем")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (job?.isActive != true) job = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        val store = Store(applicationContext)
        var fails = 0
        setStatus("Слежу за туннелем")
        while (true) {
            delay(INTERVAL_MS)
            if (TuneState.ui.value.running) continue
            val st = try { Engine.state(applicationContext) } catch (e: Exception) { "?" }
            if (st == "DOWN") {
                WatchState.set("Туннель выключен")
                break
            }
            val ok = withContext(Dispatchers.IO) {
                try { Prober.traceWarp() } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            }
            if (ok) {
                fails = 0
                setStatus("Туннель работает (проверено ${now()})")
            } else {
                fails++
                setStatus("Нет связи с WARP: проверка $fails из $FAILS_TO_ACT")
                if (fails >= FAILS_TO_ACT) {
                    fails = 0
                    val res = recover(store)
                    if (res == null) {
                        val down = withContext(Dispatchers.IO) { disconnect(store) }
                        val detail = if (down) "Туннель отключён: автопереключение не помогло."
                            else "Связь не подтверждена; отключить туннель не удалось."
                        WatchState.set(detail)
                        alert("Туннель не работает", "$detail Откройте приложение и проверьте подключение.")
                        break
                    } else if (res > 0) {
                        setStatus("Включён запасной конфиг №$res (${now()})")
                        alert("Туннель переключён", "Основной конфиг не отвечал, включён запасной вариант №$res из последнего подбора.")
                    } else {
                        setStatus("Переподключено (${now()})")
                    }
                }
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun disconnect(store: Store): Boolean = watchdogDisconnect(
        down = { Engine.down(applicationContext) },
        saveActive = { store.activeCfg = it },
    )

    private suspend fun recover(store: Store): Int? {
        val active = store.activeCfg
        val ranked = outcomeFromJson(store.lastOutcome)?.ranked?.map { it.config }.orEmpty()
        val list = (listOf(active) + ranked).filter { it.isNotBlank() }.distinct().take(MAX_CANDIDATES)
        for ((i, cfg) in list.withIndex()) {
            val number = if (cfg == active) 0 else i + if (active.isBlank()) 1 else 0
            setStatus(if (number == 0) "Переподключаю…" else "Пробую запасной конфиг №$number…")
            val ok = withContext(Dispatchers.IO) {
                watchdogTryCandidate(
                    config = cfg,
                    disconnect = { disconnect(store) },
                    connect = {
                        delay(1000)
                        Engine.up(applicationContext, applySplit(it, store, packageName)) == "UP"
                    },
                    verify = { Prober.waitReady(15000) },
                    saveActive = { store.activeCfg = it },
                )
            }
            if (ok) return number
        }
        return null
    }

    private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun setStatus(text: String) {
        WatchState.set(text)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Контроль туннеля", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Сбои туннеля", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(code: Int): PendingIntent = PendingIntent.getActivity(
        this, code,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Контроль туннеля")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(2))
            .build()

    private fun alert(title: String, text: String) {
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(3))
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
