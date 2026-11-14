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
import androidx.activity.compose.BackHandler
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

    /** Settings sub-page to open (from a notification), e.g. "spam". */
    private var openPage by mutableStateOf<String?>(null)

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
        openPage = intent?.getStringExtra(EXTRA_PAGE)
        setContent {
            OneUiTheme {
                var tab by rememberSaveable { mutableIntStateOf(if (startOnContacts) 0 else 2) }
                var page by rememberSaveable { mutableStateOf<String?>(null) }
                LaunchedEffect(openPage) {
                    openPage?.let { tab = 2; page = it; openPage = null }
                }
                BackHandler(enabled = tab == 2 && page != null) { page = null }
                GlassScaffold(
                    selected = tab,
                    tabs = listOf(
                        TabItem("Recents", AppIcons.Recents, AppIcons.RecentsFilled),
                        TabItem("Contacts", AppIcons.Person, AppIcons.PersonFilled),
                        TabItem("Settings", AppIcons.Sliders, AppIcons.SlidersFilled),
                    ),
                    onSelect = { if (it == tab) page = null; tab = it },
                ) {
                    Box(Modifier.fillMaxSize().background(One.colors.background)) {
                        when (tab) {
                            0 -> RecentsScreen(resumeTick, ::requestRuntimePerms)
                            1 -> ContactsScreen(resumeTick, ::requestRuntimePerms)
                            else -> when (page) {
                                "spam" -> SpamScreen(resumeTick, { page = null }, ::requestScreeningRole)
                                "messages" -> MessagesScreen(resumeTick, { page = null }, ::requestPerms)
                                "notes" -> NotesScreen(resumeTick, { page = null }, ::requestPerms)
                                else -> SettingsScreen(
                                    tick = resumeTick,
                                    onRequestRole = ::requestScreeningRole,
                                    onRequestOverlay = ::openOverlaySettings,
                                    onRequestPerms = ::requestRuntimePerms,
                                    onOpenBattery = ::openBatterySettings,
                                    onPickLogo = ::pickLogo,
                                    onOpenPage = { page = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PAGE)?.let { openPage = it }
    }

    /** Asks for specific permissions; opens App info if Android won't ask again. */
    private fun requestPerms(perms: Array<String>) {
        if (perms.all { has(it) }) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            permsLauncher.launch(perms)
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
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

internal fun Context.has(perm: String) =
    ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

internal fun Context.holdsScreeningRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

internal fun Context.ignoresBatteryOpt() =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

