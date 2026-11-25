package com.example.warptuner

import android.content.Context
import android.content.pm.PackageManager

internal data class BypassApp(val pkg: String, val label: String)

// Exact IDs verified against https://www.rustore.ru/catalog/app/<package>.
// This is a curated starter catalog, not a nationality detector or a complete list.
internal val russianBypassCatalog = listOf(
    BypassApp("com.vkontakte.android", "ВКонтакте"),
    BypassApp("ru.rostel", "Госуслуги"),
    BypassApp("ru.gosuslugi.pos", "Госуслуги Решаем вместе"),
    BypassApp("ru.gosuslugi.auto", "Госуслуги Авто"),
    BypassApp("ru.rtlabs.mobile.ebs.gosuslugi.android", "Госуслуги Биометрия"),
    BypassApp("ru.yandex.taxi", "Яндекс Go"),
    BypassApp("ru.yandex.yandexmaps", "Яндекс Карты"),
)

internal class RussianAppBypassSettings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("russian_app_bypass", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    val overrides: Set<String> get() = prefs.getStringSet("not_automatic", emptySet()).orEmpty().toSet()

    fun setAutomatic(pkg: String, automatic: Boolean) {
        val updated = overrides.toMutableSet()
        if (automatic) updated.remove(pkg) else updated.add(pkg)
        prefs.edit().putStringSet("not_automatic", updated).apply()
    }
}

@Suppress("DEPRECATION")
internal fun installedRussianBypassApps(ctx: Context): List<BypassApp> =
    russianBypassCatalog.filter { app ->
        try {
            ctx.packageManager.getPackageInfo(app.pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

internal fun russianAppBypassConfig(ctx: Context, config: String): String {
    val settings = RussianAppBypassSettings(ctx)
    if (!settings.enabled) return config
    val packages = installedRussianBypassApps(ctx).map { it.pkg }.toSet() - settings.overrides
    return mergeRussianAppBypass(config, packages, ctx.packageName)
}

/** Called only after the original configuration has passed backend parsing. */
internal fun mergeRussianAppBypass(config: String, packages: Set<String>, ownPkg: String): String {
    val bypass = packages - ownPkg
    if (bypass.isEmpty()) return config
    var inInterface = false
    var hasIncluded = false
    val included = linkedSetOf<String>()
    val excluded = linkedSetOf<String>()
    for (raw in config.lines()) {
        val line = raw.substringBefore('#').trim()
        if (line.startsWith("[")) {
            inInterface = line.equals("[Interface]", true)
            continue
        }
        if (!inInterface || '=' !in line) continue
        val key = line.substringBefore('=').trim()
        val values = line.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }
        when {
            key.equals("IncludedApplications", true) -> {
                hasIncluded = true
                included.addAll(values)
            }
            key.equals("ExcludedApplications", true) -> excluded.addAll(values)
        }
    }
    return if (hasIncluded) {
        applySplit(config, "include", (included - bypass).joinToString(","), ownPkg)
    } else {
        applySplit(config, "exclude", (excluded + bypass).joinToString(","), ownPkg)
    }
}
