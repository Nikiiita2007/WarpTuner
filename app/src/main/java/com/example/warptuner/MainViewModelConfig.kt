package com.example.warptuner

import org.json.JSONException
import org.json.JSONObject

internal fun MainViewModel.applyConfigSettings(template: String, endpoint: String): Boolean {
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
    store.template = template
    store.endpoint = endpoint.trim()
    store.cfg = candidate
    setCfg(candidate)
    return true
}

internal fun MainViewModel.saveManualConfig(config: String) {
    store.cfg = config
    store.account = ""
    setCfg(config)
}
