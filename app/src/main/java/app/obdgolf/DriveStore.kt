package app.obdgolf

import android.content.Context
import app.obdgolf.core.Drive
import app.obdgolf.core.DriveSample
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves each drive as a small JSON file in the app's private storage. */
class DriveStore(context: Context) {
    private val dir = File(context.filesDir, "drives").apply { mkdirs() }

    fun save(d: Drive) {
        val samples = JSONArray()
        d.samples.forEach { s ->
            samples.put(JSONArray().put(s.tSec).put(s.speedKmh).put(s.rpm).put(s.coolantC ?: JSONObject.NULL))
        }
        val o = JSONObject()
            .put("start", d.startMs).put("end", d.endMs)
            .put("km", d.distanceKm).put("maxKmh", d.maxSpeedKmh).put("avgKmh", d.avgMovingSpeedKmh)
            .put("maxRpm", d.maxRpm).put("maxCoolant", d.maxCoolantC ?: JSONObject.NULL)
            .put("minV", d.minBatteryV ?: JSONObject.NULL).put("idle", d.idleSec)
            .put("demo", d.demo).put("samples", samples)
        File(dir, "${d.startMs}.json").writeText(o.toString())
    }

    fun all(): List<Drive> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { parse(JSONObject(it.readText())) }.getOrNull() }
        .sortedByDescending { it.startMs }

    fun delete(startMs: Long) {
        File(dir, "$startMs.json").delete()
    }

    private fun parse(o: JSONObject): Drive {
        val arr = o.getJSONArray("samples")
        val samples = List(arr.length()) { i ->
            val s = arr.getJSONArray(i)
            DriveSample(s.getInt(0), s.getInt(1), s.getInt(2), if (s.isNull(3)) null else s.getInt(3))
        }
        fun optD(k: String) = if (o.isNull(k)) null else o.getDouble(k)
        return Drive(
            startMs = o.getLong("start"), endMs = o.getLong("end"),
            distanceKm = o.getDouble("km"), maxSpeedKmh = o.getDouble("maxKmh"),
            avgMovingSpeedKmh = o.getDouble("avgKmh"), maxRpm = o.getDouble("maxRpm"),
            maxCoolantC = optD("maxCoolant"), minBatteryV = optD("minV"),
            idleSec = o.getInt("idle"), demo = o.getBoolean("demo"), samples = samples,
        )
    }
}
