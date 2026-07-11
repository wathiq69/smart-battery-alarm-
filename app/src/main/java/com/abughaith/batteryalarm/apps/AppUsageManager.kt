package com.abughaith.batteryalarm.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings
import java.util.concurrent.TimeUnit

class AppUsageManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: AppUsageManager? = null
        fun getInstance(context: Context): AppUsageManager {
            return instance ?: synchronized(this) {
                instance ?: AppUsageManager(context.applicationContext).also { instance = it }
            }
        }
    }
    data class AppInfo(
        val packageName: String,
        val label: String,
        val foregroundTimeMs: Long,
        val lastTimeUsed: Long,
        val icon: Drawable?,
        val isSystemApp: Boolean
    )
    private val usageStatsManager: UsageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    private val packageManager: PackageManager get() = context.packageManager

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    fun getTopAppsByUsage(limit: Int = 15): List<AppInfo> {
        if (!hasUsageStatsPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val start = now - TimeUnit.HOURS.toMillis(24)
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return emptyList()
        val byPackage = stats.groupBy { it.packageName }.mapValues { e -> e.value.maxByOrNull { it.lastTimeUsed }!! }
        val pm = packageManager
        return byPackage.values
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .mapNotNull { stat ->
                try {
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    AppInfo(
                        packageName = stat.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        foregroundTimeMs = stat.totalTimeInForeground,
                        lastTimeUsed = stat.lastTimeUsed,
                        icon = pm.getApplicationIcon(info),
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                } catch (e: PackageManager.NameNotFoundException) { null }
            }
    }

    fun formatUsageTime(timeMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) - TimeUnit.HOURS.toMinutes(hours)
        return when {
            hours > 0 && minutes > 0 -> "$hours ساعة و $minutes دقيقة"
            hours > 0 -> "$hours ساعة"
            minutes > 0 -> "$minutes دقيقة"
            else -> "أقل من دقيقة"
        }
    }

    fun openAppSettingsForForceStop(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
