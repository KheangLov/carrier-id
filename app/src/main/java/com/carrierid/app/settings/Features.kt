package com.carrierid.app.settings

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.carrierid.app.spam.SpamSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * All feature switches, stored in one SharedPreferences file.
 * Compose screens read [version] so they redraw when anything changes.
 */
object Features {
    private const val FILE = "features"
    val version = mutableIntStateOf(0)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, 0)

    class Bool(private val key: String, private val default: Boolean) {
        fun get(ctx: Context) = Features.prefs(ctx).getBoolean(key, default)
        fun set(ctx: Context, v: Boolean) {
            Features.prefs(ctx).edit().putBoolean(key, v).apply(); Features.version.intValue++
        }
    }

    class Str(private val key: String, private val default: String) {
        fun get(ctx: Context): String = Features.prefs(ctx).getString(key, null) ?: default
        fun set(ctx: Context, v: String) {
            Features.prefs(ctx).edit().putString(key, v).apply(); Features.version.intValue++
        }
    }

    // ---- Spam & blocking
    val warnSpam = Bool("warn_spam", true)
    val blockSpam = Bool("block_spam", true)
    val blockHidden = Bool("block_hidden", false)
    val blockForeign = Bool("block_foreign", false)
    val blockUnknown = Bool("block_unknown", false)
    val blockSuspicious = Bool("block_suspicious", false)
    val notifyBlocked = Bool("notify_blocked", true)

    fun spamSettings(ctx: Context) = SpamSettings(
        blockSpam = blockSpam.get(ctx),
        blockHidden = blockHidden.get(ctx),
        blockForeign = blockForeign.get(ctx),
        blockUnknown = blockUnknown.get(ctx),
        blockSuspicious = blockSuspicious.get(ctx),
    )

    // ---- Messages
    val autoReply = Bool("auto_reply", false)
    val autoReplyContactsOnly = Bool("auto_reply_contacts_only", true)
    val autoReplyText = Str(
        "auto_reply_text",
        "Sorry, I can't answer right now. I'll call you back soon.",
    )
    val quickReplies = Bool("quick_replies", true)
    val declineOnQuickReply = Bool("decline_on_quick_reply", true)

    private val defaultQuickTexts = listOf(
        "Can't talk now. I'll call you back.",
        "I'm driving. I'll call you later.",
        "In a meeting. Please text me.",
    )

    fun quickTexts(ctx: Context): List<String> {
        val raw = prefs(ctx).getString("quick_texts", null) ?: return defaultQuickTexts
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(defaultQuickTexts)
    }

    fun setQuickTexts(ctx: Context, texts: List<String>) {
        prefs(ctx).edit().putString("quick_texts", JSONArray(texts).toString()).apply()
        version.intValue++
    }

    // ---- Notes & live text
    val afterCallCard = Bool("after_call_card", true)
    val liveText = Bool("live_text", true)
    /** BCP-47 language for live text: "km-KH" or "en-US". */
    val liveLang = Str("live_lang", "km-KH")
}

/** Small newest-first event list (blocked calls, sent replies) kept in SharedPreferences. */
class EventLog(private val file: String, private val key: String, private val max: Int = 100) {
    data class Event(val number: String, val time: Long, val text: String)

    @Synchronized
    fun add(ctx: Context, number: String, text: String) {
        val list = all(ctx).toMutableList()
        list.add(0, Event(number, System.currentTimeMillis(), text))
        val a = JSONArray()
        list.take(max).forEach {
            a.put(JSONObject().put("n", it.number).put("t", it.time).put("x", it.text))
        }
        ctx.applicationContext.getSharedPreferences(file, 0).edit().putString(key, a.toString()).apply()
        Features.version.intValue++
    }

    fun all(ctx: Context): List<Event> {
        val raw = ctx.applicationContext.getSharedPreferences(file, 0).getString(key, null)
            ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Event(o.optString("n"), o.optLong("t"), o.optString("x"))
            }
        }.getOrDefault(emptyList())
    }

    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(file, 0).edit().remove(key).apply()
        Features.version.intValue++
    }
}
