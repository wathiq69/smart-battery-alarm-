package com.abughaith.batteryalarm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abughaith.batteryalarm.App
import com.abughaith.batteryalarm.R
import com.abughaith.batteryalarm.prefs.PreferencesManager
import com.abughaith.batteryalarm.tts.TtsManager
import com.abughaith.batteryalarm.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

class BatteryMonitorService : Service() {
    companion object {
        private const val TAG = "BatteryMonitorSvc"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.abughaith.batteryalarm.START"
        const val ACTION_STOP = "com.abughaith.batteryalarm.STOP"
        private val LOW_THRESHOLDS = listOf(15, 10, 5)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { PreferencesManager.getInstance(this) }
    private val tts by lazy { TtsManager.getInstance(this) }
    private var lastPercent: Int = -1
    private var lastCharging: Boolean = false
    private var lastFullAnnounced: Boolean = false
    private var lastChargingStepAnnounced: Int = -1
    private var lastNotifiedPercent: Int = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                handleBatteryChange(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMonitoring(); return START_NOT_STICKY }
            else -> { startForegroundWithNotification(); registerBatteryReceiver() }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notif = buildMonitoringNotification(50, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else { startForeground(NOTIF_ID, notif) }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        val sticky = registerReceiver(null, filter)
        sticky?.let { handleBatteryChange(it) }
    }

    private fun handleBatteryChange(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level.toFloat() / scale.toFloat() * 100f).toInt().coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // تحديث الإشعار فقط كل 5% لتوفير البطارية
        if (lastNotifiedPercent == -1 || kotlin.math.abs(percent - lastNotifiedPercent) >= 5 || isCharging != lastCharging) {
            updateMonitoringNotification(percent, isCharging)
            lastNotifiedPercent = percent
        }

        val chargingStateChanged = isCharging != lastCharging
        if (isCharging) {
            if (chargingStateChanged) {
                onChargerConnected(percent)
                lastChargingStepAnnounced = -1
                lastFullAnnounced = false
                prefs.resetLowAlertFlags()
            }
            if (percent in listOf(20, 30, 40, 50, 60, 70, 80, 90)) {
                if (percent != lastChargingStepAnnounced) { onChargingProgress(percent); lastChargingStepAnnounced = percent }
            }
            if (percent == 100 && !lastFullAnnounced) { onChargingComplete(); lastFullAnnounced = true }
        } else {
            if (chargingStateChanged && lastCharging) {
                onChargerDisconnected(percent)
                lastFullAnnounced = false
                lastChargingStepAnnounced = -1
            }
            if (percent in LOW_THRESHOLDS) {
                if (percent == 15 && !prefs.lowAlert15) { onLowBattery(percent); prefs.lowAlert15 = true }
                else if (percent == 10 && !prefs.lowAlert10) { onLowBattery(percent); prefs.lowAlert10 = true }
                else if (percent == 5 && !prefs.lowAlert5) { onLowBattery(percent); prefs.lowAlert5 = true }
            }
            if (percent > 15) {
                if (prefs.lowAlert15) prefs.lowAlert15 = false
                if (prefs.lowAlert10) prefs.lowAlert10 = false
                if (prefs.lowAlert5) prefs.lowAlert5 = false
            }
        }
        lastPercent = percent
        lastCharging = isCharging
    }

    private fun onLowBattery(percent: Int) {
        val msg = "مستوى شحن البطارية الآن هو $percent بالمئة، جهازك بحاجة إلى الشحن"
        speakAndAlert(msg, "تنبيه: شحن منخفض ($percent%)", NotificationManager.IMPORTANCE_HIGH)
    }

    private fun onChargerConnected(percent: Int) {
        val speedHours = prefs.chargeSpeedHours
        val remainingMin = ((100 - percent) / 100f * speedHours * 60f).toInt()
        val msg = "يتم شحن جهازك الآن وتبقى $remainingMin دقيقة على انتهاء الشحن"
        speakAndAlert(msg, "تم توصيل الشاحن", NotificationManager.IMPORTANCE_DEFAULT)
    }

    private fun onChargingProgress(percent: Int) {
        val msg = "نسبة الشحن الآن هي $percent بالمئة"
        speakAndAlert(msg, "تقدم الشحن: $percent%", NotificationManager.IMPORTANCE_LOW)
    }

    private fun onChargingComplete() {
        val msg = "مستوى الشحن وصل إلى 100 بالمئة، يمكنك الآن فصل الشاحنة من الجهاز"
        speakAndAlert(msg, "اكتمل الشحن!", NotificationManager.IMPORTANCE_HIGH)
    }

    private fun onChargerDisconnected(percent: Int) {
        val msg = "تم فصل الشاحنة ونسبة الشحن الآن هي $percent بالمئة"
        speakAndAlert(msg, "تم فصل الشاحن ($percent%)", NotificationManager.IMPORTANCE_DEFAULT)
    }

    private fun speakAndAlert(text: String, title: String, importance: Int) {
        if (prefs.canSpeakNow()) { tts.speakNow(text) }
        showAlertNotification(text, title, importance)
    }

    private fun showAlertNotification(text: String, title: String, importance: Int) {
        val channelId = if (importance >= NotificationManager.IMPORTANCE_HIGH) App.CHANNEL_ID_ALERTS else App.CHANNEL_ID_MONITORING
        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).setPriority(importance).build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun buildMonitoringNotification(percent: Int, isCharging: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val statusText = if (isCharging) "جاري الشحن" else "يعمل بالبطارية"
        return NotificationCompat.Builder(this, App.CHANNEL_ID_MONITORING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setContentText(getString(R.string.notification_monitoring_text, percent) + " - " + statusText)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateMonitoringNotification(percent: Int, isCharging: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildMonitoringNotification(percent, isCharging))
    }

    private fun stopMonitoring() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
