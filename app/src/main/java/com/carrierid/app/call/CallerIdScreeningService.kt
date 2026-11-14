package com.carrierid.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.spam.SpamCheck
import com.carrierid.app.spam.Verdict

/**
 * Bound by Android for each new call once this app holds ROLE_CALL_SCREENING.
 *
 * Every incoming call is checked against the spam rules (all offline):
 *  - block  → rejected silently, logged, optional quiet notification
 *  - allow  → rings normally and the carrier card (with any spam warning)
 *             is shown on top of Samsung's call screen
 *
 * Note: Android only passes calls from SAVED contacts to this service if the
 * app has been granted READ_CONTACTS — the setup screen asks for it.
 */
class CallerIdScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        val spoofed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            callDetails.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val app = applicationContext

        // Contacts + call-log lookups take a few ms; keep them off the main thread.
        // Android allows ~5 s to respond.
        Thread {
            val verdict = try {
                SpamCheck.check(app, number, spoofed)
            } catch (e: Exception) {
                Log.w("Screening", "Spam check failed", e)
                Verdict.SAFE
            }

            if (verdict.block) {
                respondToCall(
                    callDetails,
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)   // no "missed call" for blocked spam
                        .build()
                )
                BlockStore.blockedLog.add(app, number.orEmpty(), verdict.reason ?: "Blocked")
                BlockStore.notifyBlocked(app, number, verdict.reason)
            } else {
                respondToCall(callDetails, CallResponse.Builder().build())
                if (!number.isNullOrBlank()) {
                    CallerOverlay.show(app, number, CarrierLookup.lookup(number), verdict)
                }
            }
        }.start()
    }
}
