package com.example.warptuner

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        "off" to "Ручной список выключен (автоисключения действуют отдельно)",
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
            "Ручной список применяется при нажатии «Подключить»; во время подбора не используется. " +
                    "Автоисключения ниже действуют при каждом запуске туннеля. Изменения требуют переподключения.",
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
        else Text("Выбрано вручную: ${selected.size}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            item(key = "russian-auto-bypass") { RussianAppBypassControls() }
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
