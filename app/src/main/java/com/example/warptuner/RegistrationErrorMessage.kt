package com.example.warptuner

import kotlinx.coroutines.CancellationException

/** Never classify by arbitrary exception text or display raw response/exception data. */
internal fun registrationErrorMessage(error: Throwable): String = when (error) {
    is CancellationException -> throw error
    is RegistrationNetworkException ->
        "Не удалось установить соединение с API Cloudflare для регистрации. " +
            "Причина сетевого сбоя пока не установлена; это не подтверждение блокировки.\n\n" +
            "Попробуйте другую сеть. Если прямой доступ недоступен, можно временно использовать " +
            "доверенный VPN или импортировать собственный рабочий конфиг в Настройках. " +
            "Не удаляйте сохранённый рабочий конфиг."
    is SecretStorageException ->
        "Не удалось безопасно сохранить данные регистрации. " +
            "Смена сети или включение VPN не устранят ошибку локального шифрования."
    else ->
        "Не удалось завершить создание конфига. " +
            "Причину нужно уточнить по безопасной диагностике; сетевой сбой не подтверждён."
}
