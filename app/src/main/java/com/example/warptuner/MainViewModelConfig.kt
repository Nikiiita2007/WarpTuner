package com.example.warptuner

internal fun MainViewModel.applyConfigSettings(template: String, endpoint: String): Boolean {
    store.template = template
    store.endpoint = endpoint
    val config = rebuildConfig(store) ?: return false
    setCfg(config)
    return true
}

internal fun MainViewModel.saveManualConfig(config: String) {
    store.cfg = config
    store.account = ""
    setCfg(config)
}
