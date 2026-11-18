package com.example.warptuner

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun AppNav(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    when (state.screen) {
        AppScreen.ONBOARDING -> OnboardingScreen(onDone = { vm.finishOnboarding() })
        AppScreen.SETTINGS -> SettingsScreen(
            vm.store,
            onBack = { vm.navigate(AppScreen.MAIN) },
            onCfg = { vm.setCfg(it) }
        )
        AppScreen.APPS -> AppsScreen(vm.store, onBack = { vm.navigate(AppScreen.MAIN) })
        AppScreen.MAIN -> MainScreen(vm)
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val store = vm.store
    val state by vm.state.collectAsState()
    val ui by TuneState.ui.collectAsState()
    val watch by WatchState.text.collectAsState()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    val running = ui.running
    val best = ui.outcome?.ranked?.firstOrNull()
    val baseline = ui.outcome?.baseline
    val cfg = state.cfg

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
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
        vm.notify("Лучший конфиг скопирован в буфер обмена")
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) pending?.invoke() else vm.notify("VPN не разрешён")
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

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row {
            Text(
                "WarpTuner", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.navigate(AppScreen.APPS) }) { Text("Приложения") }
            TextButton(onClick = { vm.navigate(AppScreen.SETTINGS) }) { Text("Настройки") }
        }

        if (cfg.isBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Конфиг ещё не создан", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Приложение может само зарегистрировать бесплатный WARP-аккаунт. " +
                                "Вставить готовый конфиг можно в настройках."
                    )
                    Button(enabled = !state.creating, onClick = { vm.createAccount() }) {
                        Text("Создать автоматически")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("или импортируйте готовый конфиг:")
                    ImportButtons(store, onDone = { vm.setCfg(it) }, onMsg = { vm.notify(it) })
                }
            }
        } else {
            Text(
                if (store.account.isNotBlank()) "Конфиг: создан приложением (WARP-аккаунт)"
                else "Конфиг: введён вручную"
            )
        }
        if (state.notice.isNotEmpty()) Text(state.notice)
        if (watch.isNotEmpty()) {
            Text(watch, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.watchOn, onCheckedChange = { vm.setWatch(it) })
            Spacer(Modifier.padding(4.dp))
            Text("Следить за туннелем и переключать при сбое")
        }
        BatteryHint(store)
        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                onClick = { withVpn { vm.connect(cfg) } }
            ) { Text("Подключить") }
            Spacer(Modifier.padding(4.dp))
            Button(
                modifier = Modifier.weight(1f), enabled = !running,
                onClick = { vm.disconnect() }
            ) { Text("Отключить") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TuneMode.values().forEach { m ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.tuneMode == m.name, enabled = !running,
                        onClick = { vm.setTuneMode(m.name) }
                    )
                    Text(m.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                onClick = { withVpn { vm.startTune() } }
            ) { Text("Подобрать") }
            Spacer(Modifier.padding(4.dp))
            Button(
                modifier = Modifier.weight(1f), enabled = running,
                onClick = { vm.stopTune() }
            ) { Text("Стоп") }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                modifier = Modifier.weight(1f), enabled = !running && best != null,
                onClick = { best?.let { b -> withVpn { vm.connect(b.config) } } }
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
        Button(enabled = !running, onClick = { vm.trace() }) { Text("Проверить связь (trace)") }
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
