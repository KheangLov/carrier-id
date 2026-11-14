package com.carrierid.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLogos
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.settings.Features
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.spam.Risk
import com.carrierid.app.spam.Verdict
import com.carrierid.app.ui.CallNoteActivity
import com.carrierid.app.ui.LiveTextActivity
import com.carrierid.app.ui.PhoneFormat

/**
 * The floating card shown over the call screen. It has three modes:
 *
 *   RINGING   ┌──────────────────────────────┐
 *             │ ● Carrier ID · Incoming call ✕ │
 *             │ ⚠ Suspected spam – reason     │   (only when the rules flag it)
 *             │ (logo)  Metfone               │
 *             │         088 234 5678          │
 *             │ [Can't talk…] [Driving…] [Block] │  quick replies
 *             └──────────────────────────────┘
 *   IN_CALL   timer + [Live text] [Add note]
 *   ENDED     duration + [Add note] [Message] [Block]   (auto-hides)
 *
 * Drag it up/down to move it. It disappears when the call ends, when ✕ is
 * tapped, or after [AUTO_HIDE_MS] as a safety net.
 */
object CallerOverlay {

    enum class Mode { RINGING, IN_CALL, ENDED }

    private const val AUTO_HIDE_MS = 90_000L
    private const val AFTER_CALL_HIDE_MS = 15_000L
    private const val PREFS = "overlay"
    private const val KEY_Y = "y"

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var card: LinearLayout? = null
    private var shownKey: String? = null
    private var dismissedKey: String? = null   // user tapped ✕ during this call
    private var wm: WindowManager? = null
    private var appContext: Context? = null
    private var callStateCleanup: (() -> Unit)? = null
    private val autoHide = Runnable { hide() }

    // What the card is showing
    private var mode = Mode.RINGING
    private var number = ""
    private var carrier: Carrier? = null
    private var verdict: Verdict? = null
    private var preview = false
    private var ringAt = 0L
    private var answeredAt = 0L
    private var endedSeconds = 0L
    private var timerView: TextView? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (mode != Mode.IN_CALL || view == null) return
            timerView?.text = "On call  ·  ${duration((System.currentTimeMillis() - answeredAt) / 1000)}"
            main.postDelayed(this, 1000)
        }
    }

    fun show(
        context: Context,
        number: String,
        carrier: Carrier?,
        verdict: Verdict? = null,
        preview: Boolean = false,
    ) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return

        val key = CarrierLookup.normalize(number) ?: number.filter { it.isDigit() }
        main.post {
            // Already showing this caller (both detection paths fired) → maybe add the warning
            if (view != null && shownKey == key && mode != Mode.ENDED) {
                if (verdict != null && this.verdict?.risk != verdict.risk) {
                    this.verdict = verdict
                    render()
                }
                return@post
            }
            if (!preview && key == dismissedKey) return@post
            hide()
            this.mode = Mode.RINGING
            this.number = number
            this.carrier = carrier
            this.verdict = verdict
            this.preview = preview
            this.ringAt = CallSession.current(app)?.second ?: System.currentTimeMillis()
            if (!attach(app, key)) return@post

            // Start the keep-alive service AFTER the overlay is visible: on
            // Android 12+ a visible overlay is what allows a background FGS start.
            CallWatcherService.start(app)
            if (!preview) listenForCallState(app)
            main.postDelayed(autoHide, AUTO_HIDE_MS)
        }
    }

    /** The call was answered: switch to the in-call buttons. */
    fun setInCall() {
        main.post {
            if (view == null || mode != Mode.RINGING || preview) return@post
            mode = Mode.IN_CALL
            answeredAt = System.currentTimeMillis()
            main.removeCallbacks(autoHide)   // calls can be long; the call-end listener hides it
            render()
            main.post(ticker)
        }
    }

    /** Short card after an answered call: add a note, text back or block. */
    fun showAfterCall(context: Context, number: String, ringTime: Long, seconds: Long) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        main.post {
            hide()
            mode = Mode.ENDED
            this.number = number
            this.carrier = CarrierLookup.lookup(number)
            this.verdict = null
            this.preview = false
            this.ringAt = ringTime
            this.endedSeconds = seconds
            val key = CarrierLookup.normalize(number) ?: number.filter { it.isDigit() }
            if (!attach(app, key)) return@post
            main.postDelayed(autoHide, AFTER_CALL_HIDE_MS)
        }
    }

    /** Preview any mode from Settings, without a real call. */
    fun preview(context: Context, number: String, mode: Mode) {
        when (mode) {
            Mode.RINGING -> show(context, number, CarrierLookup.lookup(number), preview = true)
            Mode.IN_CALL -> {
                show(context, number, CarrierLookup.lookup(number), preview = true)
                main.post {
                    this.mode = Mode.IN_CALL
                    answeredAt = System.currentTimeMillis()
                    render(); main.post(ticker)
                }
            }
            Mode.ENDED -> showAfterCall(context, number, System.currentTimeMillis() - 200_000, 185)
        }
    }

    /** Called when the phone goes back to idle. */
    fun onCallEnded() {
        dismissedKey = null
        if (mode == Mode.ENDED && view != null) return   // the after-call card stays until it times out
        hide()
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        main.removeCallbacks(ticker)
        callStateCleanup?.invoke()
        callStateCleanup = null
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        card = null
        timerView = null
        shownKey = null
        appContext?.let { CallWatcherService.stop(it) }
    }

    // ---------- window

    private fun attach(app: Context, key: String): Boolean {
        appContext = app
        val windowManager = app.getSystemService(WindowManager::class.java)
        val outer = LinearLayout(app).apply { setPadding(dp(app, 12), dp(app, 10), dp(app, 12), dp(app, 14)) }
        val c = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(app, 20), dp(app, 14), dp(app, 14), dp(app, 16))
            elevation = dp(app, 10).toFloat()
        }
        outer.addView(c, LinearLayout.LayoutParams(-1, -2))
        card = c
        render()

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
        enableVerticalDrag(app, outer, params, windowManager)
        return try {
            windowManager.addView(outer, params)
            view = outer
            shownKey = key
            wm = windowManager
            true
        } catch (e: Exception) {
            card = null
            false
        }
    }

    // ---------- call state

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun listenForCallState(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        val onState: (Int) -> Unit = { state ->
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> main.post { onCallEnded() }
                TelephonyManager.CALL_STATE_OFFHOOK -> setInCall()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onState(state)
                }
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
                callStateCleanup = { tm.unregisterTelephonyCallback(cb) }
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
                }
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                callStateCleanup = { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: SecurityException) { }
    }

    // ---------- actions

    private fun quickReply(ctx: Context, text: String) {
        if (preview) {
            toast(ctx, "Preview: would text “$text” and decline")
            return
        }
        if (!SmsSender.canSend(ctx)) {
            toast(ctx, "Allow SMS in Carrier ID → Settings → Messages")
            return
        }
        val sent = SmsSender.send(ctx, number, text)
        if (!sent) { toast(ctx, "Couldn't send the message"); return }
        CallSession.markReplied(ctx)
        AutoReply.logQuickReply(ctx, number, text)
        val declined = Features.declineOnQuickReply.get(ctx) && CallControl.decline(ctx)
        toast(ctx, if (declined) "Message sent, call declined" else "Message sent")
    }

    private fun block(ctx: Context) {
        if (preview) { toast(ctx, "Preview: would block this number"); return }
        BlockStore.block(ctx, number)
        if (mode == Mode.RINGING) CallControl.decline(ctx)
        toast(ctx, "${PhoneFormat.display(number)} blocked")
        hide()
    }

    private fun openNote(ctx: Context) {
        ctx.startActivity(
            CallNoteActivity.intent(ctx, number, ringAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        if (mode == Mode.ENDED) hide()
    }

    private fun openLiveText(ctx: Context) {
        ctx.startActivity(
            LiveTextActivity.intent(ctx, if (preview) null else number, ringAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openMessage(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        hide()
    }

    private fun toast(ctx: Context, msg: String) =
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    // ---------- UI

    private class Palette(ctx: Context) {
        val dark = (ctx.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val card = if (dark) Color.parseColor("#1E1E1E") else Color.WHITE
        val text = if (dark) Color.parseColor("#F5F5F5") else Color.parseColor("#111111")
        val secondary = if (dark) Color.parseColor("#9A9A9A") else Color.parseColor("#7D7D7D")
        val chip = if (dark) Color.parseColor("#2E2E2E") else Color.parseColor("#EFEFEF")
        val accent = if (dark) Color.parseColor("#6A9BFF") else Color.parseColor("#3E7BFA")
        val danger = Color.parseColor("#E5484D")
        val dangerBg = if (dark) Color.parseColor("#3A1D1F") else Color.parseColor("#FDECEC")
    }

    /** (Re)builds the card's contents for the current mode. */
    private fun render() {
        val c = card ?: return
        val ctx = c.context
        val p = Palette(ctx)
        val v = verdict
        val flagged = v != null && v.risk != Risk.NONE && Features.warnSpam.get(ctx)
        c.removeAllViews()
        timerView = null
        c.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 26).toFloat()
            setColor(p.card)
            if (flagged) setStroke(dp(ctx, 2), p.danger)
        }

        // Header: "● Carrier ID · Incoming call"                       ✕
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (flagged) p.danger else carrier?.colorInt ?: p.secondary)
            }
        }, LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)))
        val title = TextView(ctx).apply {
            text = when (mode) {
                Mode.RINGING -> if (preview) "Carrier ID  ·  Preview" else "Carrier ID  ·  Incoming call"
                Mode.IN_CALL -> "On call  ·  0:00"
                Mode.ENDED -> "Call ended  ·  ${duration(endedSeconds)}"
            }
            setTextColor(p.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(ctx, 8), 0, 0, 0)
        }
        if (mode == Mode.IN_CALL) timerView = title
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(ctx).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(p.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(p.chip) }
            setOnClickListener { dismissedKey = shownKey; hide() }
        }, LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)))
        c.addView(header)

        // Spam warning strip
        if (flagged && v != null) {
            c.addView(TextView(ctx).apply {
                text = (if (v.risk == Risk.SPAM) "⚠  Likely spam" else "⚠  Be careful") +
                    (v.reason?.let { "  ·  $it" } ?: "")
                setTextColor(p.danger)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 12).toFloat(); setColor(p.dangerBg)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 10); marginEnd = dp(ctx, 6) })
        }

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
            setTextColor(p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            // Medium weight of the phone's own font (follows Samsung Font style)
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        })
        texts.addView(TextView(ctx).apply {
            text = PhoneFormat.display(number)
            setTextColor(p.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        body.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(body)

        // Your last note about this caller, so you remember who it is
        if (mode != Mode.ENDED) {
            CallNotes.forNumber(ctx, number).firstOrNull()?.preview?.takeIf { it.isNotBlank() }?.let { note ->
                c.addView(TextView(ctx).apply {
                    text = "📝  $note"
                    setTextColor(p.secondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(ctx, 10), dp(ctx, 6), 0)
                })
            }
        }

        // Actions
        val actions = mutableListOf<Action>()
        when (mode) {
            Mode.RINGING -> {
                if (Features.quickReplies.get(ctx)) {
                    Features.quickTexts(ctx).filter { it.isNotBlank() }.forEach { t ->
                        actions += Action(t, STYLE_NORMAL) { quickReply(ctx, t) }
                    }
                }
                actions += Action("Block", STYLE_DANGER) { block(ctx) }
            }
            Mode.IN_CALL -> {
                if (Features.liveText.get(ctx)) actions += Action("🎙  Live text", STYLE_ACCENT) { openLiveText(ctx) }
                actions += Action("📝  Add note", STYLE_NORMAL) { openNote(ctx) }
            }
            Mode.ENDED -> {
                actions += Action("📝  Add note", STYLE_ACCENT) { openNote(ctx) }
                actions += Action("Message", STYLE_NORMAL) { openMessage(ctx) }
                if (!BlockStore.isBlocked(ctx, number)) actions += Action("Block", STYLE_DANGER) { block(ctx) }
            }
        }
        if (actions.isNotEmpty()) {
            if (mode == Mode.RINGING && Features.quickReplies.get(ctx)) {
                c.addView(TextView(ctx).apply {
                    text = "Decline with a message"
                    setTextColor(p.secondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(ctx, 14), 0, 0)
                })
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(ctx, 10), dp(ctx, 6), 0)
            }
            actions.forEach { a ->
                row.addView(chip(ctx, p, a.label, a.style, a.run), LinearLayout.LayoutParams(-2, dp(ctx, 38)).apply {
                    marginEnd = dp(ctx, 8)
                })
            }
            c.addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private class Action(val label: String, val style: Int, val run: () -> Unit)

    private const val STYLE_NORMAL = 0
    private const val STYLE_ACCENT = 1
    private const val STYLE_DANGER = 2

    private fun chip(ctx: Context, p: Palette, label: String, style: Int, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
            setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
            setTextColor(
                when (style) {
                    STYLE_ACCENT -> Color.WHITE
                    STYLE_DANGER -> p.danger
                    else -> p.text
                }
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 19).toFloat()
                setColor(
                    when (style) {
                        STYLE_ACCENT -> p.accent
                        STYLE_DANGER -> p.dangerBg
                        else -> p.chip
                    }
                )
            }
            setOnClickListener { onClick() }
        }

    /** Real logo filling the circle if one is set, else a coloured letter circle. */
    fun badgeView(ctx: Context, carrier: Carrier?, size: Int): View {
        val logo = carrier?.let { CarrierLogos.bitmap(ctx, it) }
        val view: View = if (logo != null) {
            // Pre-shaped square logo, cropped to a circle edge to edge (no white ring)
            ImageView(ctx).apply {
                setImageBitmap(logo)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
        } else {
            TextView(ctx).apply {
                text = carrier?.initial ?: "?"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
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

    private fun duration(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
        ).toInt()
}
