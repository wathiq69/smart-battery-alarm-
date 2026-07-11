package com.abughaith.batteryalarm.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.abughaith.batteryalarm.prefs.PreferencesManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "WeatherManager"
        @Volatile private var instance: WeatherManager? = null
        fun getInstance(context: Context): WeatherManager {
            return instance ?: synchronized(this) {
                instance ?: WeatherManager(context.applicationContext).also { instance = it }
            }
        }
    }
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val prefs = PreferencesManager.getInstance(context)

    suspend fun getCurrentWeather(): String? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        try {
            val location = getLastLocation() ?: return@withContext null
            val lat = location.latitude
            val lon = location.longitude
            val cityName = getCityName(lat, lon) ?: prefs.lastCity.ifBlank { null }
            if (cityName != null) prefs.lastCity = cityName
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parseWeather(body, cityName)
            }
        } catch (e: Exception) { null }
    }

    private fun parseWeather(json: String, city: String?): String? {
        return try {
            val root = JSONObject(json)
            val cur = root.optJSONObject("current_weather") ?: return null
            val temp = cur.optDouble("temperature", Double.NaN)
            val code = cur.optInt("weathercode", -1)
            val desc = weatherCodeToArabic(code)
            val tempStr = String.format(Locale.US, "%.0f", temp)
            if (city.isNullOrBlank()) "$desc، $tempStr درجة مئوية" else "$desc، $tempStr درجة مئوية، في $city"
        } catch (e: Exception) { null }
    }

    private fun weatherCodeToArabic(code: Int): String = when (code) {
        0 -> "سماء صافية"
        1, 2, 3 -> "غائم جزئياً"
        45, 48 -> "ضباب"
        51, 53, 55 -> "رذاذ خفيف"
        61, 63, 65 -> "ممطر"
        66, 67 -> "مطر متجمد"
        71, 73, 75 -> "مثلج"
        80, 81, 82 -> "زخات مطرية"
        85, 86 -> "زخات ثلجية"
        95 -> "عواصف رعدية"
        96, 99 -> "عواصف رعدية مع بَرَد"
        else -> "حالة جوية غير محددة"
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(loc)
                    else {
                        try {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { l -> cont.resume(l) }
                                .addOnFailureListener { cont.resume(null) }
                        } catch (_: Exception) { cont.resume(null) }
                    }
                }.addOnFailureListener { cont.resume(null) }
        } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
    }

    private fun getCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
        } catch (e: Exception) { null }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
