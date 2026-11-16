package dev.drewett.vista.data.weather

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class WeatherState(val city: String = "", val temperatureF: Int? = null, val condition: String = "", val stale: Boolean = false)

/** Optional coarse-city weather; no device location permission or API key is required. */
class WeatherRepository private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ambient-weather.json"))
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WeatherState())
    val state = _state.asStateFlow()
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var fetched = 0L

    suspend fun configure(city: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(city.trim().length in 1..120) { "Enter a city and state." }
            val parts = city.trim().split(',').map(String::trim)
            val data = json("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(parts.first(), "UTF-8")}&count=10&language=en&format=json")
            val results = data.optJSONArray("results") ?: error("City not found")
            val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            val place = candidates.firstOrNull { parts.size > 1 && it.optString("admin1").equals(parts[1], true) }
                ?: candidates.firstOrNull() ?: error("City not found")
            latitude = place.getDouble("latitude"); longitude = place.getDouble("longitude")
            _state.value = WeatherState(place.getString("name"))
            fetched = 0
            write()
            refreshLocked()
        }
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (latitude == null && file.baseFile.exists()) {
                try {
                    val saved = JSONObject(file.readFully().toString(Charsets.UTF_8))
                    latitude = saved.getDouble("lat"); longitude = saved.getDouble("lon")
                    fetched = saved.optLong("fetched")
                    _state.value = WeatherState(saved.getString("city"), if (saved.has("temperature")) saved.getInt("temperature") else null, saved.optString("condition"), true)
                } catch (_: Exception) { return@withLock }
            }
            if (latitude == null || (!force && System.currentTimeMillis() - fetched < 15 * 60_000)) return@withLock
            try { refreshLocked() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(stale = true) }
        }
    }

    private fun refreshLocked() {
        val data = json("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=auto")
        val current = data.getJSONObject("current")
        val temperature = current.getDouble("temperature_2m")
        require(temperature.isFinite() && temperature in -150.0..180.0)
        val code = current.getInt("weather_code")
        val condition = when (code) {
            0 -> "Clear"; 1,2 -> "Partly cloudy"; 3 -> "Cloudy"; 45,48 -> "Fog"
            51,53,55,56,57 -> "Drizzle"; 61,63,65,66,67,80,81,82 -> "Rain"
            71,73,75,77,85,86 -> "Snow"; 95,96,99 -> "Thunderstorms"; else -> "Weather"
        }
        _state.value = _state.value.copy(temperatureF = kotlin.math.round(temperature).toInt(), condition = condition, stale = false)
        fetched = System.currentTimeMillis()
        write()
    }

    private fun json(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            require(connection.responseCode == 200)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512 * 1024)
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 512 * 1024)
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private fun write() {
        val state = _state.value
        val data = JSONObject().put("city", state.city).put("lat", latitude).put("lon", longitude)
            .put("fetched", fetched).put("condition", state.condition)
        state.temperatureF?.let { data.put("temperature", it) }
        val stream = file.startWrite()
        try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object {
        @Volatile private var instance: WeatherRepository? = null
        fun get(context: Context): WeatherRepository = instance ?: synchronized(this) {
            instance ?: WeatherRepository(context.applicationContext).also { instance = it }
        }
    }
}
