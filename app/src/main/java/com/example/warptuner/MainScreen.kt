package com.example.warptuner

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNav(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            val dir = if (initialState == AppScreen.MAIN || initialState == AppScreen.ONBOARDING) 1 else -1
            (slideInHorizontally(tween(300)) { it / 8 * dir } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 8 * dir } + fadeOut(tween(200)))
        },
        label = "screen"
    ) { screen ->
        when (screen) {
            AppScreen.ONBOARDING -> OnboardingScreen(onDone = { vm.finishOnboarding() })
            AppScreen.SETTINGS -> SettingsScreen(
                vm = vm,
                onBack = { vm.navigate(AppScreen.MAIN) }
            )
            AppScreen.APPS -> AppsScreen(vm.store, onBack = { vm.navigate(AppScreen.MAIN) })
            AppScreen.MAIN -> MainScreen(vm)
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val store = vm.store
    val state by vm.state.collectAsState()
    val ui by TuneState.ui.collectAsState()
    val watch by WatchState.text.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    val running = ui.running
    val best = ui.outcome?.ranked?.firstOrNull()
    val baseline = ui.outcome?.baseline
    val cfg = state.cfg
    val animatedProgress by animateFloatAsState(
        targetValue = ui.progress,
        animationSpec = tween(300),
        label = "tuneProgress"
    )

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
        cm.setPrimaryClip(sensitiveClip("conf", b.config))
        vm.notify("Лучший конфиг скопирован; буфер очистится через ${CLIP_CLEAR_MS / 1000} с")
        scope.launch {
            delay(CLIP_CLEAR_MS)
            clearClipIfUnchanged(cm, b.config)
        }
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
                    ActionButton(enabled = !state.creating, onClick = { vm.createAccount() }) {
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
        AnimatedNotice(state.notice, Modifier.fillMaxWidth())
        AnimatedNotice(watch, Modifier.fillMaxWidth(), MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.watchOn, onCheckedChange = { vm.setWatch(it) })
            Spacer(Modifier.padding(4.dp))
            Text("Следить за туннелем и переключать при сбое")
        }
        BatteryHint(store)
        Spacer(Modifier.height(8.dp))

        Row {
            ActionButton(
                modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                onClick = { withVpn { vm.connect(cfg) } }
            ) { Text("Подключить") }
            Spacer(Modifier.padding(4.dp))
            ActionButton(
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
            ActionButton(
                modifier = Modifier.weight(1f), enabled = !running && cfg.isNotBlank(),
                onClick = { withVpn { vm.startTune() } }
            ) { Text("Подобрать") }
            Spacer(Modifier.padding(4.dp))
            ActionButton(
                modifier = Modifier.weight(1f), enabled = running,
                onClick = { vm.stopTune() }
            ) { Text("Стоп") }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            ActionButton(
                modifier = Modifier.weight(1f), enabled = !running && best != null,
                onClick = { best?.let { b -> withVpn { vm.connect(b.config) } } }
            ) { Text("Лучший") }
            Spacer(Modifier.padding(4.dp))
            ActionButton(modifier = Modifier.weight(1f), enabled = best != null, onClick = {
                best?.let { b ->
                    if (baseline != null && b.mbps < baseline.mbps * 1.15) confirmCopy = true
                    else doCopy(b)
                }
            }) { Text("Копировать") }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(enabled = !running, onClick = { vm.trace() }) { Text("Проверить связь (trace)") }
        Spacer(Modifier.height(12.dp))

        if (running || ui.status.isNotEmpty()) {
            AnimatedNotice(ui.status, Modifier.fillMaxWidth())
            if (running) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (!running && best != null) {
            AppearAnimated { SummaryCard(best, baseline, ui.outcome?.rawMbps ?: 0.0, ui.outcome?.endpointNote ?: "") }
            Spacer(Modifier.height(8.dp))
            Text("Лучшие варианты (после повторного замера)", style = MaterialTheme.typography.titleMedium)
            ui.outcome?.ranked?.forEach { r ->
                key("ranked", r.config) { AppearAnimated { ResultCard(r) } }
            }
            if (ui.rows.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Все проверенные варианты", style = MaterialTheme.typography.titleMedium)
                ui.rows.sortedByDescending { it.score }.forEach { r ->
                    key("row", r.config) { AppearAnimated { ResultCard(r) } }
                }
            }
        } else {
            ui.rows.asReversed().forEach { r ->
                key("live", r.config) { AppearAnimated { ResultCard(r) } }
            }
        }

        TextButton(onClick = { showHelp = !showHelp }) {
            Text(if (showHelp) "Скрыть пояснения" else "Что означают значения?")
        }
        if (showHelp) HelpCard()
        TextButton(onClick = { showLog = !showLog }) {
            Text(if (showLog) "Скрыть журнал" else "Технический журнал")
        }
        if (showLog) Text(ui.log, style = MaterialTheme.typography.bodySmall)
    }
}
