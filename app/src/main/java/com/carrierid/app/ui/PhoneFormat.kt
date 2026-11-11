package com.carrierid.app.ui

import android.telephony.PhoneNumberUtils
import com.carrierid.app.carrier.CarrierLookup

/** One place that decides how every phone number is shown in the app. */
object PhoneFormat {
    fun display(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // Cambodian numbers: 012 345 678 / 088 234 5678
        CarrierLookup.formatLocal(raw)?.let { return it }
        // Foreign numbers: Android's own formatter, e.g. +66 81 234 5678
        val trimmed = raw.trim()
        if (trimmed.startsWith("+") || trimmed.startsWith("00")) {
            val e164 = if (trimmed.startsWith("00")) "+" + trimmed.drop(2) else trimmed
            PhoneNumberUtils.formatNumber(e164.filter { it == '+' || it.isDigit() }, "KH")
                ?.let { return it }
        }
        // Short codes, service numbers, etc.: leave as they are
        return trimmed
    }
}
