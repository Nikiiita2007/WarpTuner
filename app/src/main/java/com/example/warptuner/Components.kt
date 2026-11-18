package com.example.warptuner

import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

fun siteName(k: String) = when (k) {
    "youtube" -> "YouTube"
    "telegram" -> "Telegram"
    "tg_web" -> "Telegram Web"
    else -> k
}

@Composable
fun ResultCard(r: TuneResult) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            val t = Tuner.titleOf(r.name).replaceFirstChar { it.uppercase() }
            Text("$t · оценка ${"%.1f".format(r.score)}", fontWeight = FontWeight.Bold)
            if (!r.ok) {
                Text("Не прошёл проверку: ${r.err}")
            } else {
                Text(
                    "Скорость ${"%.1f".format(r.mbps)} Мбит/с · задержка ${"%.0f".format(r.rttMs)} мс · " +
                            "потери ${"%.0f".format(r.loss * 100)}%"
                )
                Text(r.sites.entries.joinToString("   ") { siteName(it.key) + if (it.value) " ✓" else " ✗" })
            }
            Text(
                "Jc=${r.params.jc}, Jmin=${r.params.jmin}, Jmax=${r.params.jmax}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SummaryCard(best: TuneResult, base: TuneResult?, raw: Double = 0.0, note: String = "") {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Итог подбора", style = MaterialTheme.typography.titleMedium)
            Text("Лучший найденный: ${"%.1f".format(best.mbps)} Мбит/с, ${"%.0f".format(best.rttMs)} мс")
            if (raw > 0) {
                Text("Без VPN (ваш интернет сейчас): ${"%.1f".format(raw)} Мбит/с")
                Text("Лучший даёт ${"%.0f".format(best.mbps / raw * 100)}% от этой скорости.")
            }
            if (base != null) {
                Text("Ваш текущий: ${"%.1f".format(base.mbps)} Мбит/с, ${"%.0f".format(base.rttMs)} мс")
                val d = if (base.mbps > 0) (best.mbps - base.mbps) / base.mbps * 100 else 0.0
                Text(
                    when {
                        best.mbps <= base.mbps -> "Найденный не быстрее текущего: менять конфиг не нужно."
                        d < 15 -> "Выигрыш ${"%.0f".format(d)}% находится в пределах погрешности замеров: менять необязательно."
                        else -> "Найденный быстрее текущего на ${"%.0f".format(d)}%: его стоит использовать."
                    }
                )
            } else {
                Text("В вашем конфиге нет Jc/Jmin/Jmax, поэтому сравнивать не с чем.")
            }
            Text(
                "Значения усреднены по двум замерам, потому что скорость и задержка заметно колеблются.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (note.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(note, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HelpCard() {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Как читать результаты", style = MaterialTheme.typography.titleMedium)
            Text(
                "Оценка (0–100): сводный балл для сравнения вариантов. Это доля доступных сайтов, " +
                        "умноженная на сумму: 40% скорость (максимум засчитывается на 50 Мбит/с), " +
                        "30% задержка и 30% отсутствие потерь."
            )
            Text(
                "Скорость: сколько данных за 5 секунд скачалось с серверов Cloudflare через туннель. " +
                        "Показывает пропускную способность, а не качество видео."
            )
            Text(
                "Задержка (мс): время ответа сервера через туннель, медиана из 4 запросов. " +
                        "Чем меньше, тем отзывчивее сайты и мессенджеры."
            )
            Text("Потери: доля из 4 тестовых запросов, на которые не пришёл ответ.")
            Text(
                "YouTube, Telegram, Telegram Web: ✓ значит, что сервер ответил через туннель. " +
                        "Это проверка доступности, а не гарантия, что видео не будет тормозить."
            )
            Text(
                "Jc, Jmin, Jmax: число и размер «мусорных» пакетов перед рукопожатием. " +
                        "Они маскируют трафик и известны только вашему приложению, серверу WARP согласовывать их не нужно."
            )
            Text(
                "Повтор: лучшие варианты и текущий конфиг проверяются второй раз, " +
                        "а итог усредняется, чтобы случайный удачный замер не обманул."
            )
        }
    }
}

@Composable
fun BatteryHint(store: Store) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var hidden by remember { mutableStateOf(store.hideBatteryHint) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ok = isIgnoringBattery(ctx) }

    if (!ok && !hidden) {
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Подбор может прерываться", style = MaterialTheme.typography.titleMedium)
                Text(
                    "На телефонах Samsung система может останавливать приложение в фоне. " +
                            "Разрешите работу без ограничений батареи, и подбор не будет прерываться, когда вы его сворачиваете."
                )
                Row {
                    Button(onClick = {
                        try {
                            launcher.launch(batteryRequestIntent(ctx))
                        } catch (e: Exception) {
                            launcher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }) { Text("Разрешить") }
                    TextButton(onClick = { launcher.launch(appSettingsIntent(ctx)) }) { Text("Настройки") }
                    TextButton(onClick = {
                        store.hideBatteryHint = true
                        hidden = true
                    }) { Text("Не напоминать") }
                }
            }
        }
    }
}

@Composable
fun ImportButtons(store: Store, onDone: (String) -> Unit, onMsg: (String) -> Unit) {
    val ctx = LocalContext.current

    fun handle(text: String) {
        val err = tryImport(text, store)
        if (err == null) {
            onDone(store.cfg)
            onMsg("Конфиг импортирован")
        } else {
            onMsg(err)
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
            } catch (e: Throwable) {
                ""
            }
            handle(text.take(200_000))
        }
    }

    Row(Modifier.fillMaxWidth()) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(ctx).toString()
            } else {
                ""
            }
            handle(text)
        }) { Text("Из буфера") }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            fileLauncher.launch(arrayOf("*/*"))
        }) { Text("Файл") }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(ctx, options).startScan()
                .addOnSuccessListener { b -> handle(b.rawValue.orEmpty()) }
                .addOnFailureListener { e -> onMsg("Сканер недоступен: ${e.message}") }
        }) { Text("QR") }
    }
}
