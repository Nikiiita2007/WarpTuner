package com.example.warptuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TuneService : Service() {
    companion object {
        const val ACTION_STOP = "com.example.warptuner.STOP"
        private const val CHANNEL = "tune"
        private const val NOTIF_ID = 1
        private const val TAG = "TuneService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "STOP received, active=${job?.isActive}")
            if (job?.isCompleted == false) {
                TuneState.update { it.copy(status = "Останавливаю…") }
                updateNotification("Останавливаю…", TuneState.ui.value.progress)
                job?.cancel()
            } else {
                finish()
            }
            return START_NOT_STICKY
        }
        // Cancellation is not completion: cleanup may still own the tunnel.
        if (job?.isCompleted == false) return START_NOT_STICKY
        finishing = false
        createChannel()
        val n = notification("Подготовка…", 0f)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
        val cfg = Store(applicationContext).cfg
        if (cfg.isBlank()) {
            finish()
            return START_NOT_STICKY
        }
        job = scope.launch { runTune(cfg) }
        return START_NOT_STICKY
    }

    private suspend fun runTune(cfg: String) {
        TuneState.update { TuneUi(running = true, status = "Старт") }
        val cb = TuneCallbacks(
            status = { s ->
                TuneState.update { it.copy(status = s) }
                updateNotification(s, TuneState.ui.value.progress)
            },
            progress = { d, t ->
                val p = if (t > 0) d.toFloat() / t else 0f
                TuneState.update { it.copy(progress = p) }
                updateNotification(TuneState.ui.value.status, p)
            },
            result = { r ->
                TuneState.update { it.copy(rows = it.rows + r, log = Tuner.fmt(r) + "\n" + it.log) }
            }
        )
        val t0 = System.currentTimeMillis()
        val mode = runCatching { TuneMode.valueOf(Store(applicationContext).tuneMode) }
            .getOrDefault(TuneMode.NORMAL)
        try {
            val o = Tuner.tune(applicationContext, cfg, cb, mode)
            currentCoroutineContext().ensureActive()
            Store(applicationContext).lastOutcome = outcomeToJson(o)
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                val completedAt = System.currentTimeMillis()
                val best = o.ranked.firstOrNull { it.ok }?.historyMetrics()
                if (best != null) {
                    TuningHistory(applicationContext).append(
                        TuningHistoryEntry(completedAt, mode.name, o.baseline?.historyMetrics(), best)
                    )
                }
                val sec = (completedAt - t0) / 1000
                TuneState.update { it.copy(outcome = o, status = "Готово за ${sec / 60} мин ${sec % 60} с") }
            }
        } catch (e: CancellationException) {
            TuneState.update { it.copy(status = "Остановлено") }
        } catch (e: Throwable) {
            TuneState.update { it.copy(status = "Ошибка: ${e.message}") }
        } finally {
            Log.d(TAG, "finally: start cleanup")
            finishing = true
            finishTuneOperation(
                cleanup = {
                    withContext(Dispatchers.IO) {
                        runCatching { Engine.down(applicationContext) }
                    }
                },
                finished = {
                    withContext(Dispatchers.Main.immediate) {
                        Log.d(TAG, "finally: done")
                        finish()
                    }
                },
            )
        }
    }

    private fun TuneResult.historyMetrics(): HistoryMetrics? =
        if (ok && mbps.isFinite() && rttMs.isFinite() && loss.isFinite()) {
            HistoryMetrics(mbps, rttMs, loss)
        } else null

    private fun finish() {
        finishing = true
        TuneState.update { it.copy(running = false) }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Подбор конфига", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun notification(text: String, progress: Float): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TuneService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Подбор конфига")
            .setContentText(text)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Остановить", stop)
            .build()
    }

    private fun updateNotification(text: String, progress: Float) {
        if (finishing) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification(text, progress))
    }

    override fun onDestroy() {
        // A cancelled job keeps running=true until its final cleanup returns.
        if (job?.isCompleted != false) {
            TuneState.update { it.copy(running = false) }
        }
        scope.cancel()
        super.onDestroy()
    }
}
