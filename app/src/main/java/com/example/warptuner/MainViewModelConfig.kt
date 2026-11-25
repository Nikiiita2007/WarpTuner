package com.example.warptuner

import org.json.JSONException
import org.json.JSONObject

internal fun MainViewModel.configEditingBlocked(): Boolean =
    state.value.operation != null || TuneState.ui.value.running ||
        tunnelOperationCoordinator.idleSnapshot() == null

internal fun MainViewModel.applyConfigSettings(template: String, endpoint: String): Boolean {
    if (configEditingBlocked()) return false
    val account = store.account
    if (account.isBlank() || endpoint.any { it == '\r' || it == '\n' }) return false
    val candidate = try {
        val json = JSONObject(account)
        val warpAccount = WarpAccount(
            json.getString("priv"), json.getString("v4"), json.getString("v6"),
            json.getString("pub"), json.getString("ep")
        )
        buildConfig(warpAccount, template, endpoint.trim())
    } catch (_: JSONException) {
        return false
    }
    if (validateConfig(candidate) != null) return false
    store.cfg = candidate
    store.template = template
    store.endpoint = endpoint.trim()
    setCfg(candidate)
    return true
}

/** Same validation and normalization as file/clipboard import. Null means success. */
internal fun MainViewModel.saveManualConfig(config: String): String? {
    if (configEditingBlocked()) return "Дождитесь завершения текущей операции."
    val error = tryImport(config, store)
    if (error != null) return error
    setCfg(config.trim())
    return null
}
