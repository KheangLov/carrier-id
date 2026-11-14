package com.carrierid.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.notes.CallNote
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.settings.Features
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.spam.SpamRules
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import com.carrierid.app.ui.theme.One
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MissedRed = Color(0xFFE5484D)

/** Call history, Samsung Phone "Recents" style, with the caller's network on every row. */
@Composable
fun RecentsScreen(tick: Int, onRequestPerms: () -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val granted = remember(tick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
    }

    if (!granted) {
        LargeTitleScreen("Recents", "Your calls with each caller's network") {
            item {
                EmptyState(
                    AppIcons.Recents, "Allow call history",
                    "Carrier ID needs your call history to show which network every call came from.",
                    action = "Allow", onAction = onRequestPerms,
                )
            }
        }
        return
    }

    var calls by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(tick) {
        calls = withContext(Dispatchers.IO) { CallLogRepository.load(ctx) }
    }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CallEntry?>(null) }
    val fv by Features.version
    val nv by CallNotes.version
    val blockList = remember(fv) { BlockStore.blocked(ctx) }
    val notes = remember(nv) { CallNotes.all(ctx) }
    var filter by rememberSaveable { mutableStateOf<Carrier?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val all = calls
    val shown = remember(all, missedOnly, blockedOnly, filter, query) {
        val digits = query.filter(Char::isDigit)
        all.orEmpty().filter {
            (!missedOnly || it.kind == CallKind.MISSED) &&
                (!blockedOnly || it.kind == CallKind.BLOCKED) &&
                (filter == null || it.carrier == filter) &&
                (query.isBlank() || (it.name?.contains(query, true) == true) ||
                    (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits)))
        }
    }
    val present = remember(all) { all.orEmpty().mapNotNullTo(HashSet()) { it.carrier } }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(ctx) }
    val days = remember(shown) { shown.groupBy { dayLabel(it.date) } } // keeps newest-first order

    LargeTitleScreen(
        title = "Recents",
        subtitle = if (all == null) null else {
            val missed = all.count { it.kind == CallKind.MISSED && isToday(it.date) }
            if (missed > 0) "$missed missed today" else "${all.size} calls"
        },
    ) {
        item { SearchBar(query) { query = it } }
        if (all != null) item {
            PillRow {
                OnePill("All", !missedOnly && !blockedOnly && filter == null,
                    { missedOnly = false; blockedOnly = false; filter = null })
                OnePill("Missed", missedOnly, { missedOnly = !missedOnly; blockedOnly = false })
                if (all.any { it.kind == CallKind.BLOCKED }) {
                    OnePill("Blocked", blockedOnly, { blockedOnly = !blockedOnly; missedOnly = false })
                }
                Carrier.entries.forEach { carrier ->
                    if (carrier in present) {
                        OnePill(carrier.displayName, filter == carrier,
                            { filter = if (filter == carrier) null else carrier },
                            leading = { CarrierBadge(carrier, 20.dp) })
                    }
                }
            }
        }

        when {
            all == null -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.5.dp)
                }
            }
            shown.isEmpty() -> item {
                EmptyState(AppIcons.Recents, "No calls",
                    if (query.isNotBlank() || filter != null || missedOnly || blockedOnly) "Try another search or filter."
                    else "Calls you make and receive will appear here.")
            }
        }

        days.forEach { (day, list) ->
            item(key = "d_$day") { GroupHeader(day) }
            itemsIndexed(list, key = { _, it -> it.id }) { i, call ->
                val missed = call.kind == CallKind.MISSED || call.kind == CallKind.REJECTED
                GroupCell(
                    i, list.size,
                    dividerStart = Dimens.textStart(AvatarSize),
                    onClick = { if (call.number.isNotBlank()) selected = call },
                ) {
                    val note = remember(notes, call.id) { noteFor(notes, call) }
                    val isBlocked = remember(blockList, call.number) {
                        SpamRules.matches(blockList, call.number)
                    }
                    ContactAvatar(call.name ?: "", call.carrier)
                    Spacer(Modifier.width(Dimens.Gap))
                    Column(Modifier.weight(1f)) {
                        Text(
                            call.name ?: PhoneFormat.display(call.number).ifBlank { "Private number" },
                            color = if (missed) MissedRed else c.text,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                kindIcon(call.kind), null,
                                tint = if (missed) MissedRed else c.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                call.carrier?.displayName ?: "Unknown network",
                                color = call.carrier?.let { Color(it.colorInt) } ?: c.textSecondary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            if (call.name != null) {
                                Text("  ·  ${PhoneFormat.display(call.number)}", color = c.textSecondary,
                                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isBlocked) {
                                Text("  ·  Blocked", color = MissedRed, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        if (note != null && note.preview.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Note, null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(note.preview, color = c.textSecondary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(timeFormat.format(Date(call.date)), color = c.textSecondary, fontSize = 12.5.sp)
                }
            }
        }
    }
    selected?.let { CallActionsDialog(it) { selected = null } }
}

private fun noteFor(notes: List<CallNote>, call: CallEntry): CallNote? {
    val k = SpamRules.key(call.number) ?: return null
    return notes.filter { it.key == k && abs(it.time - call.date) <= 3 * 60 * 1000L }
        .minByOrNull { abs(it.time - call.date) }
}

/** Actions for one call: call back, text, note, block, report spam. */
@Composable
private fun CallActionsDialog(call: CallEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val fv by Features.version
    val nv by CallNotes.version
    val blocked = remember(fv) { BlockStore.isBlocked(ctx, call.number) }
    val reported = remember(fv) { BlockStore.isReported(ctx, call.number) }
    val note = remember(nv) { CallNotes.forCall(ctx, call.number, call.date) }
    val inContacts = call.name != null

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card)
                .padding(vertical = 18.dp)
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(call.name ?: "", call.carrier)
                Spacer(Modifier.width(Dimens.Gap))
                Column(Modifier.weight(1f)) {
                    Text(call.name ?: PhoneFormat.display(call.number), color = c.text, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            call.carrier?.displayName ?: "Unknown network",
                            if (call.name != null) PhoneFormat.display(call.number) else null,
                        ).joinToString("  ·  "),
                        color = c.textSecondary, fontSize = 13.sp,
                    )
                }
            }
            if (note != null && note.preview.isNotBlank()) {
                Text(
                    note.preview, color = c.text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)).background(c.field).padding(12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            ActionRow(AppIcons.Phone, "Call") {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(call.number)}")))
                onDismiss()
            }
            ActionRow(AppIcons.Message, "Send message") {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(call.number)}")))
                }
                onDismiss()
            }
            ActionRow(AppIcons.Note, if (note != null) "Edit note" else "Add note") {
                ctx.startActivity(CallNoteActivity.intent(ctx, call.number, call.date))
                onDismiss()
            }
            if (blocked) {
                ActionRow(AppIcons.Blocked, "Unblock") { BlockStore.unblock(ctx, call.number) }
            } else {
                ActionRow(AppIcons.Blocked, "Block number", MissedRed) { BlockStore.block(ctx, call.number) }
            }
            if (!inContacts) {
                if (reported) {
                    ActionRow(AppIcons.Shield, "Not spam") { BlockStore.unreport(ctx, call.number) }
                } else {
                    ActionRow(AppIcons.Warning, "Report as spam", MissedRed) { BlockStore.report(ctx, call.number) }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, color: Color? = null, onClick: () -> Unit) {
    val c = One.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color ?: c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = color ?: c.text, fontSize = 16.sp)
    }
}

private fun kindIcon(kind: CallKind) = when (kind) {
    CallKind.INCOMING -> AppIcons.Incoming
    CallKind.OUTGOING -> AppIcons.Outgoing
    CallKind.MISSED -> AppIcons.Missed
    CallKind.REJECTED, CallKind.BLOCKED -> AppIcons.Blocked
    CallKind.OTHER -> AppIcons.Recents
}

private fun isToday(t: Long) = DateUtils.isToday(t)

private val dayFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
private val dayYearFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

private fun dayLabel(t: Long): String {
    if (DateUtils.isToday(t)) return "Today"
    if (DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS)) return "Yesterday"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val fmt = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) dayFormat else dayYearFormat
    return fmt.format(Date(t))
}
