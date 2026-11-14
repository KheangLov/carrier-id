package com.carrierid.app.notes

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.carrierid.app.spam.SpamRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * A note about one call: typed text and/or the live-text transcript.
 * [time] is when the call started ringing, which matches the call log's date.
 */
data class CallNote(
    val id: Long,
    val number: String,
    val time: Long,
    val text: String,
    val transcript: String,
) {
    val key: String? get() = SpamRules.key(number)
    val preview: String get() = text.ifBlank { transcript }.replace('\n', ' ').trim()
}

/** Notes saved in app storage (call_notes.json). Never leaves the phone. */
object CallNotes {
    private const val FILE = "call_notes.json"
    /** Calls in the log and notes are matched if they are this close in time. */
    private const val MATCH_MS = 3 * 60 * 1000L

    val version = mutableIntStateOf(0)
    private var cache: MutableList<CallNote>? = null

    @Synchronized
    fun all(ctx: Context): List<CallNote> = load(ctx).sortedByDescending { it.time }

    fun forNumber(ctx: Context, raw: String?): List<CallNote> {
        val k = SpamRules.key(raw) ?: return emptyList()
        return all(ctx).filter { it.key == k }
    }

    fun forCall(ctx: Context, raw: String?, time: Long): CallNote? =
        forNumber(ctx, raw).filter { abs(it.time - time) <= MATCH_MS }.minByOrNull { abs(it.time - time) }

    @Synchronized
    fun save(ctx: Context, note: CallNote) {
        val list = load(ctx)
        list.removeAll { it.id == note.id }
        if (note.text.isNotBlank() || note.transcript.isNotBlank()) list += note
        write(ctx, list)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        write(ctx, list)
    }

    /** Adds live-text lines to the note for this call, creating it if needed. */
    @Synchronized
    fun appendTranscript(ctx: Context, number: String, time: Long, lines: String) {
        if (lines.isBlank()) return
        val existing = forCall(ctx, number, time)
        val note = existing?.copy(
            transcript = listOf(existing.transcript, lines).filter { it.isNotBlank() }.joinToString("\n")
        ) ?: CallNote(System.currentTimeMillis(), number, time, "", lines)
        save(ctx, note)
    }

    private fun load(ctx: Context): MutableList<CallNote> {
        cache?.let { return it }
        val f = File(ctx.applicationContext.filesDir, FILE)
        val list = mutableListOf<CallNote>()
        if (f.exists()) runCatching {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list += CallNote(
                    o.getLong("id"), o.optString("number"), o.optLong("time"),
                    o.optString("text"), o.optString("transcript"),
                )
            }
        }
        cache = list
        return list
    }

    private fun write(ctx: Context, list: MutableList<CallNote>) {
        cache = list
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("number", it.number).put("time", it.time)
                    .put("text", it.text).put("transcript", it.transcript)
            )
        }
        val f = File(ctx.applicationContext.filesDir, FILE)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(f)
        version.intValue++
    }
}
