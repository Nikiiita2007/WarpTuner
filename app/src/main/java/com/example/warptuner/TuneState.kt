package com.example.warptuner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class TuneUi(
    val running: Boolean = false,
    val status: String = "",
    val progress: Float = 0f,
    val rows: List<TuneResult> = emptyList(),
    val outcome: TuneOutcome? = null,
    val log: String = ""
)

object TuneState {
    private val _ui = MutableStateFlow(TuneUi())
    val ui: StateFlow<TuneUi> = _ui.asStateFlow()
    fun update(f: (TuneUi) -> TuneUi) { _ui.update { f(it) } }
}

fun TuneResult.toJson(): JSONObject = JSONObject()
    .put("name", name).put("jc", params.jc).put("jmin", params.jmin).put("jmax", params.jmax)
    .put("ok", ok).put("rtt", rttMs).put("loss", loss).put("mbps", mbps)
    .put("score", score).put("config", config).put("err", err).put("ep", params.endpoint)
    .put("sites", JSONObject(sites as Map<*, *>))

fun resultFromJson(j: JSONObject): TuneResult {
    val s = j.getJSONObject("sites")
    val sites = s.keys().asSequence().associateWith { s.getBoolean(it) }
    return TuneResult(
        j.getString("name"),
        Params(j.getInt("jc"), j.getInt("jmin"), j.getInt("jmax"), j.optString("ep")),
        j.getBoolean("ok"),
        j.getDouble("rtt"),
        j.getDouble("loss"),
        j.getDouble("mbps"),
        sites,
        j.getDouble("score"),
        j.getString("config"),
        j.optString("err")
    )
}


fun outcomeToJson(o: TuneOutcome): String {
    val j = JSONObject()
        .put("ranked", JSONArray(o.ranked.map { it.toJson() }))
        .put("raw", o.rawMbps).put("note", o.endpointNote)
    o.baseline?.let { j.put("baseline", it.toJson()) }
    return j.toString()
}

fun outcomeFromJson(s: String): TuneOutcome? = try {
    val j = JSONObject(s)
    val arr = j.getJSONArray("ranked")
    val ranked = (0 until arr.length()).map { resultFromJson(arr.getJSONObject(it)) }
    val base = if (j.has("baseline")) resultFromJson(j.getJSONObject("baseline")) else null
    TuneOutcome(ranked, base, j.optDouble("raw", 0.0), j.optString("note"))
} catch (_: Exception) {
    null
}