package com.karim.foodrun

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karim.foodrun.orders.HubPairing
import com.karim.foodrun.shared.orders.GroupPlatform
import com.karim.foodrun.shared.orders.GroupReplyCallback
import com.karim.foodrun.shared.orders.GroupSubscription
import java.util.UUID
import java.util.concurrent.Executors

/** Retained native services; the current Activity is attached only while it exists. */
class GroupAndroidPlatform(context: Context) : GroupPlatform {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val files = Executors.newSingleThreadExecutor()
    private val storage = GroupSecureStore(this.context)
    private val transport = GroupTransport(this.context)
    private val discovery = GroupDiscovery(this.context)
    private var activity: ComponentActivity? = null
    private var documents: ActivityResultLauncher<Array<String>>? = null
    private var scanner: ActivityResultLauncher<ScanOptions>? = null
    private var importCallback: GroupReplyCallback? = null
    private var scanCallback: GroupReplyCallback? = null
    private var closed = false
    private var googleCallback: GroupReplyCallback? = null
    private var googleVerifier = ""
    private var googleChallenge = ""
    private var googleWasBackgrounded = false
    private var googleGeneration = 0

    override fun googleSignIn(callback: GroupReplyCallback) {
        googleCallback?.complete("", "Sign-in was restarted.")
        googleGeneration++
        googleCallback = callback
        googleVerifier = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")
        googleChallenge = java.security.MessageDigest.getInstance("SHA-256").digest(googleVerifier.toByteArray()).joinToString("") { "%02x".format(it) }
        googleWasBackgrounded = false
        try { requireNotNull(activity).startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://intrvioo.com/?nativeSignIn=$googleChallenge"))) }
        catch (_: Exception) { finishGoogle("", "Could not open Google sign-in. Check your browser.") }
    }
    fun googleBackgrounded() { if (googleCallback != null) googleWasBackgrounded = true }
    fun googleForegrounded() {
        if (googleWasBackgrounded && googleCallback != null) main.postDelayed({
            if (googleWasBackgrounded && googleCallback != null) finishGoogle("", "Google sign-in was cancelled. Try again.")
        }, 1000)
    }
    fun handleGoogleCallback(uri: android.net.Uri?) {
        if (uri?.scheme != "foodrun" || uri.host != "signin" || googleCallback == null) return
        if (uri.getQueryParameter("state") != googleChallenge) return
        val code = uri.getQueryParameter("code").orEmpty()
        if (!code.matches(Regex("[A-Za-z0-9_-]{43}"))) return
        googleWasBackgrounded = false
        val verifier = googleVerifier
        val generation = googleGeneration
        googleChallenge = ""
        files.execute {
            var token = ""
            var error = ""
            try {
                val url = java.net.URL("https://foodrun-api-q6b9.onrender.com/auth/native/exchange")
                val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
                try {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.connectTimeout = 15000; connection.readTimeout = 15000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(org.json.JSONObject().put("code", code).put("verifier", verifier).toString().toByteArray()) }
                    require(connection.responseCode == 200)
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    token = org.json.JSONObject(json).getString("firebaseToken")
                } finally { connection.disconnect() }
            } catch (_: Exception) { error = "Google sign-in could not finish. Start again in the app." }
            main.post { if (!closed && generation == googleGeneration) finishGoogle(token, error) }
        }
    }
    private fun finishGoogle(token: String, error: String) {
        val callback = googleCallback
        googleCallback = null; googleVerifier = ""; googleChallenge = ""; googleWasBackgrounded = false
        callback?.complete(token, error)
    }

    fun attach(activity: ComponentActivity) {
        check(!closed)
        this.activity = activity
        // Registration order stays stable so Android can deliver results after recreation.
        documents = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = importCallback
            importCallback = null
            if (uri == null) callback?.complete("", context.getString(R.string.group_import_cancelled))
            else files.execute {
                try {
                    val menu = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readUtf8Bounded(MAX_MENU_BYTES) }
                    main.post { if (!closed) callback?.complete(menu, "") }
                } catch (_: java.nio.charset.CharacterCodingException) {
                    main.post { if (!closed) callback?.complete("", context.getString(R.string.group_menu_invalid_encoding)) }
                } catch (_: IllegalArgumentException) {
                    main.post { if (!closed) callback?.complete("", context.getString(R.string.group_menu_too_large)) }
                } catch (_: Exception) {
                    main.post { if (!closed) callback?.complete("", context.getString(R.string.group_menu_open_failed)) }
                }
            }
        }
        scanner = activity.registerForActivityResult(ScanContract()) { result ->
            val callback = scanCallback
            scanCallback = null
            callback?.complete(result.contents.orEmpty(), if (result.contents == null) context.getString(R.string.group_scan_cancelled) else "")
        }
    }

    fun detach(activity: ComponentActivity) {
        if (this.activity === activity) {
            this.activity = null
            documents = null
            scanner = null
        }
    }

    override fun enableNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1204)
    }
    override fun notify(title: String, body: String) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("foodrun-orders", "Food Run invitations and selections", android.app.NotificationManager.IMPORTANCE_HIGH))
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val intent = android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(body.hashCode(), androidx.core.app.NotificationCompat.Builder(context, "foodrun-orders")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(tap).setAutoCancel(true).build())
    }
    override fun now() = System.currentTimeMillis()
    override fun uuid() = UUID.randomUUID().toString()
    override fun read(key: String): String = storage.read(key)
    override fun write(key: String, value: String): Boolean = storage.write(key, value)
    override fun request(hub: HubPairing, body: String, callback: GroupReplyCallback) = transport.request(hub, body, callback)
    override fun watch(hub: HubPairing, body: String, callback: GroupReplyCallback): GroupSubscription = transport.watch(hub, body, callback)

    override fun share(text: String, fileName: String) {
        val current = requireNotNull(activity) { context.getString(R.string.group_share_failed) }
        val intent = Intent(Intent.ACTION_SEND)
        if (fileName.isBlank()) {
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
        } else {
            val file = createGroupExportFile(context.cacheDir, fileName, text)
            val uri = FileProvider.getUriForFile(current, "${context.packageName}.files", file)
            intent.type = if (fileName.endsWith(".json")) "application/json" else "text/plain"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = android.content.ClipData.newRawUri(fileName, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        current.startActivity(Intent.createChooser(intent, null))
    }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Food Run restaurant order", text))
    }

    override fun importMenu(callback: GroupReplyCallback) {
        check(importCallback == null) { context.getString(R.string.group_import_active) }
        val launcher = requireNotNull(documents) { context.getString(R.string.group_menu_open_failed) }
        importCallback = callback
        try { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (error: Exception) { importCallback = null; throw error }
    }

    override fun scanPairing(callback: GroupReplyCallback) {
        check(scanCallback == null) { context.getString(R.string.group_scan_active) }
        val launcher = requireNotNull(scanner) { context.getString(R.string.group_scan_cancelled) }
        scanCallback = callback
        try {
            launcher.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(context.getString(R.string.group_scan_prompt)).setBeepEnabled(false),
            )
        } catch (error: Exception) { scanCallback = null; throw error }
    }

    override fun discover(callback: GroupReplyCallback) = discovery.discover(callback)

    override fun openLink(url: String) {
        val uri = android.net.Uri.parse(url)
        require(uri.scheme in listOf("tel", "https"))
        val current = requireNotNull(activity) { context.getString(R.string.group_share_failed) }
        current.startActivity(Intent(if (uri.scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW, uri))
    }

    fun close() {
        closed = true
        googleCallback = null; googleVerifier = ""; googleChallenge = ""; googleGeneration++
        transport.close()
        discovery.close()
        files.shutdownNow()
        main.removeCallbacksAndMessages(null)
        importCallback = null
        scanCallback = null
        activity = null
        documents = null
        scanner = null
    }

    private companion object { const val MAX_MENU_BYTES = 2 * 1024 * 1024 }
}
