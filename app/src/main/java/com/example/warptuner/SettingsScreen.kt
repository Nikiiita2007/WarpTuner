package com.example.warptuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val tune by TuneState.ui.collectAsState()
    val busy = state.operation != null || tune.running
    val store = vm.store
    var tpl by remember { mutableStateOf(store.template) }
    var ep by remember { mutableStateOf(store.endpoint) }
    var msg by remember { mutableStateOf("") }
    var manualDraft by remember { mutableStateOf<String?>(null) }
    var showRegistrationNotice by remember { mutableStateOf(state.creating) }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Оформление", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                FilterChip(selected = state.themeMode == mode, onClick = { vm.setThemeMode(mode) }, label = { Text(mode.label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("1. WARP-аккаунт", style = MaterialTheme.typography.titleMedium)
        Text("Приложение регистрирует бесплатное устройство в Cloudflare и собирает конфиг само, если сервис регистрации доступен в вашей сети. Если регистрация не проходит, вставьте готовый конфиг ниже.")
        ActionButton(enabled = !busy, onClick = {
            showRegistrationNotice = true
            vm.createAccount()
        }) { Text("Создать аккаунт и конфиг") }
        Spacer(Modifier.height(12.dp))
        Text("2. Шаблон обфускации", style = MaterialTheme.typography.titleMedium)
        Text("Строки, которые добавляются в [Interface]: Jc, Jmin, Jmax, S, H, I1. Если встроенный шаблон не подключается в вашей сети, вставьте рабочий конфиг в поле 4 и нажмите «Взять из конфига».")
        OutlinedTextField(value = tpl, onValueChange = { tpl = it }, enabled = !busy, minLines = 5, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(enabled = !busy, onClick = { tpl = DEFAULT_TEMPLATE }) { Text("Сбросить") }
            TextButton(enabled = !busy, onClick = { tpl = extractTemplate(manualDraft ?: state.cfg) }) { Text("Взять из конфига") }
        }
        Spacer(Modifier.height(12.dp))
        Text("3. Endpoint (необязательно)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = ep, onValueChange = { ep = it }, enabled = !busy, singleLine = true, label = { Text("адрес:порт") }, modifier = Modifier.fillMaxWidth())
        ActionButton(enabled = !busy, onClick = {
            showRegistrationNotice = false
            msg = try {
                when {
                    store.account.isBlank() -> "Не сохранено: сначала создайте аккаунт."
                    vm.applyConfigSettings(tpl, ep) -> "Шаблон и endpoint применены к конфигу аккаунта"
                    else -> "Не сохранено: проверьте настройки конфига и дождитесь завершения операции."
                }
            } catch (_: SecretStorageException) {
                "Не удалось безопасно сохранить конфиг. Введённые данные не изменены."
            }
        }) { Text("Сохранить и применить") }
        Spacer(Modifier.height(12.dp))
        Text("4. Конфиг вручную", style = MaterialTheme.typography.titleMedium)
        Text("Сохранение переводит приложение в ручной режим: конфиг аккаунта больше не пересобирается.")
        if (!busy) {
            ImportButtons(store, onDone = {
                manualDraft = null
                vm.setCfg(it)
            }, onMsg = {
                showRegistrationNotice = false
                msg = it
            })
        } else {
            Text("Изменение конфигурации недоступно до завершения операции.")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = manualDraft ?: state.cfg, enabled = !busy, minLines = 6, modifier = Modifier.fillMaxWidth(), onValueChange = { manualDraft = it })
        ActionButton(enabled = manualDraft != null && !busy, onClick = {
            showRegistrationNotice = false
            val draft = manualDraft
            if (draft != null) {
                msg = try {
                    val error = vm.saveManualConfig(draft)
                    if (error != null) error else {
                        manualDraft = null
                        "Конфиг проверен и сохранён"
                    }
                } catch (_: SecretStorageException) {
                    "Не удалось безопасно сохранить конфиг. Черновик оставлен в редакторе."
                }
            }
        }) { Text("Сохранить ручной конфиг") }
        val message = if (state.creating || showRegistrationNotice) state.notice else msg
        Spacer(Modifier.height(8.dp))
        AnimatedNotice(message, Modifier.fillMaxWidth())
    }
}
