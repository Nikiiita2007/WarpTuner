package com.example.warptuner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun I1FallbackControls(vm: MainViewModel, busy: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(ctx) { I1FallbackRepository(ctx) }
    val account = vm.store.account
    var revision by remember { mutableStateOf(0) }
    val configured = remember(account, revision) { repository.read(account) != null }
    var reading by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var messageIsError by rememberSaveable { mutableStateOf(false) }
    var requestedToken by rememberSaveable { mutableStateOf<String?>(null) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    val loading = reading || requestedToken != null
    val editable = !busy && !loading && account.isNotBlank()

    fun report(text: String, error: Boolean = false) {
        message = text
        messageIsError = error
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = requestedToken
        requestedToken = null
        if (uri == null) {
            report("Выбор отменён. Настройки не изменены.")
        } else {
            scope.launch {
                reading = true
                try {
                    val value = withContext(Dispatchers.IO) {
                        val context = currentCoroutineContext()
                        val input = ctx.contentResolver.openInputStream(uri) ?: error("Unavailable input")
                        input.use { readSelectedI1(it) { context.ensureActive() } }
                    }
                    if (vm.configEditingBlocked()) throw I1SelectionException(I1SelectionProblem.BUSY)
                    val currentAccount = vm.store.account
                    if (token.isNullOrBlank() || token != i1SelectionAccountToken(currentAccount)) {
                        throw I1SelectionException(I1SelectionProblem.ACCOUNT_CHANGED)
                    }
                    val candidate = profileWithI1(vm.store.cfg, value)
                    if (validateConfig(candidate) != null) throw I1SelectionException(I1SelectionProblem.INVALID_CONFIG)
                    repository.save(currentAccount, value)
                    revision++
                    report("Готово. Теперь достаточно нажать «Подключить». Из файла сохранён только запасной I1; ключи и текущий профиль не заменены.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: I1SelectionException) {
                    report(when (e.problem) {
                        I1SelectionProblem.TOO_LARGE -> "Файл слишком большой. Выберите текстовый .conf размером не более 128 КиБ."
                        I1SelectionProblem.MISSING_I1 -> "В файле нужен один непустой I1 в единственном разделе [Interface]. Дубликаты не поддерживаются."
                        I1SelectionProblem.ACCOUNT_CHANGED -> "Аккаунт изменился или выбор не удалось восстановить. Выберите файл заново для текущего аккаунта."
                        I1SelectionProblem.BUSY -> "Дождитесь окончания подключения или подбора, затем выберите файл снова."
                        I1SelectionProblem.INVALID_CONFIG -> "Этот I1 не проходит проверку с текущим профилем. Выберите другой рабочий .conf."
                    }, true)
                } catch (_: SecretStorageException) {
                    report("Не удалось зашифровать запасной вариант. Настройка не сохранена; текущий профиль не заменён.", true)
                } catch (_: IllegalArgumentException) {
                    report("Текущий профиль не подходит для замены I1. Проверьте его раздел [Interface].", true)
                } catch (_: Exception) {
                    report("Не удалось прочитать или обработать файл. Попробуйте выбрать локальный текстовый .conf ещё раз.", true)
                } finally {
                    reading = false
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Автоподбор подключения", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(when {
                account.isBlank() -> "Недоступен для импортированного или ручного профиля."
                loading -> "Подготавливаю запасной вариант…"
                configured -> "Включён: настроен один запасной I1."
                else -> "Не настроен. Подключение использует текущий профиль."
            }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Один раз выберите свой рабочий .conf. Если текущий вариант не подтвердит WARP, приложение попробует запасной I1 и запомнит его после успеха. Это не подбор максимальной скорости.")
            if (account.isBlank()) {
                Text("Настройка доступна после создания аккаунта в приложении. Рабочий импорт менять не нужно.")
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (busy) Text("Настройка временно недоступна: выполняется другая операция.")
            Button(enabled = editable, modifier = Modifier.fillMaxWidth(), onClick = {
                if (!vm.configEditingBlocked()) {
                    requestedToken = i1SelectionAccountToken(vm.store.account).takeIf { it.isNotBlank() }
                    if (requestedToken != null) {
                        try { chooser.launch(arrayOf("*/*")) }
                        catch (_: Exception) {
                            requestedToken = null
                            report("Не удалось открыть выбор файла. Попробуйте ещё раз.", true)
                        }
                    }
                }
            }) { Text(if (configured) "Выбрать другой рабочий файл" else "Выбрать рабочий .conf") }
            TextButton(enabled = editable && configured, onClick = { showDisableDialog = true }) {
                Text("Отключить автоподбор")
            }
            if (message.isNotEmpty()) {
                Text(message, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("Отключить автоподбор?") },
            text = { Text("Запасной I1 будет удалён. Уже сохранённый рабочий профиль останется без изменений; его I1 не будет возвращён к старому значению.") },
            confirmButton = {
                TextButton(enabled = editable && configured, onClick = {
                    if (!vm.configEditingBlocked()) {
                        repository.clear()
                        revision++
                        showDisableDialog = false
                        report("Автоподбор отключён. Текущий профиль не изменён.")
                    }
                }) { Text("Отключить") }
            },
            dismissButton = { TextButton(onClick = { showDisableDialog = false }) { Text("Оставить включённым") } },
        )
    }
}
