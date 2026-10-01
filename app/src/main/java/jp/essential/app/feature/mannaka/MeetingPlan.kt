package jp.essential.app.feature.mannaka

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class MeetingParticipant(
    val id: Int,
    val name: String = "",
    val walkMinutes: String = "",
    val input: String = "",
    val candidates: List<MeetingPoint> = emptyList(),
    val selected: MeetingPoint? = null,
)

internal val meetingCategories = listOf("カラオケ", "公園", "ごはん")

internal fun meetingTimeLabel(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

internal fun shiftMeetingTime(minutes: Int, delta: Int): Int = Math.floorMod(minutes + delta, 24 * 60)

internal fun encodeParticipants(participants: List<MeetingParticipant>): String = JSONArray().apply {
    participants.forEach { participant ->
        put(JSONObject().apply {
            put("id", participant.id); put("name", participant.name); put("walk", participant.walkMinutes); put("input", participant.input)
            participant.selected?.let { point ->
                put("station", JSONObject().apply {
                    put("name", point.name); put("lat", point.latitude); put("lon", point.longitude); put("id", point.id); put("detail", point.detail)
                })
            }
        })
    }
}.toString()

internal fun decodeParticipants(value: String): List<MeetingParticipant> = runCatching {
    val array = JSONArray(value)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val station = item.optJSONObject("station")
        MeetingParticipant(item.getInt("id"), item.optString("name"), item.optString("walk"), item.optString("input"),
            selected = station?.let { MeetingPoint(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.optString("id"), it.optString("detail")) })
    }.takeIf { it.size >= 2 } ?: listOf(MeetingParticipant(0), MeetingParticipant(1))
}.getOrElse { listOf(MeetingParticipant(0), MeetingParticipant(1)) }
