package com.example.warptuner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
internal fun I1FallbackControls(vm: MainViewModel, busy: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(ctx) { I1FallbackRepository(ctx) }
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var requestedAccount by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(repository.read(vm.store.account) != null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            reading = true
            try {
                val value = withContext(Dispatchers.IO) {
                    val source = ctx.contentResolver.openInputStream(uri) ?: error("No input")
                    val text = source.use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= 131072) { "File too large" }
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toString("UTF-8")
                    }
                    profileI1(text) ?: error("No unique I1")
                }
                check(!vm.configEditingBlocked() && requestedAccount.isNotBlank() && vm.store.account == requestedAccount)
                val candidate = profileWithI1(vm.store.cfg, value)
                check(validateConfig(candidate) == null)
                repository.save(requestedAccount, value)
                configured = true
                message = "Автоповтор включён для текущего аккаунта. Из файла сохранён только I1; текущий конфиг не заменён."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Не сохранено: нужен небольшой текстовый .conf с одним I1 в [Interface], текущий аккаунт и отсутствие другой операции." }
            finally { reading = false }
        }
    }
    Text("Автоматический запасной I1")
    Text("Только для профиля созданного аккаунта. Одна повторная попытка, если проверка WARP не прошла. Импортированные профили автоматически не изменяются.")
    TextButton(enabled = !busy && !reading && vm.store.account.isNotBlank(), onClick = {
        requestedAccount = vm.store.account
        chooser.launch(arrayOf("*/*"))
    }) { Text("Выбрать рабочий .conf для запасного I1") }
    TextButton(enabled = !busy && !reading, onClick = {
        repository.clear()
        configured = false
        message = "Автоповтор выключен. Текущий конфиг не изменён."
    }) { Text("Выключить автоповтор") }
    Text(if (reading) "Читаю выбранный файл…" else if (configured) "Запасной вариант настроен." else "Запасной вариант не выбран.")
    if (message.isNotEmpty()) Text(message)
}
