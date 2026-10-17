package com.batterysot.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

class BatteryTrackerBridge(private val context: Context) {

    @JavascriptInterface
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @JavascriptInterface
    fun openUsageSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun resetCycleNow(): String {
        val prefs = context.getSharedPreferences("battery_sot_prefs", Context.MODE_PRIVATE)
        val currentLevel = PowerConnectionReceiver.getCurrentBatteryLevel(context)
        prefs.edit()
            .putLong("unplug_timestamp", System.currentTimeMillis())
            .putInt("unplug_battery_level", currentLevel)
            .apply()
        return getBatteryDataJson()
    }

    @JavascriptInterface
    fun getBatteryDataJson(): String {
        val prefs = context.getSharedPreferences("battery_sot_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val currentLevel = PowerConnectionReceiver.getCurrentBatteryLevel(context)
        val isCharging = PowerConnectionReceiver.isCurrentlyCharging(context)

        var unplugTime = prefs.getLong("unplug_timestamp", 0L)
        var unplugLevel = prefs.getInt("unplug_battery_level", currentLevel)

        // Si es la primera vez que se abre la app, inicializamos el ciclo hace 1 minuto
        if (unplugTime == 0L) {
            unplugTime = now - 60_000L
            unplugLevel = currentLevel
            prefs.edit()
                .putLong("unplug_timestamp", unplugTime)
                .putInt("unplug_battery_level", unplugLevel)
                .apply()
        }

        val hasPerm = hasUsagePermission()
        var sotMillis = 0L
        val appUsageMap = mutableMapOf<String, Long>()

        if (hasPerm) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(unplugTime, now)
            val event = UsageEvents.Event()

            var lastScreenOnTime: Long? = unplugTime // Asumimos encendida si ya estaba interactiva
            var seenFirstScreenEvent = false

            val appStartMap = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        seenFirstScreenEvent = true
                        lastScreenOnTime = event.timeStamp
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        if (!seenFirstScreenEvent) {
                            // Estaba encendida desde unplugTime hasta este evento
                            sotMillis += (event.timeStamp - unplugTime).coerceAtLeast(0L)
                            seenFirstScreenEvent = true
                        } else if (lastScreenOnTime != null) {
                            sotMillis += (event.timeStamp - lastScreenOnTime).coerceAtLeast(0L)
                        }
                        lastScreenOnTime = null
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        appStartMap[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = appStartMap.remove(event.packageName)
                        if (start != null && event.timeStamp > start) {
                            val delta = event.timeStamp - start
                            appUsageMap[event.packageName] = (appUsageMap[event.packageName] ?: 0L) + delta
                        }
                    }
                }
            }

            // Si la pantalla sigue encendida ahora mismo
            if (!seenFirstScreenEvent || lastScreenOnTime != null) {
                val ref = lastScreenOnTime ?: unplugTime
                sotMillis += (now - ref).coerceAtLeast(0L)
            }
        }

        val totalCycleMillis = (now - unplugTime).coerceAtLeast(1000L)
        val safeSotMillis = sotMillis.coerceAtMost(totalCycleMillis)
        val screenOffMillis = (totalCycleMillis - safeSotMillis).coerceAtLeast(0L)

        // Top 5 aplicaciones por tiempo de uso en este ciclo
        val pm = context.packageManager
        val topAppsJson = JSONArray()
        appUsageMap.entries
            .sortedByDescending { it.value }
            .take(5)
            .forEach { (pkg, durationMs) ->
                val appLabel = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg.substringAfterLast('.')
                }
                val obj = JSONObject()
                obj.put("name", appLabel)
                obj.put("package", pkg)
                obj.put("minutes", (durationMs / 60000.0))
                topAppsJson.put(obj)
            }

        val result = JSONObject()
        result.put("hasPermission", hasPerm)
        result.put("unplugTimestamp", unplugTime)
        result.put("nowTimestamp", now)
        result.put("unplugBatteryLevel", unplugLevel)
        result.put("currentBatteryLevel", currentLevel)
        result.put("isCharging", isCharging)
        result.put("totalCycleMinutes", totalCycleMillis / 60000.0)
        result.put("sotMinutes", safeSotMillis / 60000.0)
        result.put("screenOffMinutes", screenOffMillis / 60000.0)
        result.put("topApps", topAppsJson)

        return result.toString()
    }
}
