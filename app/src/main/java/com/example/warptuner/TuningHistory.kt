package com.example.warptuner

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class HistoryMetrics(val mbps: Double, val rttMs: Double, val loss: Double) {
    fun toJson() = JSONObject().put("mbps", mbps).put("rttMs", rttMs).put("loss", loss)

    companion object {
        fun fromJson(json: JSONObject) = HistoryMetrics(
            json.getDouble("mbps"), json.getDouble("rttMs"), json.getDouble("loss")
        )
    }
}

internal data class TuningHistoryEntry(
    val completedAt: Long,
    val mode: String,
    val baseline: HistoryMetrics?,
    val best: HistoryMetrics
) {
    fun toJson() = JSONObject().put("completedAt", completedAt).put("mode", mode)
        .put("baseline", baseline?.toJson() ?: JSONObject.NULL).put("best", best.toJson())

    companion object {
        fun fromJson(json: JSONObject) = TuningHistoryEntry(
            json.getLong("completedAt"),
            json.getString("mode"),
            json.optJSONObject("baseline")?.let { HistoryMetrics.fromJson(it) },
            HistoryMetrics.fromJson(json.getJSONObject("best"))
        )
    }
}

internal class TuningHistory(context: Context) {
    private val prefs = context.getSharedPreferences("tuning_history", Context.MODE_PRIVATE)

    fun read(): List<TuningHistoryEntry> {
        val array = try { JSONArray(prefs.getString("entries", "[]") ?: "[]") }
        catch (_: JSONException) { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            try { TuningHistoryEntry.fromJson(array.getJSONObject(index)) }
            catch (_: JSONException) { null }
        }.take(20)
    }

    fun append(entry: TuningHistoryEntry) = synchronized(writeLock) {
        val array = JSONArray()
        (listOf(entry) + read()).take(20).forEach { array.put(it.toJson()) }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    private companion object {
        val writeLock = Any()
    }
}
