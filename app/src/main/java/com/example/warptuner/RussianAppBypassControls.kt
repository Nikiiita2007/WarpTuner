package com.example.warptuner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RussianAppBypassControls() {
    val ctx = LocalContext.current
    val settings = remember(ctx) { RussianAppBypassSettings(ctx) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var overrides by remember { mutableStateOf(settings.overrides) }
    var installed by remember { mutableStateOf<List<BypassApp>?>(null) }
    LaunchedEffect(ctx) {
        installed = withContext(Dispatchers.IO) { installedRussianBypassApps(ctx) }
    }
    Column {
        Text("Российские приложения напрямую", style = MaterialTheme.typography.titleMedium)
        Text("Включено по умолчанию. Весь трафик отмеченных приложений идёт без WARP, включая иностранные адреса. Это каталог из 7 приложений, не полный список. Сайты в браузере не исключаются.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = {
                settings.enabled = it
                enabled = it
            })
            Text("Автоматически исключать установленные приложения из каталога")
        }
        Text("Применяется при следующем запуске туннеля, в том числе при подборе и восстановлении watchdog. Для текущего соединения требуется переподключение.", style = MaterialTheme.typography.bodySmall)
        Text("Снятие отметки отменяет только автоисключение: ручной список ниже продолжает действовать. Автоисключение имеет приоритет над ручным списком «только через VPN».", style = MaterialTheme.typography.bodySmall)
        val apps = installed
        if (apps == null) Text("Проверяю установленные приложения…")
        else if (apps.isEmpty()) Text("Приложения из каталога не найдены.")
        else apps.forEach { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = app.pkg !in overrides, enabled = enabled, onCheckedChange = { automatic ->
                    settings.setAutomatic(app.pkg, automatic)
                    overrides = settings.overrides
                })
                Column {
                    Text(app.label)
                    Text(if (enabled && app.pkg !in overrides) "Напрямую, без WARP" else "Без автоисключения", style = MaterialTheme.typography.bodySmall)
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
