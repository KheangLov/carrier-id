package com.carrierid.app.ui

import android.Manifest
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrierid.app.call.CallerOverlay
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLogos
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.notes.CallNotes
import com.carrierid.app.settings.Features
import com.carrierid.app.spam.BlockStore
import com.carrierid.app.ui.theme.One

private data class SetupItem(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val offText: String,
    val done: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun SettingsScreen(
    tick: Int,
    onRequestRole: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestPerms: () -> Unit,
    onOpenBattery: () -> Unit,
    onPickLogo: (Carrier) -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val c = One.colors
    val logoVersion by CarrierLogos.version
    val items = remember(tick) {
        listOf(
            SetupItem(AppIcons.Shield, Color(0xFF30B467), "Caller ID app",
                "Let Carrier ID see incoming numbers", ctx.holdsScreeningRole(), onRequestRole),
            SetupItem(AppIcons.Layers, Color(0xFF3E7BFA), "Show over call screen",
                "Needed to display the network card", Settings.canDrawOverlays(ctx), onRequestOverlay),
            SetupItem(AppIcons.PersonFilled, Color(0xFFF2994A), "Contacts, calls & phone",
                "Logos in contacts and call history", ctx.has(Manifest.permission.READ_CONTACTS) &&
                    ctx.has(Manifest.permission.READ_PHONE_STATE) &&
                    ctx.has(Manifest.permission.READ_CALL_LOG), onRequestPerms),
            SetupItem(AppIcons.Battery, Color(0xFF8E6CEF), "Keep running",
                "Stop the phone from putting it to sleep", ctx.ignoresBatteryOpt(), onOpenBattery),
        )
    }
    val doneCount = items.count { it.done }
    val overlayOk = items[1].done

    LargeTitleScreen(
        title = "Settings",
        subtitle = if (doneCount == items.size) "All set. You'll see the caller's network on every call."
        else "$doneCount of ${items.size} turned on",
    ) {
        // ---- Setup
        item { GroupHeader("Setup") }
        itemsIndexed(items) { i, it ->
            GroupCell(i, items.size, onClick = it.onClick, dividerStart = Dimens.textStart(34.dp)) {
                SettingContent(it.icon, it.color, it.title,
                    if (it.done) "On" else it.offText, it.done, it.onClick)
            }
        }

        // ---- Features
        item { GroupHeader("Features") }
        item {
            val v by Features.version
            val nv by CallNotes.version
            val blockedToday = remember(v) {
                BlockStore.blockedLog.all(ctx).count { android.text.format.DateUtils.isToday(it.time) }
            }
            val rows = listOf(
                Triple("spam", "Spam & blocking",
                    if (blockedToday > 0) "$blockedToday blocked today" else "Warn about and block unwanted calls"),
                Triple("messages", "Messages",
                    if (Features.autoReply.get(ctx)) "Auto-reply on  ·  quick replies on the call card"
                    else "Quick replies and auto-reply by SMS"),
                Triple("notes", "Notes & live text",
                    remember(nv) { CallNotes.all(ctx).size }.let { if (it > 0) "$it call notes" else "Call notes and live captions" }),
            )
            val looks = mapOf(
                "spam" to (AppIcons.Shield to Color(0xFFE5484D)),
                "messages" to (AppIcons.Message to Color(0xFF30B467)),
                "notes" to (AppIcons.Note to Color(0xFFF2994A)),
            )
            Column {
                rows.forEachIndexed { i, (id, title, sub) ->
                    val (icon, color) = looks.getValue(id)
                    GroupCell(i, rows.size, onClick = { onOpenPage(id) }, dividerStart = Dimens.textStart(34.dp)) {
                        LinkContent(icon, color, title, sub)
                    }
                }
            }
        }

        // ---- Number checker
        item { GroupHeader("Check a number") }
        item {
            var input by rememberSaveable { mutableStateOf("") }
            val carrier = CarrierLookup.lookup(input)
            OneCard {
                OneTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = "088 234 5678", icon = AppIcons.Phone,
                    background = c.field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                if (input.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarrierBadge(carrier, 44.dp)
                        Spacer(Modifier.width(Dimens.Gap))
                        Column {
                            Text(carrier?.displayName ?: "Unknown network", color = c.text,
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (carrier != null) "${PhoneFormat.display(input)}  ·  Cambodia mobile"
                                else "Not a Cambodian mobile number",
                                color = c.textSecondary, fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                OneButton("Preview call card", enabled = overlayOk) {
                    val n = input.ifBlank { "012 345 678" }
                    CallerOverlay.show(ctx, n, CarrierLookup.lookup(n), preview = true)
                }
            }
            if (!overlayOk) GroupFooter("Turn on \"Show over call screen\" to preview the card.")
        }

        // ---- Networks & logos
        item { GroupHeader("Networks & logos") }
        val carriers = Carrier.entries
        itemsIndexed(carriers) { i, carrier ->
            val custom = remember(logoVersion, carrier) { CarrierLogos.hasCustom(ctx, carrier) }
            GroupCell(i, carriers.size, onClick = { onPickLogo(carrier) },
                dividerStart = Dimens.textStart(40.dp)) {
                CarrierBadge(carrier, 40.dp)
                Spacer(Modifier.width(Dimens.Gap))
                RowTexts(carrier.displayName, CarrierLookup.prefixesOf(carrier).joinToString("  "))
                Spacer(Modifier.width(8.dp))
                if (custom) {
                    Text(
                        "Remove", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { CarrierLogos.remove(ctx, carrier) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Image, null, tint = c.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Add logo", color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            GroupFooter(
                "Tap a network to choose its logo from your photos. The network is worked out " +
                    "from the number's prefix, so a number moved to another network still shows " +
                    "the original one."
            )
        }
    }
}
