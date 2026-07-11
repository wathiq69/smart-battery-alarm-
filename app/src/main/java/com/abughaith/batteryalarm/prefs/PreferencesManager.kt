package com.abughaith.batteryalarm.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class PreferencesManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "smart_battery_alarm_prefs"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_CHARGE_SPEED_HOURS = "charge_speed_hours"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start_min"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end_min"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_BG_URI = "background_uri"
        private const val KEY_LAST_CITY = "last_city"
        private const val KEY_MUTE_UNTIL = "mute_until_ts"
        private const val KEY_LOW_ALERT_15 = "low_alert_15"
        private const val KEY_LOW_ALERT_10 = "low_alert_10"
        private const val KEY_LOW_ALERT_5 = "low_alert_5"

        @Volatile private var instance: PreferencesManager? = null
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var ownerName: String
        get() = prefs.getString(KEY_OWNER_NAME, "أبو غيث") ?: "أبو غيث"
        set(value) = prefs.edit().putString(KEY_OWNER_NAME, value).apply()

    var chargeSpeedHours: Float
        get() = prefs.getFloat(KEY_CHARGE_SPEED_HOURS, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_CHARGE_SPEED_HOURS, value).apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()

    var quietHoursStartMin: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_START, 23 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_START, value).apply()

    var quietHoursEndMin: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_END, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_HOURS_END, value).apply()

    var weatherEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_ENABLED, value).apply()

    var lastCity: String
        get() = prefs.getString(KEY_LAST_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CITY, value).apply()

    var backgroundUri: String
        get() = prefs.getString(KEY_BG_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BG_URI, value).apply()

    fun getBackgroundUri(): Uri? =
        if (backgroundUri.isBlank()) null else Uri.parse(backgroundUri)

    var muteUntilTs: Long
        get() = prefs.getLong(KEY_MUTE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_MUTE_UNTIL, value).apply()

    val isCurrentlyMuted: Boolean
        get() = muteUntilTs > 0 && System.currentTimeMillis() < muteUntilTs

    fun muteForHours(hours: Float) {
        muteUntilTs = System.currentTimeMillis() + (hours * 3600_000L).toLong()
    }

    fun muteUntilMorning(endHour: Int = 8) {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, endHour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        muteUntilTs = cal.timeInMillis
    }

    fun unmute() { muteUntilTs = 0L }

    fun isInQuietHours(): Boolean {
        if (!quietHoursEnabled) return false
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = quietHoursStartMin
        val end = quietHoursEndMin
        return if (start <= end) { nowMin in start until end }
        else { nowMin >= start || nowMin < end }
    }

    fun canSpeakNow(): Boolean = !isCurrentlyMuted && !isInQuietHours()

    var lowAlert15: Boolean
        get() = prefs.getBoolean(KEY_LOW_ALERT_15, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_ALERT_15, value).apply()

    var lowAlert10: Boolean
        get() = prefs.getBoolean(KEY_LOW_ALERT_10, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_ALERT_10, value).apply()

    var lowAlert5: Boolean
        get() = prefs.getBoolean(KEY_LOW_ALERT_5, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_ALERT_5, value).apply()

    fun resetLowAlertFlags() {
        prefs.edit()
            .putBoolean(KEY_LOW_ALERT_15, false)
            .putBoolean(KEY_LOW_ALERT_10, false)
            .putBoolean(KEY_LOW_ALERT_5, false)
            .apply()
    }
}
