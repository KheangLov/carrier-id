package com.carrierid.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.ui.theme.One
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ContactsScreen(tick: Int, onRequestPerms: () -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val granted = remember(tick) { ctx.has(Manifest.permission.READ_CONTACTS) }

    if (!granted) {
        LargeTitleScreen("Contacts", "Every contact with their mobile network") {
            item {
                EmptyState(
                    AppIcons.Person, "Allow contacts",
                    "Carrier ID needs your contacts to show a network logo next to every number.",
                    action = "Allow", onAction = onRequestPerms,
                )
            }
        }
        return
    }

    var contacts by remember { mutableStateOf<List<ContactNumber>?>(null) }
    LaunchedEffect(tick) {
        contacts = withContext(Dispatchers.IO) { ContactsRepository.load(ctx) }
    }
    var filter by rememberSaveable { mutableStateOf<Carrier?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val all = contacts
    val shown = remember(all, filter, query) {
        val digits = query.filter(Char::isDigit)
        all.orEmpty().filter {
            (filter == null || it.carrier == filter) &&
                (query.isBlank() || it.name.contains(query, true) ||
                    (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits)))
        }
    }
    val counts = remember(all) { all.orEmpty().groupingBy { it.carrier }.eachCount() }
    val groups = remember(shown) {
        shown.groupBy {
            val ch = it.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (ch.isLetter()) ch.toString() else "#"
        }.toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
    }

    LargeTitleScreen(
        title = "Contacts",
        subtitle = if (all == null) null else "${all.size} numbers",
    ) {
        item { SearchBar(query) { query = it } }
        if (all != null) item {
            PillRow {
                OnePill("All", filter == null, { filter = null })
                Carrier.entries.forEach { carrier ->
                    val n = counts[carrier] ?: 0
                    if (n > 0) OnePill("${carrier.displayName}  $n", filter == carrier,
                        { filter = if (filter == carrier) null else carrier },
                        leading = { CarrierBadge(carrier, 20.dp) })
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
                EmptyState(AppIcons.Search, "No contacts",
                    if (query.isNotBlank() || filter != null) "Try another search or filter."
                    else "Contacts you save will appear here.")
            }
        }

        groups.forEach { (letter, list) ->
            item(key = "h_$letter") { GroupHeader(letter) }
            itemsIndexed(list, key = { _, it -> "${it.contactId}:${it.number}" }) { i, item ->
                GroupCell(
                    i, list.size,
                    dividerStart = Dimens.textStart(AvatarSize),
                    onClick = {
                        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI,
                            item.contactId.toString())
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                ) {
                    ContactAvatar(item.name, item.carrier)
                    Spacer(Modifier.width(Dimens.Gap))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = c.text, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(PhoneFormat.display(item.number), color = c.textSecondary, fontSize = 13.sp)
                    }
                    item.carrier?.let { CarrierTag(it) }
                }
            }
        }
    }
}

/** Small coloured network label on the right of a row. */
@Composable
internal fun CarrierTag(carrier: Carrier) {
    val color = Color(carrier.colorInt)
    Text(
        carrier.displayName, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
