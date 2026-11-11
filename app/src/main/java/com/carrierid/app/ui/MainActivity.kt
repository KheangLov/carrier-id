package com.carrierid.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.carrierid.app.carrier.CarrierLogos
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.carrierid.app.call.CallerOverlay
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.ui.theme.One
import com.carrierid.app.ui.theme.OneUiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so screens re-read permission state. */
    private var resumeTick by mutableIntStateOf(0)

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }
    private val permsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }

    /** Carrier whose logo the user is picking right now. */
    private var pickingLogoFor: Carrier? = null
    private val logoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val carrier = pickingLogoFor ?: return@registerForActivityResult
            pickingLogoFor = null
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { CarrierLogos.save(this@MainActivity, carrier, uri) }
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "${carrier.displayName} logo saved" else "Couldn't use that image",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    private fun pickLogo(carrier: Carrier) {
        pickingLogoFor = carrier
        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val startOnContacts = holdsScreeningRole() && has(Manifest.permission.READ_CONTACTS)
        setContent {
            OneUiTheme {
                var tab by rememberSaveable { mutableIntStateOf(if (startOnContacts) 0 else 2) }
                GlassScaffold(
                    selected = tab,
                    tabs = listOf(
                        TabItem("Recents", Icons.Outlined.Call, Icons.Filled.Call),
                        TabItem("Contacts", Icons.Outlined.Person, Icons.Filled.Person),
                        TabItem("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
                    ),
                    onSelect = { tab = it },
                ) {
                    Box(Modifier.fillMaxSize().background(One.colors.background)) {
                        when (tab) {
                            0 -> RecentsScreen(resumeTick, ::requestRuntimePerms)
                            1 -> ContactsScreen(resumeTick, ::requestRuntimePerms)
                            else -> SettingsScreen(
                                tick = resumeTick,
                                onRequestRole = ::requestScreeningRole,
                                onRequestOverlay = ::openOverlaySettings,
                                onRequestPerms = ::requestRuntimePerms,
                                onOpenBattery = ::openBatterySettings,
                                onPickLogo = ::pickLogo,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            // Already ours: open the system "Caller ID & spam app" chooser so it can be changed.
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } else if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestRuntimePerms() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        val allGranted = perms.all { has(it) }
        if (allGranted) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            permsLauncher.launch(perms.toTypedArray())
        }
    }

    private fun openBatterySettings() {
        val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            // Already allowed: open App info (Battery → Unrestricted) so it can be changed.
            startActivity(appSettings)
            return
        }
        // Shows the system dialog "Let app always run in background?" → Allow
        @SuppressLint("BatteryLife")
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            startActivity(ask)
        } catch (e: ActivityNotFoundException) {
            // Some phones hide that dialog: App info → Battery → Unrestricted
            startActivity(appSettings)
            Toast.makeText(this, "Tap Battery, then choose Unrestricted", Toast.LENGTH_LONG).show()
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun Context.has(perm: String) =
    ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

private fun Context.holdsScreeningRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

private fun Context.ignoresBatteryOpt() =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

private data class SetupItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val title: String,
    val offText: String,
    val done: Boolean,
    val onClick: () -> Unit,
)

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(
    tick: Int,
    onRequestRole: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestPerms: () -> Unit,
    onOpenBattery: () -> Unit,
    onPickLogo: (Carrier) -> Unit,
) {
    val ctx = LocalContext.current
    val c = One.colors
    val logoVersion by CarrierLogos.version
    val items = remember(tick) {
        listOf(
            SetupItem(Icons.Filled.Call, Color(0xFF34B36B), "Caller ID app",
                "Let Carrier ID see incoming numbers", ctx.holdsScreeningRole(), onRequestRole),
            SetupItem(Icons.Filled.Info, Color(0xFF3E7BFA), "Show over call screen",
                "Needed to display the network card", Settings.canDrawOverlays(ctx), onRequestOverlay),
            SetupItem(Icons.Filled.Person, Color(0xFFF59B3A), "Contacts, calls & phone",
                "Logos in contacts and call history", ctx.has(Manifest.permission.READ_CONTACTS) &&
                    ctx.has(Manifest.permission.READ_PHONE_STATE) &&
                    ctx.has(Manifest.permission.READ_CALL_LOG), onRequestPerms),
            SetupItem(Icons.Filled.Refresh, Color(0xFF8E6CEF), "Keep running",
                "Stop the phone from putting it to sleep", ctx.ignoresBatteryOpt(), onOpenBattery),
        )
    }
    val doneCount = items.count { it.done }
    val overlayOk = items[1].done

    LargeTitleScreen(
        title = "Carrier ID",
        subtitle = if (doneCount == items.size) "Ready. You'll see the caller's network on every call."
        else "$doneCount of ${items.size} settings on",
    ) {
        item { GroupHeader("Setup") }
        itemsIndexed(items) { i, it ->
            GroupCell(i, items.size, onClick = it.onClick) {
                SettingContent(it.icon, it.color, it.title,
                    if (it.done) "On" else it.offText, it.done, it.onClick)
            }
        }

        item { GroupHeader("Check a number") }
        item {
            var input by rememberSaveable { mutableStateOf("") }
            val carrier = CarrierLookup.lookup(input)
            OneCard {
                OneTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = "088 234 5678", icon = Icons.Filled.Phone,
                    background = c.field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                if (input.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarrierBadge(carrier, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(carrier?.displayName ?: "Unknown network", color = c.text,
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (carrier != null) "${PhoneFormat.display(input)}  ·  Cambodia mobile"
                                else "Not a Cambodian mobile number",
                                color = c.textSecondary, fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OneButton("Preview call card", enabled = overlayOk) {
                    val n = input.ifBlank { "012 345 678" }
                    CallerOverlay.show(ctx, n, CarrierLookup.lookup(n), preview = true)
                }
                if (!overlayOk) {
                    Spacer(Modifier.height(8.dp))
                    Text("Turn on \"Show over call screen\" first.", color = c.textSecondary,
                        fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item { GroupHeader("Networks & logos") }
        item {
            Text(
                "Tap a network to choose its logo from your photos. Save each company's " +
                    "logo image from its website or Facebook page first.",
                color = c.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 10.dp),
            )
        }
        val carriers = Carrier.entries
        itemsIndexed(carriers) { i, carrier ->
            val custom = remember(logoVersion, carrier) { CarrierLogos.hasCustom(ctx, carrier) }
            GroupCell(i, carriers.size, onClick = { onPickLogo(carrier) }) {
                CarrierBadge(carrier, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(carrier.displayName, color = c.text, fontSize = 16.sp)
                    Text(CarrierLookup.prefixesOf(carrier).joinToString("  "),
                        color = c.textSecondary, fontSize = 13.sp)
                }
                if (custom) {
                    Text(
                        "Remove", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { CarrierLogos.remove(ctx, carrier) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Text("Add logo", color = c.accent, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Text(
                "The network is worked out from the number's prefix. Numbers moved to another " +
                    "network will still show the original one.",
                color = c.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Contacts

@Composable
private fun ContactsScreen(tick: Int, onRequestPerms: () -> Unit) {
    val ctx = LocalContext.current
    val c = One.colors
    val granted = remember(tick) { ctx.has(Manifest.permission.READ_CONTACTS) }

    if (!granted) {
        LargeTitleScreen("Contacts", "See each contact's mobile network") {
            item {
                Spacer(Modifier.height(12.dp))
                OneCard {
                    Text("Allow access to your contacts to see a network logo next to every number.",
                        color = c.text, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    OneButton("Allow", onClick = onRequestPerms)
                }
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
        all.orEmpty().filter {
            (filter == null || it.carrier == filter) &&
                (query.isBlank() || it.name.contains(query, true) ||
                    it.number.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }))
        }
    }
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
                OnePill("All", filter == null, { filter = null })
                Carrier.entries.forEach { carrier ->
                    val n = all.count { it.carrier == carrier }
                    if (n > 0) OnePill("${carrier.displayName} $n", filter == carrier,
                        { filter = if (filter == carrier) null else carrier },
                        leading = { CarrierBadge(carrier, 18.dp) })
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
                Text("No contacts", color = c.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(40.dp), fontSize = 15.sp)
            }
        }

        groups.forEach { (letter, list) ->
            item(key = "h_$letter") { GroupHeader(letter) }
            itemsIndexed(list, key = { _, it -> "${it.contactId}:${it.number}" }) { i, item ->
                GroupCell(i, list.size, onClick = {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI,
                        item.contactId.toString())
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) {
                    ContactAvatar(item.name, item.carrier)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = c.text, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(PhoneFormat.display(item.number), color = c.textSecondary, fontSize = 13.sp)
                            item.carrier?.let {
                                Text("  ·  ", color = c.textSecondary, fontSize = 13.sp)
                                Text(it.displayName, color = Color(it.colorInt), fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
