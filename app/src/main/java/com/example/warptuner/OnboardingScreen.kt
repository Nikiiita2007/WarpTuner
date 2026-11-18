package com.example.warptuner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val titles = listOf(
        "Что делает приложение",
        "Как получить конфиг",
        "Если регистрация не проходит"
    )
    val texts = listOf(
        listOf(
            "WarpTuner подключает бесплатный WARP от Cloudflare через AmneziaWG и подбирает под вашу сеть " +
                    "параметры «маскировки» трафика (Jc, Jmin, Jmax) и endpoint.",
            "Подбор идёт в фоне: приложение можно свернуть, в шторке будет уведомление с кнопкой «Остановить»."
        ),
        listOf(
            "Вариант 1. Нажмите «Создать автоматически»: приложение само зарегистрирует бесплатное устройство в Cloudflare.",
            "Вариант 2. Если конфиг уже есть, скопируйте его и нажмите «Из буфера», либо откройте файл .conf (кнопка «Файл»), " +
                    "либо отсканируйте QR-код."
        ),
        listOf(
            "В некоторых сетях доступ к API Cloudflare заблокирован, и создание конфига заканчивается ошибкой «таймаут».",
            "Включите на минуту любой другой VPN, нажмите «Создать автоматически» ещё раз и после успеха отключите чужой VPN.",
            "Либо импортируйте готовый конфиг из буфера, файла или QR-кода."
        )
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Шаг ${step + 1} из 3", style = MaterialTheme.typography.bodySmall)
        Text(titles[step], style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        texts[step].forEach {
            Text(it)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Назад") }
            } else {
                TextButton(onClick = onDone) { Text("Пропустить") }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step < 2) step++ else onDone() }) {
                Text(if (step < 2) "Далее" else "Начать")
            }
        }
    }
}
