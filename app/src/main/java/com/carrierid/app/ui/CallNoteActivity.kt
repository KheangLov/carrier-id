package com.carrierid.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.notes.CallNote
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.ui.theme.One
import com.carrierid.app.ui.theme.OneUiTheme
import com.carrierid.app.ui.theme.SystemFont
import java.text.DateFormat
import java.util.Date

/** Bottom panel for writing a note about a call. Opens over the call screen or from Recents. */
class CallNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val time = intent.getLongExtra(EXTRA_TIME, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        val existing = CallNotes.forCall(this, number, time)

        setContent {
            OneUiTheme {
                val c = One.colors
                var text by remember { mutableStateOf(existing?.text.orEmpty()) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

                fun save() {
                    val note = (existing ?: CallNote(System.currentTimeMillis(), number, time, "", ""))
                        .copy(text = text.trim())
                    CallNotes.save(this@CallNoteActivity, note)
                    finish()
                }

                BottomPanel(onDismiss = ::save) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarrierBadge(CarrierLookup.lookup(number), 40.dp)
                        Spacer(Modifier.width(Dimens.Gap))
                        Column(Modifier.weight(1f)) {
                            Text("Call note", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                PhoneFormat.display(number).ifBlank { "Unknown number" } + "  ·  " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(time)),
                                color = c.textSecondary, fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OneTextArea(
                        text, { text = it }, "What was the call about?",
                        minLines = 4, modifier = Modifier.focusRequester(focus),
                    )
                    val transcript = existing?.transcript.orEmpty()
                    if (transcript.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Live text", color = c.textSecondary, fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            transcript, color = c.text, fontSize = 14.sp, lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(14.dp)).background(c.field)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (existing != null) {
                            Box(Modifier.weight(1f)) {
                                SecondaryButton("Delete", danger = true) {
                                    CallNotes.delete(this@CallNoteActivity, existing.id); finish()
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) { OneButton("Save") { save() } }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_TIME = "time"

        fun intent(ctx: Context, number: String, callTime: Long): Intent =
            Intent(ctx, CallNoteActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_TIME, callTime)
    }
}

/** Dimmed screen with a rounded card at the bottom that moves up with the keyboard. */
@Composable
fun BottomPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = One.colors
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(10.dp)
                .clip(RoundedCornerShape(28.dp)).background(c.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 20.dp),
            content = content,
        )
    }
}

/** Multi-line text box in the app's style. */
@Composable
fun OneTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 3,
    modifier: Modifier = Modifier,
) {
    val c = One.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = c.textSecondary, fontSize = 16.sp)
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = SystemFont),
            cursorBrush = SolidColor(c.accent),
            minLines = minLines,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** Outlined-looking secondary button. */
@Composable
fun SecondaryButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = One.colors
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(c.field).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) Color(0xFFE5484D) else c.text,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
