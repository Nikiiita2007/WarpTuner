package com.example.warptuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val store = vm.store
    var tpl by remember { mutableStateOf(store.template) }
    var ep by remember { mutableStateOf(store.endpoint) }
    var msg by remember { mutableStateOf("") }
    var showRegistrationNotice by remember { mutableStateOf(state.creating) }

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
        Button(enabled = !state.creating, onClick = {
            showRegistrationNotice = true
            vm.createAccount()
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
            TextButton(onClick = { tpl = extractTemplate(state.cfg) }) { Text("Взять из конфига") }
        }
        Spacer(Modifier.height(12.dp))

        Text("3. Endpoint (необязательно)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ep, onValueChange = { ep = it }, singleLine = true,
            label = { Text("адрес:порт") }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            showRegistrationNotice = false
            store.template = tpl
            store.endpoint = ep
            val c = rebuildConfig(store)
            if (c != null) {
                vm.setCfg(c)
                msg = "Шаблон и endpoint применены к конфигу аккаунта"
            } else {
                msg = "Сохранено. Для пересборки конфига сначала создайте аккаунт"
            }
        }) { Text("Сохранить и применить") }
        Spacer(Modifier.height(12.dp))

        Text("4. Конфиг вручную", style = MaterialTheme.typography.titleMedium)
        Text("Любое изменение здесь переводит приложение в ручной режим: конфиг аккаунта больше не пересобирается.")
        ImportButtons(store, onDone = vm::setCfg, onMsg = {
            showRegistrationNotice = false
            msg = it
        })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.cfg, minLines = 6, modifier = Modifier.fillMaxWidth(),
            onValueChange = {
                store.cfg = it
                store.account = ""
                vm.setCfg(it)
            }
        )
        val message = if (state.creating || showRegistrationNotice) state.notice else msg
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message)
        }
    }
}
