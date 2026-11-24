package com.example.warptuner

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun MainViewModel.connectUsingI1(text: String) {
    val ctx = getApplication<Application>()
    val repository = I1FallbackRepository(ctx)
    var shutdownUnconfirmed = false
    try {
        val account = store.account
        val original = store.cfg
        check(text.trim() == original.trim()) { "Profile changed" }
        val alternative = repository.read(account)?.let { value ->
            runCatching { profileWithI1(text, value) }.getOrNull()
        }?.takeIf { it != text && validateConfig(it) == null }
        WatchdogService.stop(ctx)
        notify("Подключаюсь…")
        val selected = connectAndRememberProfile(
            original = text,
            alternative = alternative,
            verify = { candidate ->
                val status = withContext(Dispatchers.IO) {
                    Engine.up(ctx, applySplit(candidate, store, ctx.packageName))
                }
                notify("Туннель поднят ($status), проверяю связь…")
                withContext(Dispatchers.IO) { Prober.waitReady(12000) }
            },
            disconnect = {
                val down = try {
                    withContext(Dispatchers.IO) { Engine.down(ctx) == "DOWN" }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
                shutdownUnconfirmed = !down
                if (!down) notify("Не удалось подтвердить отключение VPN. Проверьте состояние туннеля.")
                down
            },
            retry = { notify("Пробую запасной вариант I1…") },
            remember = { chosen -> repository.remember(store, account, text, chosen) },
        )
        if (selected == null) {
            notify("Связь через WARP не подтверждена. Проверка могла не получить ответ. Тестовый туннель отключён; сохранённый профиль не изменён.")
        } else {
            setCfg(selected)
            notify(if (selected != text) "Подключено через запасной I1. Рабочий вариант сохранён."
                else "Подключено: проверка подтвердила WARP.")
            if (state.value.watchOn) WatchdogService.start(ctx)
        }
    } catch (e: CancellationException) {
        if (!shutdownUnconfirmed) notify("Подключение отменено.")
        throw e
    } catch (_: Exception) {
        if (!shutdownUnconfirmed) notify("Подключение не завершено. Проверьте выбранный профиль и настройки; повтор не выполнен либо тестовый туннель отключён.")
    }
}
