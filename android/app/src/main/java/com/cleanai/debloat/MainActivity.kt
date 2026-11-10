package com.cleanai.debloat

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import com.cleanai.debloat.agent.AgentViewModel
import com.cleanai.debloat.agent.Launch
import com.cleanai.debloat.agent.QrScan
import android.net.Uri
import com.cleanai.debloat.ui.AppRoot
import com.cleanai.debloat.ui.AppTheme
import com.cleanai.debloat.ui.DocLaunch
import com.cleanai.debloat.ui.MobileHandlerTheme
import com.cleanai.debloat.data.Updates
import android.os.Build
import android.widget.Toast
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Screens another part of the app may open through the floating calculator's EXTRA_OPEN. */
private val OPENABLE = setOf("calculator")

class MainActivity : ComponentActivity() {
    private val vm: AgentViewModel by viewModels()

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.onImagesPicked(uris)
    }

    private val scanDocs = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val scan = if (result.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(result.data) else null
        vm.onDocScanned(scan?.pdf?.uri, scan?.pdf?.pageCount ?: 0, scan?.pages?.map { it.imageUri }.orEmpty(), null)
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onLocationPermission(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light HaHo background: dark status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        AppTheme.load(this, (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        DocLaunch.showScreen = { vm.show(it) }
        setContent {
            MobileHandlerTheme { AppRoot(vm) }
        }
        // About once a day, quietly look for a new version and notify (Settings > App updates).
        lifecycleScope.launch { Updates.backgroundCheck(applicationContext) }
        askNotificationsOnce()
        openRequested(intent)
        // The chat asks for scanners / pickers / permissions through vm.launch.
        lifecycleScope.launch {
            snapshotFlow { vm.launch }.filterNotNull().collect { what ->
                vm.launchHandled()
                when (what) {
                    Launch.QR -> scanQr()
                    Launch.DOC -> scanDocument()
                    Launch.PHOTOS -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    Launch.LOCATION -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequested(intent)
    }

    /** The floating calculator's title opens the full calculator here; a file opened from another app goes to its editor. */
    private fun openRequested(intent: Intent?) {
        // The activity is exported, so other apps could send this too: only the screens the app itself asks for.
        intent?.getStringExtra(com.cleanai.debloat.overlay.FloatingCalcService.EXTRA_OPEN)?.takeIf { it in OPENABLE }?.let { vm.show(it) }
        intent?.removeExtra(com.cleanai.debloat.overlay.FloatingCalcService.EXTRA_OPEN)
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { openFile(it, intent.type) }
            intent.action = null
        }
    }

    private fun openFile(uri: Uri, mime: String?) {
        val name = runCatching { com.cleanai.debloat.data.PdfEdit.displayName(this, uri) }.getOrNull()
        val kind = DocLaunch.kindOf(mime ?: contentResolver.getType(uri), name)
        if (kind == null) {
            Toast.makeText(this, "Mobile Handler opens PDF, Word (.docx) and Excel (.xlsx, .csv) files.", Toast.LENGTH_LONG).show()
            return
        }
        DocLaunch.open(kind, uri)
        // An editor with unsaved changes asks first, and moves on to this file's editor if the user agrees.
        if (DocLaunch.canSwitchTo(kind)) vm.show(kind.screen)
    }

    override fun onResume() {
        super.onResume()
        // The user often comes back from Shizuku or Settings; refresh the connection pill.
        vm.refreshShizuku()
        // Back from allowing "Display over other apps": open the floating calculator that was asked for.
        com.cleanai.debloat.overlay.FloatingCalcService.resumeIfAllowed(this)
    }

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Android 13+: update notifications need permission; ask once, the switch in Settings can ask again. */
    private fun askNotificationsOnce() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT < 33 || Updates.canNotify(this) || !Updates.notifyOn(this) || prefs.getBoolean("asked_notify", false)) return
        prefs.edit().putBoolean("asked_notify", true).apply()
        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * The QR scanner lives in Google Play services and is downloaded on first use. Some phones never get it
     * at install time, so check for it and ask Play services to download it before opening the camera.
     */
    private fun scanQr() {
        val scanner = GmsBarcodeScanning.getClient(this)
        val modules = ModuleInstall.getClient(this)
        modules.areModulesAvailable(scanner)
            .addOnSuccessListener { if (it.areModulesAvailable()) startQr() else installQrScanner(modules, scanner) }
            .addOnFailureListener { startQr() }
    }

    private fun installQrScanner(modules: ModuleInstallClient, scanner: OptionalModuleApi) {
        Toast.makeText(this, "Getting the QR scanner from Google Play services…", Toast.LENGTH_LONG).show()
        val request = ModuleInstallRequest.newBuilder().addApi(scanner).build()
        modules.installModules(request)
            .addOnSuccessListener { if (it.areModulesAlreadyInstalled()) startQr() else waitForQrScanner(modules, scanner) }
            .addOnFailureListener { vm.onQr(null, scannerError("QR scanner", it)) }
    }

    /** Polls until the download finishes (about a minute at most), then opens the scanner. */
    private fun waitForQrScanner(modules: ModuleInstallClient, scanner: OptionalModuleApi, tries: Int = 0) {
        modules.areModulesAvailable(scanner).addOnCompleteListener { t ->
            when {
                t.isSuccessful && t.result.areModulesAvailable() -> startQr()
                tries >= 60 -> vm.onQr(null, "Google Play services is still downloading the QR scanner. Stay online and try again in a minute.")
                else -> window.decorView.postDelayed({ waitForQrScanner(modules, scanner, tries + 1) }, 1000)
            }
        }
    }

    private fun startQr() {
        GmsBarcodeScanning.getClient(this).startScan()
            .addOnSuccessListener { b ->
                vm.onQr(
                    QrScan(
                        raw = b.rawValue.orEmpty(),
                        url = b.url?.url ?: b.rawValue?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                        wifiName = b.wifi?.ssid,
                        wifiPassword = b.wifi?.password?.takeIf { it.isNotEmpty() },
                        phone = b.phone?.number?.takeIf { b.valueType == Barcode.TYPE_PHONE },
                    ),
                    null,
                )
            }
            .addOnCanceledListener { vm.onQr(null, null) }
            .addOnFailureListener { vm.onQr(null, scannerError("QR scanner", it)) }
    }

    private fun scanDocument() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF, GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { scanDocs.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.onDocScanned(null, 0, emptyList(), scannerError("document scanner", it)) }
    }

    /** First use downloads the scanner through Google Play services; say so instead of a raw error. */
    private fun scannerError(name: String, e: Exception) =
        if ((e as? MlKitException)?.errorCode == MlKitException.UNAVAILABLE ||
            e.message.orEmpty().contains("download", ignoreCase = true)
        )
            "Google Play services is still downloading the $name. Stay online and try again in a minute."
        else "⚠️ Couldn't open the $name: ${e.message}"
}
