package com.carrierid.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.carrierid.app.carrier.CarrierLookup

/**
 * Backup path for showing the call card.
 *
 * The call-screening service is the main path, but on some phones/One UI
 * versions Android skips it (e.g. for some saved contacts). The system's
 * PHONE_STATE broadcast carries the caller's number when the app has the
 * call-log permission, so we use it as a second source. CallerOverlay ignores
 * the duplicate when both fire for the same call.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android sends this broadcast twice; only one copy has the number.
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!number.isNullOrBlank()) {
                    CallerOverlay.show(context, number, CarrierLookup.lookup(number))
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> CallerOverlay.onCallEnded()
        }
    }
}
