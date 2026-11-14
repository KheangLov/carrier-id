package com.carrierid.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.settings.EventLog
import com.carrierid.app.settings.Features
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.spam.SpamCheck
import com.carrierid.app.spam.SpamRules

/** Sends texts through the phone's default SIM. Android saves them in the Messages app. */
object SmsSender {
    fun canSend(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)

    fun send(ctx: Context, number: String, text: String): Boolean {
        if (!canSend(ctx) || number.isBlank() || text.isBlank()) return false
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sm.divideMessage(text)   // Khmer text needs several parts sooner
            if (parts.size > 1) sm.sendMultipartTextMessage(number, null, parts, null, null)
            else sm.sendTextMessage(number, null, text, null, null)
            true
        } catch (e: Exception) {
            Log.w("SmsSender", "Send failed", e)
            false
        }
    }
}

/** Ends the ringing call. Needs the "answer phone calls" permission. */
object CallControl {
    fun canDecline(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun decline(ctx: Context): Boolean {
        if (!canDecline(ctx)) return false
        return try {
            @Suppress("DEPRECATION")
            ctx.getSystemService(TelecomManager::class.java)?.endCall() ?: false
        } catch (e: Exception) {
            Log.w("CallControl", "endCall failed", e)
            false
        }
    }
}

/** Texts people back after a missed or declined call. */
object AutoReply {
    private const val MIN_GAP_MS = 30 * 60 * 1000L   // at most one auto-text per caller per 30 min

    val sentLog = EventLog("messages", "sent_log", 100)

    /** Runs off the main thread: looks up contacts before deciding. */
    fun onMissed(ctx: Context, number: String) {
        val app = ctx.applicationContext
        if (!Features.autoReply.get(app)) return
        Thread {
            val text = Features.autoReplyText.get(app).trim()
            if (text.isEmpty()) return@Thread
            // Only Cambodian mobiles: no surprise international SMS charges, no texting scammers abroad
            if (CarrierLookup.normalize(number) == null) return@Thread
            if (BlockStore.isBlocked(app, number) || BlockStore.isReported(app, number)) return@Thread
            val contact = SpamCheck.isContact(app, number)
            if (Features.autoReplyContactsOnly.get(app) && !contact) return@Thread
            if (!contact && SpamCheck.check(app, number).risk != com.carrierid.app.spam.Risk.NONE) return@Thread

            val key = SpamRules.key(number) ?: return@Thread
            val p = app.getSharedPreferences("messages", 0)
            val last = p.getLong("last_$key", 0L)
            if (System.currentTimeMillis() - last < MIN_GAP_MS) return@Thread

            if (SmsSender.send(app, number, text)) {
                p.edit().putLong("last_$key", System.currentTimeMillis()).apply()
                sentLog.add(app, number, "Auto-reply: $text")
            }
        }.start()
    }

    fun logQuickReply(ctx: Context, number: String, text: String) =
        sentLog.add(ctx, number, "Quick reply: $text")
}
