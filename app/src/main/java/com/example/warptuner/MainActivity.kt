package com.example.warptuner

import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent { AppRoot { Screen() } }
    }

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
fun SettingsScreen(store: Store, onBack: () -> Unit, onCfg: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tpl by remember { mutableStateOf(store.template) }
    var ep by remember { mutableStateOf(store.endpoint) }
    var manual by remember { mutableStateOf(store.cfg) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text("1. WARP-аккаунт", style = MaterialTheme.typography.titleMedium)
        Text(
            "Приложение регистрирует бесплатное устройство в Cloudflare и собирает конфиг само. " +
                    "Если регистрация не проходит, вставьте конфиг вручную ниже."
        )
        Button(enabled = !busy, onClick = {
            busy = true
            msg = "Регистрация…"
            scope.launch {
                try {
                    val c = createAccountSmart(ctx, store)
                    manual = c
                    onCfg(c)
                    msg = "Готово: конфиг создан"
                } catch (e: Throwable) {
                    msg = friendlyError(e)
                } finally {
                    busy = false
                }
            }
        }) { Text("Создать аккаунт и конфиг") }
        Spacer(Modifier.height(12.dp))

        Text("2. Шаблон обфускации", style = MaterialTheme.typography.titleMedium)
        Text(
            "Строки, которые добавляются в [Interface]: Jc, Jmin, Jmax, S, H, I1. " +
                    "Если встроенный шаблон не подключается в вашей сети, вставьте рабочий конфиг в поле 4 " +
                    "и нажмите «Взять из конфига»."
        )
        OutlinedTextField(
            value = tpl, onValueChange = { tpl = it }, minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            TextButton(onClick = { tpl = DEFAULT_TEMPLATE }) { Text("Сбросить") }
            TextButton(onClick = { tpl = extractTemplate(manual) }) { Text("Взять из конфига") }
        }
        Spacer(Modifier.height(12.dp))

        Text("3. Endpoint (необязательно)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ep, onValueChange = { ep = it }, singleLine = true,
            label = { Text("адрес:порт") }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            store.template = tpl
            store.endpoint = ep
            val c = rebuildConfig(store)
            if (c != null) {
                manual = c
                onCfg(c)
                msg = "Шаблон и endpoint применены к конфигу аккаунта"
            } else {
                msg = "Сохранено. Для пересборки конфига сначала создайте аккаунт"
            }
        }) { Text("Сохранить и применить") }
        Spacer(Modifier.height(12.dp))

        Text("4. Конфиг вручную", style = MaterialTheme.typography.titleMedium)
        Text("Любое изменение здесь переводит приложение в ручной режим: конфиг аккаунта больше не пересобирается.")
        ImportButtons(store, onDone = { manual = it; onCfg(it) }, onMsg = { msg = it })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manual, minLines = 6, modifier = Modifier.fillMaxWidth(),
            onValueChange = {
                manual = it
                store.cfg = it
                store.account = ""
                onCfg(it)
            }
        )
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg)
        }
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Store(ctx) }
    val ui by TuneState.ui.collectAsState()
    val watch by WatchState.text.collectAsState()
    var watchOn by remember { mutableStateOf(store.watchdog) }
    var cfg by remember { mutableStateOf(store.cfg) }
    var screen by remember { mutableStateOf(if (store.onboarded) "main" else "onboarding") }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    val running = ui.running
    val best = ui.outcome?.ranked?.firstOrNull()
    val baseline = ui.outcome?.baseline

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var hideBattery by remember { mutableStateOf(store.hideBatteryHint) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryOk = isIgnoringBattery(ctx) }
    LaunchedEffect(Unit) {
        if (TuneState.ui.value.outcome == null && store.lastOutcome.isNotBlank()) {
            outcomeFromJson(store.lastOutcome)?.let { o ->
                TuneState.update { it.copy(outcome = o, status = "Последний результат") }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun doCopy(b: TuneResult) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("conf", b.config))
        notice = "Лучший конфиг скопирован в буфер обмена"
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) pending?.invoke() else notice = "VPN не разрешён"
        pending = null
    }

    fun withVpn(action: () -> Unit) {
        val i = VpnService.prepare(ctx)
        if (i != null) {
            pending = action
            launcher.launch(i)
        } else {
            action()
        }
    }

    fun connect(text: String) {
        scope.launch {
            try {
                WatchdogService.stop(ctx)
                notice = "Подключаюсь…"
                val st = withContext(Dispatchers.IO) {
                    Engine.up(ctx, applySplit(text, store, ctx.packageName))
                }
                notice = "Туннель поднят ($st), проверяю связь…"
                val ok = withContext(Dispatchers.IO) { Prober.waitReady(12000) }
                if (ok) {
                    store.activeCfg = text
                    notice = "Подключено: трафик идёт через WARP ✓"
                    if (store.watchdog) WatchdogService.start(ctx)
                } else {
                    notice = "Туннель поднят, но трафик не идёт через WARP (warp=off).\n" +
                            "Возможные причины: конфиг не подходит вашей сети (запустите подбор), " +
                            "нестабильный Wi-Fi или приложение исключено из туннеля в разделе «Приложения». " +
                            "Нажмите «Подключить» ещё раз."
                }
            } catch (e: Throwable) {
                notice = "Ошибка: ${e.message}"
            }
        }
    }

    fun startTune() {
        WatchdogService.stop(ctx)
        store.cfg = cfg
        ContextCompat.startForegroundService(ctx, Intent(ctx, TuneService::class.java))
    }

    fun stopTune() {
        ctx.startService(Intent(ctx, TuneService::class.java).setAction(TuneService.ACTION_STOP))
    }

    if (confirmCopy && best != null) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text("Выигрыш незначительный") },
            text = {
                Text(
                    "Найденный: ${"%.1f".format(best.mbps)} Мбит/с, " +
                            "текущий: ${"%.1f".format(baseline?.mbps ?: 0.0)} Мбит/с. " +
                            "Разница меньше 15%, это в пределах погрешности. Всё равно скопировать?"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmCopy = false; doCopy(best) }) { Text("Копировать") }
            },
            dismissButton = { TextButton(onClick = { confirmCopy = false }) { Text("Отмена") } }
        )
    }

    if (screen == "onboarding") {
        OnboardingScreen(onDone = { store.onboarded = true; screen = "main" })
    } else if (screen == "settings") {
        SettingsScreen(store, onBack = { screen = "main" }, onCfg = { cfg = it })
    } else if (screen == "apps") {
        AppsScreen(store, onBack = { screen = "main" })
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row {
                Text(
                    "WarpTuner", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { screen = "apps" }) { Text("Приложения") }
                TextButton(onClick = { screen = "settings" }) { Text("Настройки") }
            }

            if (cfg.isBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Конфиг ещё не создан", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Приложение может само зарегистрировать бесплатный WARP-аккаунт. " +
                                    "Вставить готовый конфиг можно в настройках."
                        )
                        Button(enabled = !creating, onClick = {
                            creating = true
                            notice = "Регистрация…"
                            scope.launch {
                                try {
                                    cfg = createAccountSmart(ctx, store)
                                    notice = "Конфиг создан"
                                } catch (e: Throwable) {
                                    notice = friendlyError(e)
                                } finally {
                                    creating = false
                                }
                            }
                        }) { Text("Создать автоматически") }
                        Spacer(Modifier.height(8.dp))
                        Text("или импортируйте готовый конфиг:")
                        ImportButtons(store, onDone = { cfg = it }, onMsg = { notice = it })
                    }
                }
            } else {
                Text(
                    if (store.account.isNotBlank()) "Конфиг: создан приложением (WARP-аккаунт)"
                    else "Конфиг: введён вручную"
                )
            }
            if (notice.isNotEmpty()) Text(notice)
            if (watch.isNotEmpty()) {
                Text(watch, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = watchOn, onCheckedChange = {
                    watchOn = it
                    store.watchdog = it
                    if (!it) WatchdogService.stop(ctx)
                })
                Spacer(Modifier.padding(4.dp))
                Text("Следить за туннелем и переключать при сбое")
            }
            if (!batteryOk && !hideBattery) {
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
                                    batteryLauncher.launch(batteryRequestIntent(ctx))
                                } catch (e: Exception) {
                                    batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }) { Text("Разрешить") }
                            TextButton(onClick = {
                                batteryLauncher.launch(appSettingsIntent(ctx))
                            }) { Text("Настройки") }
                            TextButton(onClick = {
                                store.hideBatteryHint = true
                                hideBattery = true
                            }) { Text("Не напоминать") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row {
                Button(
                    modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                    onClick = { withVpn { connect(cfg) } }
                ) { Text("Подключить") }
                Spacer(Modifier.padding(4.dp))
                Button(modifier = Modifier.weight(1f), enabled = !running, onClick = {
                    WatchdogService.stop(ctx)
                    scope.launch {
                        try {
                            notice = "Состояние туннеля: " + withContext(Dispatchers.IO) { Engine.down(ctx) }
                        } catch (e: Throwable) {
                            notice = "Ошибка: ${e.message}"
                        }
                    }
                }) { Text("Отключить") }
            }
            var tuneMode by remember { mutableStateOf(store.tuneMode) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TuneMode.values().forEach { m ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tuneMode == m.name, enabled = !running,
                            onClick = { tuneMode = m.name; store.tuneMode = m.name }
                        )
                        Text(m.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                    onClick = { withVpn { startTune() } }
                ) { Text("Подобрать") }
                Spacer(Modifier.padding(4.dp))
                Button(
                    modifier = Modifier.weight(1f), enabled = running,
                    onClick = { stopTune() }
                ) { Text("Стоп") }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    modifier = Modifier.weight(1f), enabled = !running && best != null,
                    onClick = { best?.let { b -> withVpn { connect(b.config) } } }
                ) { Text("Лучший") }
                Spacer(Modifier.padding(4.dp))
                Button(modifier = Modifier.weight(1f), enabled = best != null, onClick = {
                    best?.let { b ->
                        if (baseline != null && b.mbps < baseline.mbps * 1.15) confirmCopy = true
                        else doCopy(b)
                    }
                }) { Text("Копировать") }
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = !running, onClick = {
                scope.launch {
                    try {
                        notice = withContext(Dispatchers.IO) { Engine.trace() }
                    } catch (e: Throwable) {
                        notice = "Проверка не прошла: ${e.message}"
                    }
                }
            }) { Text("Проверить связь (trace)") }
            Spacer(Modifier.height(12.dp))

            if (running || ui.status.isNotEmpty()) {
                Text(ui.status)
                if (running) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = ui.progress, modifier = Modifier.fillMaxWidth())
                }
            }

            if (!running && best != null) {
                SummaryCard(best, baseline, ui.outcome?.rawMbps ?: 0.0, ui.outcome?.endpointNote ?: "")
                Spacer(Modifier.height(8.dp))
                Text("Лучшие варианты (после повторного замера)", style = MaterialTheme.typography.titleMedium)
                ui.outcome?.ranked?.forEach { ResultCard(it) }
                if (ui.rows.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Все проверенные варианты", style = MaterialTheme.typography.titleMedium)
                    ui.rows.sortedByDescending { it.score }.forEach { ResultCard(it) }
                }
            } else {
                ui.rows.asReversed().forEach { ResultCard(it) }
            }

            TextButton(onClick = { showHelp = !showHelp }) {
                Text(if (showHelp) "Скрыть пояснения" else "Что означают значения?")
            }
            if (showHelp) HelpCard()
            TextButton(onClick = { showLog = !showLog }) {
                Text(if (showLog) "Скрыть технический журнал" else "Технический журнал")
            }
            if (showLog) Text(ui.log, style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class AppItem(val pkg: String, val label: String)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppsScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(store.splitMode) }
    val selected = remember {
        mutableStateListOf<String>().apply {
            addAll(store.splitApps.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        }
    }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        loading = false
    }

    fun save() {
        store.splitMode = mode
        store.splitApps = selected.joinToString(",")
    }

    BackHandler { save(); onBack() }

    val modes = listOf(
        "off" to "Выключено (всё через VPN)",
        "exclude" to "Выбранные приложения идут мимо VPN",
        "include" to "Только выбранные приложения идут через VPN"
    )
    val shown = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = { save(); onBack() }) { Text("← Назад") }
        Text("Раздельное туннелирование", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Применяется при нажатии «Подключить». Если туннель уже включён, " +
                    "отключите и подключите его заново. Во время подбора не используется.",
            style = MaterialTheme.typography.bodySmall
        )
        modes.forEach { (k, t) ->
            Row(
                Modifier.fillMaxWidth().clickable { mode = k; save() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == k, onClick = { mode = k; save() })
                Text(t)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Поиск приложения") }, modifier = Modifier.fillMaxWidth()
        )
        if (loading) Text("Загрузка списка…")
        else Text("Выбрано: ${selected.size}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.pkg }) { a ->
                val checked = a.pkg in selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (checked) selected.remove(a.pkg) else selected.add(a.pkg)
                        save()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        if (it) selected.add(a.pkg) else selected.remove(a.pkg)
                        save()
                    })
                    Column {
                        Text(a.label)
                        Text(a.pkg, style = MaterialTheme.typography.bodySmall)
                    }
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

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val titles = listOf(
        "Что делает приложение",
        "Как получить конфиг",
        "Если регистрация не проходит"
    )
    val texts = listOf(
        listOf(
            "WarpTuner подключает бесплатный WARP от Cloudflare через AmneziaWG и подбирает под вашу сеть " +
                    "параметры «маскировки» трафика (Jc, Jmin, Jmax) и endpoint.",
            "Подбор идёт в фоне: приложение можно свернуть, в шторке будет уведомление с кнопкой «Остановить»."
        ),
        listOf(
            "Вариант 1. Нажмите «Создать автоматически»: приложение само зарегистрирует бесплатное устройство в Cloudflare.",
            "Вариант 2. Если конфиг уже есть, скопируйте его и нажмите «Из буфера», либо откройте файл .conf (кнопка «Файл»), " +
                    "либо отсканируйте QR-код."
        ),
        listOf(
            "В некоторых сетях доступ к API Cloudflare заблокирован, и создание конфига заканчивается ошибкой «таймаут».",
            "Включите на минуту любой другой VPN, нажмите «Создать автоматически» ещё раз и после успеха отключите чужой VPN.",
            "Либо импортируйте готовый конфиг из буфера, файла или QR-кода."
        )
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Шаг ${step + 1} из 3", style = MaterialTheme.typography.bodySmall)
        Text(titles[step], style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        texts[step].forEach {
            Text(it)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Назад") }
            } else {
                TextButton(onClick = onDone) { Text("Пропустить") }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step < 2) step++ else onDone() }) {
                Text(if (step < 2) "Далее" else "Начать")
            }
        }
    }
}

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

fun batteryRequestIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${ctx.packageName}"))

fun appSettingsIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${ctx.packageName}"))
