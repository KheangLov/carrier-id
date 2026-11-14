package com.carrierid.app.spam

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.carrierid.app.R
import com.carrierid.app.settings.EventLog
import com.carrierid.app.settings.Features
import com.carrierid.app.ui.MainActivity
import com.carrierid.app.ui.PhoneFormat

/** The user's block list and spam reports, plus a log of blocked calls. */
object BlockStore {
    private const val FILE = "spam"
    private const val BLOCKED = "blocked"
    private const val REPORTED = "reported"

    val blockedLog = EventLog(FILE, "blocked_log", 200)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, 0)

    fun blocked(ctx: Context): Set<String> = prefs(ctx).getStringSet(BLOCKED, emptySet()).orEmpty()
    fun reported(ctx: Context): Set<String> = prefs(ctx).getStringSet(REPORTED, emptySet()).orEmpty()

    fun isBlocked(ctx: Context, raw: String?) = SpamRules.matches(blocked(ctx), raw)
    fun isReported(ctx: Context, raw: String?) = SpamRules.matches(reported(ctx), raw)

    /** Adds a number or "prefix*" pattern. Returns false if it isn't a usable number. */
    fun block(ctx: Context, typed: String): Boolean {
        val e = SpamRules.entry(typed) ?: return false
        edit(ctx, BLOCKED) { it += e }
        return true
    }

    /** Removes every entry that matches [raw] (the exact number and any covering pattern). */
    fun unblock(ctx: Context, raw: String) {
        val k = SpamRules.key(raw)
        edit(ctx, BLOCKED) { set ->
            set.remove(raw)
            set.removeAll { e -> if (e.endsWith("*")) k != null && k.startsWith(e.dropLast(1)) else e == k }
        }
    }

    fun removeEntry(ctx: Context, entry: String) = edit(ctx, BLOCKED) { it.remove(entry) }

    fun report(ctx: Context, raw: String) {
        val k = SpamRules.key(raw) ?: return
        edit(ctx, REPORTED) { it += k }
    }

    fun unreport(ctx: Context, raw: String) {
        val k = SpamRules.key(raw) ?: raw
        edit(ctx, REPORTED) { it.remove(k) }
    }

    private fun edit(ctx: Context, key: String, change: (MutableSet<String>) -> Unit) {
        synchronized(this) {
            val set = HashSet(prefs(ctx).getStringSet(key, emptySet()).orEmpty())
            change(set)
            prefs(ctx).edit().putStringSet(key, set).apply()
        }
        Features.version.intValue++
    }

    // ---------- blocked-call notice

    fun notifyBlocked(ctx: Context, number: String?, reason: String?) {
        if (!Features.notifyBlocked.get(ctx)) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Blocked calls", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 7,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PAGE, "spam")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val who = PhoneFormat.display(number).ifBlank { "a hidden number" }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Blocked a call from $who")
            .setContentText(reason ?: "Spam protection")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_BASE + (who.hashCode() and 0xfff), n) }
    }

    private const val CHANNEL = "blocked_calls"
    private const val NOTIF_BASE = 5000
}

/** Collects the facts about a ringing call and runs [SpamRules]. Safe to call off the main thread. */
object SpamCheck {

    fun check(ctx: Context, number: String?, verificationFailed: Boolean = false): Verdict {
        val facts = CallFacts(
            number = number,
            inContacts = isContact(ctx, number),
            onBlocklist = BlockStore.isBlocked(ctx, number),
            reportedSpam = BlockStore.isReported(ctx, number),
            recentUnanswered = recentUnanswered(ctx, number),
            verificationFailed = verificationFailed,
        )
        return SpamRules.evaluate(facts, Features.spamSettings(ctx))
    }

    fun isContact(ctx: Context, number: String?): Boolean {
        if (number.isNullOrBlank() || !granted(ctx, Manifest.permission.READ_CONTACTS)) return false
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    /** Missed / rejected calls from this number in the last 24 h. */
    private fun recentUnanswered(ctx: Context, number: String?): Int {
        val key = SpamRules.key(number) ?: return 0
        if (!granted(ctx, Manifest.permission.READ_CALL_LOG)) return 0
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return runCatching {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} IN (?, ?)",
                arrayOf(
                    since.toString(), CallLog.Calls.MISSED_TYPE.toString(),
                    CallLog.Calls.REJECTED_TYPE.toString(),
                ),
                null,
            )?.use { c ->
                var n = 0
                while (c.moveToNext()) if (SpamRules.key(c.getString(0)) == key) n++
                n
            } ?: 0
        }.getOrDefault(0)
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}
