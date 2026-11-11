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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.carrierid.app.call.CallerOverlay
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLogos
import com.carrierid.app.carrier.CarrierLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the setup checklist re-reads permission state. */
    private var resumeTick by mutableIntStateOf(0)

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }
    private val permsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppScreen(
                        tick = resumeTick,
                        onRequestRole = ::requestScreeningRole,
                        onRequestOverlay = ::openOverlaySettings,
                        onRequestPerms = ::requestRuntimePerms,
                        onOpenBattery = ::openBatterySettings,
                    )
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
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestRuntimePerms() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permsLauncher.launch(perms.toTypedArray())
    }

    private fun openBatterySettings() {
        // Opens the system list; on Samsung also check
        // Settings > Battery > Background usage limits > Never sleeping apps.
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

// ---------------------------------------------------------------- helpers

private fun Context.has(perm: String) =
    ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

private fun Context.holdsScreeningRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

private fun Context.ignoresBatteryOpt() =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

// ---------------------------------------------------------------- UI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    tick: Int,
    onRequestRole: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestPerms: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Carrier ID") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("⚙") }, label = { Text("Setup") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("👥") }, label = { Text("Contacts") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> SetupScreen(tick, onRequestRole, onRequestOverlay, onRequestPerms, onOpenBattery)
                else -> ContactsScreen(tick)
            }
        }
    }
}

@Composable
private fun SetupScreen(
    tick: Int,
    onRequestRole: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestPerms: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val ctx = LocalContext.current
    // Re-evaluated whenever tick changes (i.e. on resume / after a permission dialog)
    val roleOk = remember(tick) { ctx.holdsScreeningRole() }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val permsOk = remember(tick) {
        ctx.has(Manifest.permission.READ_CONTACTS) && ctx.has(Manifest.permission.READ_PHONE_STATE)
    }
    val batteryOk = remember(tick) { ctx.ignoresBatteryOpt() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Turn these on so the carrier logo appears when someone calls.",
            style = MaterialTheme.typography.bodyMedium)

        SetupRow("1. Set as Caller ID & spam app", roleOk, onRequestRole)
        SetupRow("2. Allow display over other apps", overlayOk, onRequestOverlay)
        SetupRow("3. Allow contacts & phone access", permsOk, onRequestPerms)
        SetupRow("4. Don't put app to sleep (Samsung)", batteryOk, onOpenBattery)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Check a number", fontWeight = FontWeight.Bold)
        var input by remember { mutableStateOf("") }
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("e.g. 088 234 5678 or +855 12 345 678") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        val carrier = CarrierLookup.lookup(input)
        if (input.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarrierBadge(carrier, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(carrier?.displayName ?: "Unknown / not a Cambodian mobile number")
            }
        }
        Button(
            onClick = { CallerOverlay.show(ctx, input.ifBlank { "012 345 678" },
                CarrierLookup.lookup(input.ifBlank { "012345678" })) },
            enabled = overlayOk
        ) { Text("Preview call overlay") }
        if (!overlayOk) {
            Text("Enable step 2 to preview the overlay.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SetupRow(title: String, done: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = !done, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Text(if (done) "✅" else "Tap to enable",
                color = if (done) Color.Unspecified else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ContactsScreen(tick: Int) {
    val ctx = LocalContext.current
    val granted = remember(tick) { ctx.has(Manifest.permission.READ_CONTACTS) }
    if (!granted) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Allow contacts access in Setup (step 3) to see your contacts here.")
        }
        return
    }

    var contacts by remember { mutableStateOf<List<ContactNumber>?>(null) }
    LaunchedEffect(tick) {
        contacts = withContext(Dispatchers.IO) { ContactsRepository.load(ctx) }
    }
    var filter by remember { mutableStateOf<Carrier?>(null) }
    var query by remember { mutableStateOf("") }

    val all = contacts
    if (all == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val shown = all.filter {
        (filter == null || it.carrier == filter) &&
            (query.isBlank() || it.name.contains(query, true) || it.number.contains(query))
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search name or number") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null },
                label = { Text("All ${all.size}") })
            Carrier.entries.forEach { c ->
                val n = all.count { it.carrier == c }
                if (n > 0) FilterChip(
                    selected = filter == c, onClick = { filter = c },
                    label = { Text("${c.displayName} $n") },
                    leadingIcon = { CarrierBadge(c, 18.dp) }
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { "${it.contactId}:${it.number}" }) { item ->
                ContactRow(item) {
                    val uri = Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_URI, item.contactId.toString()
                    )
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }
}

@Composable
private fun ContactRow(item: ContactNumber, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CarrierBadge(item.carrier, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            Text(item.number, color = Color.Gray, fontSize = 14.sp)
        }
        Text(
            item.carrier?.displayName ?: "",
            color = item.carrier?.let { Color(it.colorInt) } ?: Color.Gray,
            fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

/** Uses your logo file if present (res/drawable/logo_xxx), otherwise a coloured letter circle. */
@Composable
fun CarrierBadge(carrier: Carrier?, size: Dp) {
    val ctx = LocalContext.current
    val logoId = remember(carrier) { carrier?.let { CarrierLogos.drawableId(ctx, it) } ?: 0 }
    if (logoId != 0) {
        Image(painterResource(logoId), contentDescription = carrier?.displayName,
            modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(carrier?.let { Color(it.colorInt) } ?: Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text(carrier?.initial ?: "?", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
        }
    }
}
