package com.carrierid.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrierid.app.call.CallSession
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.settings.Features
import com.carrierid.app.ui.theme.One
import com.carrierid.app.ui.theme.OneUiTheme

/**
 * Best-effort live captions during a call, using the phone's speech
 * recognition service (Google) on the microphone.
 *
 * Android does not give apps the call audio itself. What works depends on the
 * phone: usually your own voice is captured, and the other person only if the
 * speaker is on. Some phones mute the mic for other apps during a call; the
 * panel then says so. Lines are saved into the call's note when you close it
 * or the call ends.
 */
class LiveTextActivity : ComponentActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var saved = false
    private var emptyRounds = 0

    private val lines = mutableStateListOf<String>()
    private var partial by mutableStateOf("")
    private var status by mutableStateOf("Starting…")
    private var level by mutableFloatStateOf(0f)
    private var lang by mutableStateOf("km-KH")

    private var number: String? = null
    private var callTime = 0L
    private val onCallEnded: () -> Unit = { saveAndFinish() }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) start() else status = "Allow the microphone to use live text."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        number = intent.getStringExtra(EXTRA_NUMBER)
        callTime = intent.getLongExtra(EXTRA_TIME, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        lang = Features.liveLang.get(this)
        CallSession.addEndListener(onCallEnded)

        setContent { OneUiTheme { Panel() } }

        if (has(Manifest.permission.RECORD_AUDIO)) start()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    @Composable
    private fun Panel() {
        val c = One.colors
        val listState = rememberLazyListState()
        LaunchedEffect(lines.size, partial) {
            val last = lines.size + (if (partial.isNotBlank()) 1 else 0) - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
        BottomPanel(onDismiss = ::saveAndFinish) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Mic, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(Dimens.Gap))
                Column(Modifier.weight(1f)) {
                    Text("Live text", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = c.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Mic level: if this never moves, the phone is muting the mic during the call
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.field)) {
                Box(
                    Modifier.fillMaxWidth(level.coerceIn(0.02f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(c.accent)
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(16.dp)).background(c.field).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lines.isEmpty() && partial.isBlank()) item {
                    Text(
                        "Words will appear here. Turn on the speaker to capture the other person too.",
                        color = c.textSecondary, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                }
                items(lines) { Text(it, color = c.text, fontSize = 16.sp, lineHeight = 22.sp) }
                if (partial.isNotBlank()) item {
                    Text(partial, color = c.textSecondary, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OnePill("ខ្មែរ", lang == "km-KH", { switchLang("km-KH") })
                OnePill("English", lang == "en-US", { switchLang("en-US") })
            }
            Spacer(Modifier.height(14.dp))
            OneButton(if (number != null && lines.isNotEmpty()) "Save to call note & close" else "Close") {
                saveAndFinish()
            }
        }
    }

    // ---------- recognition loop

    private fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "No speech recognition on this phone. Install or update the Google app."
            return
        }
        running = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
        listen()
    }

    private fun listen() {
        if (!running) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        runCatching { recognizer?.startListening(i) }
            .onFailure { status = "Couldn't start: ${it.message}"; restart(1500) }
    }

    private fun restart(delayMs: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listen() }, delayMs)
    }

    private fun switchLang(l: String) {
        if (l == lang) return
        lang = l
        Features.liveLang.set(this, l)
        recognizer?.cancel()
        partial = ""
        restart(200)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (emptyRounds < 3) status = if (lang == "km-KH") "Listening (Khmer)…" else "Listening (English)…"
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { level = 0f }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            partial = ""
            if (!text.isNullOrBlank()) {
                lines += text.trim()
                emptyRounds = 0
            }
            restart(100)
        }

        override fun onError(error: Int) {
            partial = ""
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    emptyRounds++
                    if (emptyRounds >= 3) status =
                        "Not hearing anything. Turn on the speaker. Some phones mute the mic for other apps during calls."
                    restart(250)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recognizer?.cancel(); restart(800) }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    status = "Allow the microphone for Carrier ID (and the Google app) to use live text."
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    status = "Needs internet, or download this language for offline speech in Google settings."
                    restart(2000)
                }
                else -> restart(1000)
            }
        }
    }

    // ---------- finish

    private fun saveAndFinish() {
        if (!saved) {
            saved = true
            val n = number
            if (n != null && lines.isNotEmpty()) {
                CallNotes.appendTranscript(this, n, callTime, lines.joinToString("\n"))
            }
        }
        finish()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        CallSession.removeEndListener(onCallEnded)
        recognizer?.destroy()
        recognizer = null
        if (!saved && isFinishing) {
            saved = true
            number?.let { if (lines.isNotEmpty()) CallNotes.appendTranscript(this, it, callTime, lines.joinToString("\n")) }
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_TIME = "time"

        /** [number] null = test mode from Settings (nothing is saved). */
        fun intent(ctx: Context, number: String?, callTime: Long): Intent =
            Intent(ctx, LiveTextActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_TIME, callTime)
    }
}
