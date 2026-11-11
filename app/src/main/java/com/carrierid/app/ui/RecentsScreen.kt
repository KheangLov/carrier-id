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
    var filter by rememberSaveable { mutableStateOf<Carrier?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val all = calls
    val shown = remember(all, missedOnly, filter, query) {
        val digits = query.filter(Char::isDigit)
        all.orEmpty().filter {
            (!missedOnly || it.kind == CallKind.MISSED) &&
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
                OnePill("All", !missedOnly && filter == null, { missedOnly = false; filter = null })
                OnePill("Missed", missedOnly, { missedOnly = !missedOnly })
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
                    if (query.isNotBlank() || filter != null || missedOnly) "Try another search or filter."
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
                    onClick = {
                        if (call.number.isNotBlank()) {
                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.number}")))
                        }
                    },
                ) {
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
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(timeFormat.format(Date(call.date)), color = c.textSecondary, fontSize = 12.5.sp)
                }
            }
        }
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
