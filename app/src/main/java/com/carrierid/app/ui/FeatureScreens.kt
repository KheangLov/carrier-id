package com.carrierid.app.ui

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import android.widget.Toast
import com.carrierid.app.call.AutoReply
import com.carrierid.app.call.CallControl
import com.carrierid.app.call.CallerOverlay
import com.carrierid.app.call.SmsSender
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.settings.Features
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.ui.theme.One
import java.text.DateFormat
import java.util.Date

private val Red = Color(0xFFE5484D)
private val Green = Color(0xFF30B467)
private val Blue = Color(0xFF3E7BFA)
private val Orange = Color(0xFFF2994A)
private val Purple = Color(0xFF8E6CEF)
private val Grey = Color(0xFF8A8A8E)

private data class Toggle(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val pref: Features.Bool,
)

/** A group of on/off rows bound to [Features] switches. */
private fun androidx.compose.foundation.lazy.LazyListScope.toggleGroup(
    ctx: Context, toggles: List<Toggle>,
) {
    itemsIndexed(toggles) { i, t ->
        // Reading the version here redraws the switch as soon as it changes
        val on = Features.version.intValue.let { t.pref.get(ctx) }
        GroupCell(i, toggles.size, onClick = { t.pref.set(ctx, !on) }, dividerStart = Dimens.textStart(34.dp)) {
            IconTile(t.icon, t.color)
            Spacer(Modifier.width(Dimens.Gap))
            RowTexts(t.title, t.subtitle)
            Spacer(Modifier.width(10.dp))
            OneSwitch(on) { t.pref.set(ctx, !on) }
        }
    }
}

private fun fmtTime(t: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

// ======================================================================= Spam

@Composable
internal fun SpamScreen(tick: Int, onBack: () -> Unit, onRequestRole: () -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val v by Features.version
    val roleOk = remember(tick) { ctx.holdsScreeningRole() }
    val blocked = remember(v) { BlockStore.blocked(ctx).sorted() }
    val reported = remember(v) { BlockStore.reported(ctx).sorted() }
    val log = remember(v) { BlockStore.blockedLog.all(ctx) }
    val today = log.count { android.text.format.DateUtils.isToday(it.time) }

    LargeTitleScreen(
        title = "Spam & blocking",
        subtitle = if (today > 0) "$today calls blocked today" else "Checked on your phone, nothing is uploaded",
        onBack = onBack,
    ) {
        if (!roleOk) item {
            Spacer(Modifier.height(4.dp))
            OneCard {
                Text("Blocking needs Carrier ID as your Caller ID & spam app.",
                    color = c.text, fontSize = 15.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                OneButton("Turn on", onClick = onRequestRole)
            }
        }

        item { GroupHeader("Warnings") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Warning, Orange, "Warn about suspected spam",
                "Red warning on the call card with the reason", Features.warnSpam),
        ))

        item { GroupHeader("Block automatically") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Shield, Red, "Spam & scam numbers",
                "Numbers you reported, faked caller IDs and scam-prone country codes", Features.blockSpam),
            Toggle(AppIcons.Blocked, Grey, "Hidden numbers", "Calls with no caller ID", Features.blockHidden),
            Toggle(AppIcons.Globe, Blue, "International numbers", "Any number outside Cambodia",
                Features.blockForeign),
            Toggle(AppIcons.Recents, Purple, "Repeat ring-and-hang-up",
                "Unknown numbers that called 2+ times today without an answer", Features.blockSuspicious),
            Toggle(AppIcons.Person, Orange, "Everyone not in contacts",
                "Strict mode: only saved contacts can ring", Features.blockUnknown),
        ))
        item { GroupFooter("Your contacts are never blocked unless you add them to the block list.") }

        item { GroupHeader("Notifications") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Bell, Green, "Tell me when a call is blocked",
                "Quiet notification, no sound", Features.notifyBlocked),
        ))

        // ---- Block list
        item { GroupHeader("Block list") }
        item {
            var input by rememberSaveable { mutableStateOf("") }
            OneCard {
                OneTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = "Number, or prefix like +234*", icon = AppIcons.Phone,
                    background = c.field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(14.dp))
                OneButton("Block", enabled = input.any(Char::isDigit)) {
                    if (BlockStore.block(ctx, input)) input = ""
                    else Toast.makeText(ctx, "That doesn't look like a number", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (blocked.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            itemsIndexed(blocked, key = { _, e -> "b_$e" }) { i, e ->
                GroupCell(i, blocked.size, dividerStart = Dimens.textStart(40.dp)) {
                    CarrierBadge(CarrierLookup.lookup(e.removeSuffix("*")), 40.dp)
                    Spacer(Modifier.width(Dimens.Gap))
                    RowTexts(
                        if (e.endsWith("*")) "Starts with ${e.dropLast(1)}" else PhoneFormat.display(e),
                        if (e.endsWith("*")) "Every number with this prefix" else null,
                    )
                    TextAction("Unblock") { BlockStore.removeEntry(ctx, e) }
                }
            }
        }
        item { GroupFooter("End with * to block a whole prefix: +234* blocks Nigeria, 023* blocks Phnom Penh landlines.") }

        // ---- Reported
        if (reported.isNotEmpty()) {
            item { GroupHeader("Reported as spam") }
            itemsIndexed(reported, key = { _, e -> "r_$e" }) { i, e ->
                GroupCell(i, reported.size, dividerStart = Dimens.textStart(40.dp)) {
                    CarrierBadge(CarrierLookup.lookup(e), 40.dp)
                    Spacer(Modifier.width(Dimens.Gap))
                    RowTexts(PhoneFormat.display(e), "Warned or blocked as spam")
                    TextAction("Not spam") { BlockStore.unreport(ctx, e) }
                }
            }
        }

        // ---- Log
        item { GroupHeader("Recently blocked") }
        if (log.isEmpty()) item {
            OneCard { Text("No calls blocked yet.", color = c.textSecondary, fontSize = 14.sp) }
        } else {
            val shown = log.take(30)
            itemsIndexed(shown) { i, ev ->
                GroupCell(i, shown.size, dividerStart = Dimens.textStart(40.dp)) {
                    CarrierBadge(CarrierLookup.lookup(ev.number), 40.dp)
                    Spacer(Modifier.width(Dimens.Gap))
                    RowTexts(PhoneFormat.display(ev.number).ifBlank { "Hidden number" },
                        "${ev.text}  ·  ${fmtTime(ev.time)}")
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    TextAction("Clear history") { BlockStore.blockedLog.clear(ctx) }
                }
            }
        }
    }
}

// =================================================================== Messages

@Composable
internal fun MessagesScreen(tick: Int, onBack: () -> Unit, onRequestPerms: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val v by Features.version
    val smsOk = remember(tick) { SmsSender.canSend(ctx) }
    val declineOk = remember(tick) { CallControl.canDecline(ctx) }
    val log = remember(v) { AutoReply.sentLog.all(ctx) }

    LargeTitleScreen(
        title = "Messages",
        subtitle = "Text callers back when you can't answer",
        onBack = onBack,
    ) {
        if (!smsOk || !declineOk) {
            item { GroupHeader("Permissions") }
            val rows = buildList {
                if (!smsOk) add(Triple(AppIcons.Message, "Send SMS", Manifest.permission.SEND_SMS))
                if (!declineOk) add(Triple(AppIcons.Phone, "Decline calls", Manifest.permission.ANSWER_PHONE_CALLS))
            }
            itemsIndexed(rows) { i, (icon, title, perm) ->
                GroupCell(i, rows.size, onClick = { onRequestPerms(arrayOf(perm)) },
                    dividerStart = Dimens.textStart(34.dp)) {
                    IconTile(icon, Green)
                    Spacer(Modifier.width(Dimens.Gap))
                    RowTexts(title, "Tap to allow")
                    Chevron()
                }
            }
        }

        // ---- Auto-reply
        item { GroupHeader("Auto-reply") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Message, Green, "Text back missed calls",
                "Also when you decline a call", Features.autoReply),
            Toggle(AppIcons.Person, Orange, "Only my contacts",
                "Unknown numbers won't get a reply", Features.autoReplyContactsOnly),
        ))
        item {
            var text by remember { mutableStateOf(Features.autoReplyText.get(ctx)) }
            Spacer(Modifier.height(12.dp))
            OneCard {
                Text("Message", color = c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OneTextArea(text, { text = it; Features.autoReplyText.set(ctx, it) },
                    "Sorry, I can't answer right now…", minLines = 3)
            }
            GroupFooter("Sent once per caller every 30 minutes, only to Cambodian mobile numbers, " +
                "never to blocked or spam numbers. Normal SMS rates apply.")
        }

        // ---- Quick replies
        item { GroupHeader("Quick replies on the call card") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Layers, Blue, "Show quick replies",
                "One tap texts the caller", Features.quickReplies),
            Toggle(AppIcons.Phone, Red, "Decline the call too",
                "Stops the ringing after the text is sent", Features.declineOnQuickReply),
        ))
        item {
            val initial = remember { Features.quickTexts(ctx) }
            val texts = remember { mutableStateListOf<String>().apply { addAll(initial); while (size < 3) add("") } }
            Spacer(Modifier.height(12.dp))
            OneCard {
                texts.forEachIndexed { i, t ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    OneTextField(
                        value = t,
                        onValueChange = { texts[i] = it; Features.setQuickTexts(ctx, texts.toList()) },
                        placeholder = "Reply ${i + 1}", icon = AppIcons.Message, background = c.field,
                    )
                }
            }
            GroupFooter("Leave a box empty to hide it.")
        }

        // ---- Log
        if (log.isNotEmpty()) {
            item { GroupHeader("Sent") }
            val shown = log.take(30)
            itemsIndexed(shown) { i, ev ->
                GroupCell(i, shown.size, dividerStart = Dimens.textStart(40.dp)) {
                    CarrierBadge(CarrierLookup.lookup(ev.number), 40.dp)
                    Spacer(Modifier.width(Dimens.Gap))
                    RowTexts(PhoneFormat.display(ev.number), "${ev.text}  ·  ${fmtTime(ev.time)}")
                }
            }
        }
    }
}

// ====================================================================== Notes

@Composable
internal fun NotesScreen(tick: Int, onBack: () -> Unit, onRequestPerms: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val v by Features.version
    val nv by CallNotes.version
    val notes = remember(nv, tick) { CallNotes.all(ctx) }
    val micOk = remember(tick) { ctx.has(Manifest.permission.RECORD_AUDIO) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val lang = remember(v) { Features.liveLang.get(ctx) }

    LargeTitleScreen(
        title = "Notes & live text",
        subtitle = if (notes.isEmpty()) "Remember what each call was about" else "${notes.size} call notes",
        onBack = onBack,
    ) {
        item { GroupHeader("During and after calls") }
        toggleGroup(ctx, listOf(
            Toggle(AppIcons.Note, Orange, "After-call card",
                "Add a note, text back or block when a call ends", Features.afterCallCard),
            Toggle(AppIcons.Mic, Blue, "Live text button",
                "Captions while you talk, saved into the call note", Features.liveText),
        ))

        item { GroupHeader("Live text") }
        item {
            OneCard {
                Text("Language", color = c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnePill("ខ្មែរ Khmer", lang == "km-KH", { Features.liveLang.set(ctx, "km-KH") })
                    OnePill("English", lang == "en-US", { Features.liveLang.set(ctx, "en-US") })
                }
                Spacer(Modifier.height(16.dp))
                if (!micOk) {
                    OneButton("Allow microphone") { onRequestPerms(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                    Spacer(Modifier.height(10.dp))
                }
                SecondaryButton("Try live text now") {
                    ctx.startActivity(LiveTextActivity.intent(ctx, null, System.currentTimeMillis()))
                }
            }
            GroupFooter(
                "Android doesn't let apps hear the call itself, so live text listens with the microphone. " +
                    "It picks up your voice, and the other person when the speaker is on. Some phones mute " +
                    "the mic for other apps during calls; the panel tells you if it hears nothing. " +
                    "Uses Google speech recognition (internet, or an offline language pack)."
            )
        }

        item { GroupHeader("Preview the call card") }
        item {
            OneCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        SecondaryButton("In call") {
                            if (overlayOk) CallerOverlay.preview(ctx, "088 234 5678", CallerOverlay.Mode.IN_CALL)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        SecondaryButton("After call") {
                            if (overlayOk) CallerOverlay.preview(ctx, "088 234 5678", CallerOverlay.Mode.ENDED)
                        }
                    }
                }
            }
            if (!overlayOk) GroupFooter("Turn on \"Show over call screen\" in Settings first.")
        }

        item { GroupHeader("All notes") }
        if (notes.isEmpty()) item {
            OneCard {
                Text("No notes yet. Tap a call in Recents, or use the card after a call.",
                    color = c.textSecondary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        } else itemsIndexed(notes, key = { _, n -> n.id }) { i, n ->
            GroupCell(
                i, notes.size, dividerStart = Dimens.textStart(40.dp),
                onClick = { ctx.startActivity(CallNoteActivity.intent(ctx, n.number, n.time)) },
            ) {
                CarrierBadge(CarrierLookup.lookup(n.number), 40.dp)
                Spacer(Modifier.width(Dimens.Gap))
                Column(Modifier.weight(1f)) {
                    Text(PhoneFormat.display(n.number), color = c.text, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(n.preview, color = c.text.copy(alpha = 0.8f), fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(fmtTime(n.time) + if (n.transcript.isNotBlank()) "  ·  live text" else "",
                        color = c.textSecondary, fontSize = 12.5.sp)
                }
            }
        }
    }
}

/** Small tappable text, e.g. "Unblock". */
@Composable
fun TextAction(text: String, color: Color = One.colors.accent, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Settings row that opens a sub-page. */
@Composable
internal fun RowScope.LinkContent(icon: ImageVector, color: Color, title: String, subtitle: String) {
    IconTile(icon, color)
    Spacer(Modifier.width(Dimens.Gap))
    RowTexts(title, subtitle)
    Spacer(Modifier.width(8.dp))
    Chevron()
}
