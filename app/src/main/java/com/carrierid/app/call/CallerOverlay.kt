package com.carrierid.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLogos

/**
 * The floating card shown over the incoming-call screen:
 *
 *   ┌──────────────────────────────┐
 *   │ (logo)  Metfone            ✕ │
 *   │         088 234 5678         │
 *   └──────────────────────────────┘
 *
 * Drag it up/down to move it. It disappears when the call ends, when ✕ is
 * tapped, or after [AUTO_HIDE_MS] as a safety net.
 */
object CallerOverlay {

    private const val AUTO_HIDE_MS = 90_000L
    private const val PREFS = "overlay"
    private const val KEY_Y = "y"

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var wm: WindowManager? = null
    private var appContext: Context? = null
    private var callStateCleanup: (() -> Unit)? = null
    private val autoHide = Runnable { hide() }

    fun show(context: Context, number: String, carrier: Carrier?) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return

        main.post {
            hide()
            appContext = app
            val windowManager = app.getSystemService(WindowManager::class.java)
            val card = buildCard(app, number, carrier)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = app.getSharedPreferences(PREFS, 0).getInt(KEY_Y, dp(app, 120))
            }
            enableVerticalDrag(app, card, params, windowManager)

            try {
                windowManager.addView(card, params)
            } catch (e: Exception) {
                return@post
            }
            view = card
            wm = windowManager

            // Start the keep-alive service AFTER the overlay is visible: on
            // Android 12+ a visible overlay is what allows a background FGS start.
            CallWatcherService.start(app)
            listenForCallEnd(app)
            main.postDelayed(autoHide, AUTO_HIDE_MS)
        }
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        callStateCleanup?.invoke()
        callStateCleanup = null
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        appContext?.let { CallWatcherService.stop(it) }
    }

    // ---------- call state ----------

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun listenForCallEnd(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) main.post { hide() }
                    }
                }
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
                callStateCleanup = { tm.unregisterTelephonyCallback(cb) }
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) main.post { hide() }
                    }
                }
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                callStateCleanup = { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: SecurityException) { }
    }

    // ---------- UI ----------

    private fun buildCard(ctx: Context, number: String, carrier: Carrier?): View {
        val dark = (ctx.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardColor = if (dark) Color.parseColor("#1E1E1E") else Color.WHITE
        val textColor = if (dark) Color.parseColor("#F5F5F5") else Color.parseColor("#111111")
        val secondary = if (dark) Color.parseColor("#9A9A9A") else Color.parseColor("#7D7D7D")
        val accent = carrier?.colorInt ?: secondary

        val outer = LinearLayout(ctx).apply { setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 14)) }

        // One UI style: big rounded card, soft shadow, no border
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 14), dp(ctx, 18))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 26).toFloat()
                setColor(cardColor)
            }
            elevation = dp(ctx, 10).toFloat()
        }

        // Header: "Carrier ID · Incoming call"                       ✕
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }, LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)))
        header.addView(TextView(ctx).apply {
            text = "Carrier ID  ·  Incoming call"
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(ctx, 8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(ctx).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.parseColor("#2E2E2E") else Color.parseColor("#EFEFEF"))
            }
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)))
        card.addView(header)

        // Body: logo + network name + number
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 10), 0, 0)
        }
        body.addView(badgeView(ctx, carrier, dp(ctx, 52)))
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, 0, 0)
        }
        texts.addView(TextView(ctx).apply {
            text = carrier?.displayName ?: "Unknown network"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        texts.addView(TextView(ctx).apply {
            text = number
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        body.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(body)

        outer.addView(card, LinearLayout.LayoutParams(-1, -2))
        return outer
    }

    /** Real logo if you've added one to res/drawable, else a coloured letter circle. */
    fun badgeView(ctx: Context, carrier: Carrier?, size: Int): View {
        val logoId = carrier?.let { CarrierLogos.drawableId(ctx, it) } ?: 0
        val view: View = if (logoId != 0) {
            ImageView(ctx).apply {
                setImageResource(logoId)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        } else {
            TextView(ctx).apply {
                text = carrier?.initial ?: "?"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(carrier?.colorInt ?: Color.GRAY)
                }
            }
        }
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableVerticalDrag(
        ctx: Context, v: View, params: WindowManager.LayoutParams, windowManager: WindowManager
    ) {
        var startY = 0
        var touchY = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = params.y; touchY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.y = (startY + (e.rawY - touchY)).toInt().coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    ctx.getSharedPreferences(PREFS, 0).edit().putInt(KEY_Y, params.y).apply()
                    false
                }
                else -> false
            }
        }
    }

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
        ).toInt()
}
