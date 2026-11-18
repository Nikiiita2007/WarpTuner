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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
