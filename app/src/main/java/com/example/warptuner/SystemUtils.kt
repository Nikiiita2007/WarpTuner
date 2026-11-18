package com.example.warptuner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

// Distributed outside Google Play; the watchdog must keep running in Doze to keep the VPN alive.
@SuppressLint("BatteryLife")
fun batteryRequestIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${ctx.packageName}".toUri())

fun appSettingsIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${ctx.packageName}".toUri())
