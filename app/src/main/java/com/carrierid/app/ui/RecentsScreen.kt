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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
                Spacer(Modifier.height(12.dp))
                OneCard {
                    Text("Allow access to your call history to see which network every call came from.",
                        color = c.text, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    OneButton("Allow", onClick = onRequestPerms)
                }
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
    val days = remember(shown) { shown.groupBy { dayLabel(it.date) } } // keeps newest-first order

    LargeTitleScreen(
        title = "Recents",
        subtitle = if (all == null) null else {
            val missed = all.count { it.kind == CallKind.MISSED && isToday(it.date) }
            if (missed > 0) "$missed missed today" else "${all.size} calls"
        },
    ) {
        item {
            OneTextField(
                value = query, onValueChange = { query = it },
                placeholder = "Search", icon = Icons.Filled.Search,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (all != null) item {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnePill("All", !missedOnly && filter == null, { missedOnly = false; filter = null })
                OnePill("Missed", missedOnly, { missedOnly = !missedOnly })
                Carrier.entries.forEach { carrier ->
                    if (all.any { it.carrier == carrier }) {
                        OnePill(carrier.displayName, filter == carrier,
                            { filter = if (filter == carrier) null else carrier },
                            leading = { CarrierBadge(carrier, 18.dp) })
                    }
                }
            }
        }

        if (all == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            }
        } else if (shown.isEmpty()) {
            item {
                Text("No calls", color = c.textSecondary, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(40.dp))
            }
        }

        days.forEach { (day, list) ->
            item(key = "d_$day") { GroupHeader(day) }
            itemsIndexed(list, key = { _, it -> it.id }) { i, call ->
                GroupCell(i, list.size, onClick = {
                    if (call.number.isNotBlank()) {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.number}")))
                    }
                }) {
                    ContactAvatar(call.name ?: "", call.carrier)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            call.name ?: call.number.ifBlank { "Private number" },
                            color = if (call.kind == CallKind.MISSED) MissedRed else c.text,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                kindArrow(call.kind),
                                color = if (call.kind == CallKind.MISSED || call.kind == CallKind.REJECTED)
                                    MissedRed else c.textSecondary,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                call.carrier?.displayName ?: "Unknown network",
                                color = call.carrier?.let { Color(it.colorInt) } ?: c.textSecondary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            if (call.name != null) {
                                Text("  ·  ${call.number}", color = c.textSecondary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(timeOf(ctx, call.date), color = c.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun kindArrow(kind: CallKind) = when (kind) {
    CallKind.INCOMING -> "↙"
    CallKind.OUTGOING -> "↗"
    CallKind.MISSED -> "↙"
    CallKind.REJECTED -> "⊘"
    CallKind.BLOCKED -> "⊘"
    CallKind.OTHER -> "•"
}

private fun isToday(t: Long) = DateUtils.isToday(t)

private fun dayLabel(t: Long): String {
    if (DateUtils.isToday(t)) return "Today"
    if (DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS)) return "Yesterday"
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val year = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
    val pattern = if (year == thisYear) "EEEE, d MMMM" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))
}

private fun timeOf(ctx: android.content.Context, t: Long): String =
    android.text.format.DateFormat.getTimeFormat(ctx).format(Date(t))
