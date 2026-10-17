package com.batterysot.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("battery_sot_prefs", Context.MODE_PRIVATE)
        when (intent.action) {
            Intent.ACTION_POWER_DISCONNECTED -> {
                val level = getCurrentBatteryLevel(context)
                prefs.edit()
                    .putLong("unplug_timestamp", System.currentTimeMillis())
                    .putInt("unplug_battery_level", level)
                    .putBoolean("is_charging", false)
                    .apply()
            }
            Intent.ACTION_POWER_CONNECTED -> {
                prefs.edit()
                    .putBoolean("is_charging", true)
                    .apply()
            }
        }
    }

    companion object {
        fun getCurrentBatteryLevel(context: Context): Int {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level != -1 && scale > 0) (level * 100) / scale else 100
        }

        fun isCurrentlyCharging(context: Context): Boolean {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL
        }
    }
}
