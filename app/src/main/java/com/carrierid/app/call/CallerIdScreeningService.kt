package com.carrierid.app.call

import android.telecom.Call
import android.telecom.CallScreeningService
import com.carrierid.app.carrier.CarrierLookup

/**
 * Bound by Android for each new call once this app holds ROLE_CALL_SCREENING.
 * We never block anything: we let the call ring normally and just show the
 * carrier card on top of Samsung's call screen.
 *
 * Note: Android only passes calls from SAVED contacts to this service if the
 * app has been granted READ_CONTACTS — the setup screen asks for it.
 */
class CallerIdScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. Always allow the call immediately (must answer within ~5 s).
        respondToCall(callDetails, CallResponse.Builder().build())

        // 2. Only incoming calls get the overlay.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val number = callDetails.handle?.schemeSpecificPart ?: return
        val carrier = CarrierLookup.lookup(number)

        CallerOverlay.show(applicationContext, number, carrier)
    }
}
