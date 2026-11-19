package com.example.warptuner

import org.amnezia.awg.config.Config
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream

fun buildConfig(a: WarpAccount, template: String, endpointOverride: String): String = buildString {
    appendLine("[Interface]")
    appendLine("PrivateKey = ${a.privateKey}")
    appendLine("Address = ${a.v4}/32, ${a.v6}/128")
    appendLine("DNS = 1.1.1.1, 1.0.0.1")
    appendLine("MTU = 1280")
    template.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { appendLine(it) }
    appendLine()
    appendLine("[Peer]")
    appendLine("PublicKey = ${a.peerKey}")
    appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
    appendLine("Endpoint = ${endpointOverride.ifBlank { a.endpoint }}")
    appendLine("PersistentKeepalive = 25")
}

fun extractTemplate(conf: String): String {
    val skip = setOf("privatekey", "address", "dns", "mtu")
    var inIface = false
    val out = mutableListOf<String>()
    for (raw in conf.lines()) {
        val l = raw.trim()
        if (l.startsWith("[")) { inIface = l.equals("[Interface]", true); continue }
        if (!inIface || l.isEmpty()) continue
        if (l.substringBefore("=").trim().lowercase() in skip) continue
        out += l
    }
    return out.joinToString("\n")
}

private fun checkedConfig(account: WarpAccount, template: String, endpoint: String): String {
    require(endpoint.none { it == '\r' || it == '\n' }) { "Endpoint должен занимать одну строку." }
    val config = buildConfig(account, template, endpoint.trim())
    require(validateConfig(config) == null) { "Сгенерирован некорректный конфиг. Проверьте шаблон и endpoint." }
    return config
}

fun rebuildConfig(store: Store): String? {
    val account = store.account
    if (account.isBlank()) return null
    val a = try {
        val j = JSONObject(account)
        WarpAccount(j.getString("priv"), j.getString("v4"), j.getString("v6"), j.getString("pub"), j.getString("ep"))
    } catch (_: JSONException) {
        return null
    }
    val config = try {
        checkedConfig(a, store.template, store.endpoint)
    } catch (_: IllegalArgumentException) {
        return null
    }
    store.cfg = config
    return config
}

fun createAccount(store: Store): String = createAccount(store) {}

internal fun createAccount(store: Store, checkActive: () -> Unit): String {
    checkActive()
    val a = WarpApi.registerCheckingCancellation(checkActive)
    checkActive()
    val config = checkedConfig(a, store.template, store.endpoint)
    val account = JSONObject()
        .put("priv", a.privateKey).put("v4", a.v4).put("v6", a.v6)
        .put("pub", a.peerKey).put("ep", a.endpoint)
        .toString()
    checkActive()
    store.account = account
    store.cfg = config
    return config
}

fun friendlyError(e: Throwable): String {
    val m = (e.message ?: "").lowercase()
    val network = listOf("таймаут", "timeout", "unable to resolve", "failed to connect", "unreachable")
    return if (network.any { it in m }) {
        "Не удалось связаться с Cloudflare для регистрации: в вашей сети, вероятно, заблокирован доступ к API.\n\n" +
                "Что можно сделать:\n" +
                "1) Включите на минуту любой другой VPN, нажмите «Создать» ещё раз и после успеха отключите его.\n" +
                "2) Либо вставьте готовый конфиг в Настройках."
    } else {
        "Не удалось создать конфиг: ${e.message}"
    }
}

fun applySplit(cfg: String, store: Store, ownPkg: String): String =
    applySplit(cfg, store.splitMode, store.splitApps, ownPkg)

fun applySplit(cfg: String, mode: String, appsCsv: String, ownPkg: String): String {
    val lines = cfg.lines()
    val clean = lines.filterNot { l ->
        val k = l.substringBefore("=").trim().lowercase()
        k == "excludedapplications" || k == "includedapplications"
    }
    val apps = appsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    if (mode == "include") apps += ownPkg else apps -= ownPkg
    if (mode == "off" || apps.isEmpty()) {
        return if (clean.size == lines.size) cfg else clean.joinToString("\n")
    }
    val key = if (mode == "include") "IncludedApplications" else "ExcludedApplications"
    val out = mutableListOf<String>()
    var done = false
    for (l in clean) {
        out += l
        if (!done && l.trim().equals("[Interface]", ignoreCase = true)) {
            out += "$key = ${apps.joinToString(", ")}"
            done = true
        }
    }
    return out.joinToString("\n")
}

fun validateConfig(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return "Нечего импортировать: пусто."
    if (t.startsWith("vpn://")) {
        return "Формат vpn:// не поддерживается. Нужен обычный текст конфига (.conf) с блоками [Interface] и [Peer]."
    }
    return try {
        Config.parse(ByteArrayInputStream(t.toByteArray()))
        null
    } catch (e: Throwable) {
        "Конфиг не распознан: ${e.message}"
    }
}

fun tryImport(raw: String, store: Store): String? {
    validateConfig(raw)?.let { return it }
    try {
        store.cfg = raw.trim()
    } catch (_: SecretStorageException) {
        return "Не удалось безопасно сохранить конфиг. Импорт отменён."
    }
    store.account = ""
    return null
}
