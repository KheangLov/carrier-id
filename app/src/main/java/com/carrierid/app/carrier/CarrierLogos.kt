package com.carrierid.app.carrier

import android.annotation.SuppressLint
import android.content.Context

object CarrierLogos {
    /**
     * Returns the drawable id for the carrier's logo if you have added one
     * (res/drawable/logo_smart.png etc.), otherwise 0 → the app draws a
     * coloured letter badge instead.
     */
    @SuppressLint("DiscouragedApi")
    fun drawableId(context: Context, carrier: Carrier): Int =
        context.resources.getIdentifier(carrier.logoName, "drawable", context.packageName)
}
