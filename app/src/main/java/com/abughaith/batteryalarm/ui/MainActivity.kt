package com.abughaith.batteryalarm.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abughaith.batteryalarm.R
import com.abughaith.batteryalarm.apps.AppUsageManager
import com.abughaith.batteryalarm.databinding.ActivityMainBinding
import com.abughaith.batteryalarm.prefs.PreferencesManager
import com.abughaith.batteryalarm.service.BatteryMonitorService
import com.abughaith.batteryalarm.tts.TtsManager
import com.abughaith.batteryalarm.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferencesManager.getInstance(this) }
    private val tts by lazy { TtsManager.getInstance(this) }
    private val weather by lazy { WeatherManager.getInstance(this) }
    private val appUsage by lazy { AppUsageManager.getInstance(this) }
    private val appsAdapter by lazy { AppUsageAdapter { pkg -> appUsage.openAppSettingsForForceStop(pkg) } }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { intent?.let { updateBatteryUi(it) } }
    }
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) { refreshAppsList(); speakWelcomeMessage() }
        else { Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show() }
    }
    private val bgPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.backgroundUri = uri.toString()
                applyBackground()
                Toast.makeText(this, "تم تغيير الخلفية", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {}
        }
    }
       override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindow(); setupRecyclerView(); setupClickListeners(); applyBackground()
        applyAnimations()
        checkAndRequestPermissions()
    }

    private fun applyAnimations() {
        try {
            val fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
            val slideUp = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up)
            binding.tvBatteryPercent.startAnimation(fadeIn)
            binding.progressBattery.startAnimation(slideUp)
            binding.tvStatus.startAnimation(fadeIn)
        } catch (e: Exception) {}
    } 
    }
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        refreshAppsList()
    }
    override fun onPause() { super.onPause(); try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {} }
    private fun setupWindow() {
        window.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
    private fun setupRecyclerView() {
        binding.recyclerApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appsAdapter
            isNestedScrollingEnabled = false
        }
    }
    private fun setupClickListeners() {
        binding.btnStartMonitoring.setOnClickListener { startMonitoringService() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnChangeBackground.setOnClickListener { bgPicker.launch(arrayOf("image/*")) }
        binding.btnMuteTemp.setOnClickListener { showMuteDialog() }
        binding.btnRefreshApps.setOnClickListener {
            if (appUsage.hasUsageStatsPermission()) refreshAppsList() else appUsage.openUsageAccessSettings()
        }
        binding.btnGrantUsage.setOnClickListener { appUsage.openUsageAccessSettings() }
    }
    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.isNotEmpty()) permissionsLauncher.launch(needed.toTypedArray())
        else { startMonitoringService(); speakWelcomeMessage() }
        updateUsagePermissionHint()
    }
    private fun updateUsagePermissionHint() {
        val granted = appUsage.hasUsageStatsPermission()
        binding.tvPermissionHint.visibility = if (granted) View.GONE else View.VISIBLE
        binding.btnGrantUsage.visibility = if (granted) View.GONE else View.VISIBLE
    }
    private fun startMonitoringService() {
        val intent = Intent(this, BatteryMonitorService::class.java).apply { action = BatteryMonitorService.ACTION_START }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, intent)
        else startService(intent)
        Toast.makeText(this, "تم تشغيل المراقبة", Toast.LENGTH_SHORT).show()
    }
    private fun stopMonitoringService() {
        val intent = Intent(this, BatteryMonitorService::class.java).apply { action = BatteryMonitorService.ACTION_STOP }
        startService(intent)
    }
    private fun speakWelcomeMessage() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = intent?.let { computePercent(it) } ?: -1
        val owner = prefs.ownerName
        val baseMsg = "السلام عليكم استاذ $owner، مستوى شحن البطارية الآن هو $percent بالمئة"
        if (prefs.weatherEnabled && isOnline()) {
            lifecycleScope.launch {
                try {
                    val weatherInfo = withContext(Dispatchers.IO) { weather.getCurrentWeather() }
                    val fullMsg = if (weatherInfo != null) "$baseMsg. حالة الطقس لهذا اليوم هي $weatherInfo" else baseMsg
                    tts.speakNow(fullMsg)
                    binding.tvWeather.text = weatherInfo ?: getString(R.string.weather_unavailable)
                    binding.tvWeather.visibility = View.VISIBLE
                } catch (e: Exception) { tts.speakNow(baseMsg) }
            }
        } else { tts.speakNow(baseMsg); binding.tvWeather.visibility = View.GONE }
    }
    private fun computePercent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level.toFloat() / scale.toFloat() * 100f).toInt().coerceIn(0, 100)
    }
    private fun updateBatteryUi(intent: Intent) {
        val percent = computePercent(intent)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        binding.tvBatteryPercent.text = "$percent%"
        binding.progressBattery.progress = percent
        binding.tvStatus.text = when {
            percent == 100 -> getString(R.string.status_full)
            isCharging -> getString(R.string.status_charging)
            else -> getString(R.string.status_discharging)
        }
        val infoText = if (isCharging && percent < 100) {
            val remaining = ((100 - percent) / 100f * prefs.chargeSpeedHours * 60f).toInt()
            "متبقي ~$remaining دقيقة لإكمال الشحن"
        } else if (!isCharging) "البطارية تعمل بشكل طبيعي" else "اكتمل الشحن"
        binding.tvBatteryInfo.text = infoText
    }
    private fun refreshAppsList() {
        if (!appUsage.hasUsageStatsPermission()) { updateUsagePermissionHint(); return }
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { appUsage.getTopAppsByUsage(15) }
            appsAdapter.submitList(apps)
        }
    }
    private fun applyBackground() {
        val uri = prefs.getBackgroundUri()
        if (uri != null) {
            try {
                val input = contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                binding.backgroundImage.setImageBitmap(bmp)
                binding.backgroundImage.visibility = View.VISIBLE
            } catch (e: Exception) { binding.backgroundImage.visibility = View.GONE }
        } else { binding.backgroundImage.visibility = View.GONE }
    }
    private fun showMuteDialog() {
        val items = arrayOf(getString(R.string.mute_for_1h), getString(R.string.mute_for_3h), getString(R.string.mute_until_morning), getString(R.string.unmute))
        AlertDialog.Builder(this).setTitle("خيارات الكتم").setItems(items) { _, which ->
            when (which) {
                0 -> { prefs.muteForHours(1f); Toast.makeText(this, "كتم لساعة", Toast.LENGTH_SHORT).show() }
                1 -> { prefs.muteForHours(3f); Toast.makeText(this, "كتم لـ 3 ساعات", Toast.LENGTH_SHORT).show() }
                2 -> { prefs.muteUntilMorning(); Toast.makeText(this, "كتم حتى الصباح", Toast.LENGTH_SHORT).show() }
                3 -> { prefs.unmute(); Toast.makeText(this, "تم إلغاء الكتم", Toast.LENGTH_SHORT).show() }
            }
        }.show()
    }
    private fun showSettingsDialog() {
        val items = arrayOf(
            "تغيير اسم المالك (الحالي: ${prefs.ownerName})",
            "ضبط سرعة الشاحن (الحالي: ${prefs.chargeSpeedHours} ساعة)",
            "أوقات الهدوء (الحالي: ${if (prefs.quietHoursEnabled) "مفعل" else "معطل"})",
            "الطقس (الحالي: ${if (prefs.weatherEnabled) "مفعل" else "معطل"})",
            "فتح إعدادات استثناء البطارية في MIUI",
            "إيقاف المراقبة"
        )
        AlertDialog.Builder(this).setTitle(R.string.settings_title).setItems(items) { _, which ->
            when (which) {
                0 -> showOwnerNameDialog()
                1 -> showChargeSpeedDialog()
                2 -> { prefs.quietHoursEnabled = !prefs.quietHoursEnabled; Toast.makeText(this, "تم", Toast.LENGTH_SHORT).show() }
                3 -> { prefs.weatherEnabled = !prefs.weatherEnabled; Toast.makeText(this, "تم", Toast.LENGTH_SHORT).show() }
                4 -> openBatteryOptimization()
                5 -> stopMonitoringService()
            }
        }.show()
    }
    private fun showOwnerNameDialog() {
        val input = android.widget.EditText(this).apply { setText(prefs.ownerName); hint = "أدخل الاسم" }
        AlertDialog.Builder(this).setTitle("اسم المالك").setView(input)
            .setPositiveButton("حفظ") { _, _ -> prefs.ownerName = input.text.toString().ifBlank { "أبو غيث" }; Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("إلغاء", null).show()
    }
    private fun showChargeSpeedDialog() {
        val items = arrayOf("1 ساعة (شاحن سريع 33W+)", "1.5 ساعة (افتراضي)", "2 ساعة", "2.5 ساعة", "3 ساعات (شاحن بطيء)")
        val values = floatArrayOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
        AlertDialog.Builder(this).setTitle("سرعة الشاحن").setItems(items) { _, which ->
            prefs.chargeSpeedHours = values[which]; Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
        }.show()
    }
    private fun openBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            startActivity(intent)
        } catch (e: Exception) { Toast.makeText(this, "تعذر فتح الإعدادات", Toast.LENGTH_SHORT).show() }
    }
       private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetworkInfo
            net != null && net.isConnected
        } catch (e: Exception) {
            false
        }
    } 
}
