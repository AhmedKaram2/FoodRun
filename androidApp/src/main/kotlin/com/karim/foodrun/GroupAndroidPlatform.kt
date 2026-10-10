package com.karim.foodrun

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karim.foodrun.orders.HubPairing
import com.karim.foodrun.shared.orders.GroupPlatform
import com.karim.foodrun.shared.orders.GroupReplyCallback
import com.karim.foodrun.shared.orders.GroupScheduledCallback
import com.karim.foodrun.shared.orders.GroupSubscription
import java.util.UUID
import java.util.concurrent.Executors

/** Retained native services; the current Activity is attached only while it exists. */
class GroupAndroidPlatform(context: Context) : GroupPlatform {
    private var photoPicker: ActivityResultLauncher<String>? = null
    private var photoCallback: GroupReplyCallback? = null
    private var receiptPhoto = false
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
    private var notificationPermission: ActivityResultLauncher<String>? = null
    private var notificationCallback: GroupReplyCallback? = null
    private var googleCallback: GroupReplyCallback? = null
    private var googleCancellation: android.os.CancellationSignal? = null
    private var googleGeneration = 0

    override fun googleSignIn(callback: GroupReplyCallback) {
        if (googleCallback != null) { callback.complete("", "Finish the current Google sign-in first."); return }
        val current = activity ?: run { callback.complete("", "Open FoodRun before signing in with Google."); return }
        val generation = ++googleGeneration
        googleCallback = callback
        val cancellation = android.os.CancellationSignal()
        googleCancellation = cancellation
        try {
            // The explicit button flow includes new accounts and accounts needing re-authentication.
            val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            CredentialManager.create(context).getCredentialAsync(current, request, cancellation, { main.post(it) },
                object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: GetCredentialResponse) {
                        if (!isCurrentGoogle(generation)) return
                        googleCancellation = null
                        try {
                            val credential = result.credential
                            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            // Exchange Google's ID token for Firebase's token: the hub verifies the same UID as web/iOS.
                            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                                .addOnCompleteListener { signIn ->
                                    if (!isCurrentGoogle(generation)) return@addOnCompleteListener
                                    val user = if (signIn.isSuccessful) signIn.result?.user else null
                                    if (user == null) finishGoogle("", "Could not connect this Google account. Please try again.")
                                    else user.getIdToken(true).addOnCompleteListener { firebase ->
                                        if (isCurrentGoogle(generation)) {
                                            val body = if (firebase.isSuccessful) firebase.result?.token.orEmpty() else ""
                                            finishGoogle(body, if (body.isEmpty()) "Could not connect this Google account. Please try again." else "")
                                        }
                                    }
                                }
                        } catch (_: Exception) { finishGoogle("", "Google sign-in could not finish. Please try again.") }
                    }
                    override fun onError(error: GetCredentialException) {
                        if (!isCurrentGoogle(generation)) return
                        finishGoogle("", when (error) {
                            is GetCredentialCancellationException -> "Google sign-in was cancelled. Try again."
                            is NoCredentialException -> "Add a Google account on this device, then try again."
                            else -> "Google sign-in could not start. Update Google Play services and try again."
                        })
                    }
                })
        } catch (_: Exception) { finishGoogle("", "Google sign-in could not start. Please try again.") }
    }
    private fun isCurrentGoogle(generation: Int) = !closed && generation == googleGeneration && googleCallback != null
    private fun finishGoogle(token: String, error: String) {
        val callback = googleCallback
        googleCallback = null; googleCancellation = null
        callback?.complete(token, error)
    }
    override fun accountSignedOut() { FirebaseAuth.getInstance().signOut() }

    fun attach(activity: ComponentActivity) {
        check(!closed)
        this.activity = activity
        photoPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val callback = photoCallback; photoCallback = null
            if(uri == null) callback?.complete("", "") else files.execute {
                try {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while(true) { val count = input.read(buffer); if(count < 0) break; output.write(buffer,0,count); require(output.size() <= 10 * 1024 * 1024) { "Choose a photo under 10 MB." } }
                        output.toByteArray()
                    }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo could not be opened." }
                    val decode = android.graphics.BitmapFactory.Options().apply { while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize > 2800) inSampleSize *= 2 }
                    val source = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,decode))
                    val side = minOf(source.width,source.height)
                    val square = if(receiptPhoto) source else android.graphics.Bitmap.createBitmap(source,(source.width-side)/2,(source.height-side)/2,side,side)
                    val limit = if(receiptPhoto) 1200.0 else 320.0; val scale = minOf(1.0,limit/maxOf(square.width,square.height))
                    val scaled = android.graphics.Bitmap.createScaledBitmap(square,maxOf(1,(square.width*scale).toInt()),maxOf(1,(square.height*scale).toInt()),true)
                    val output = java.io.ByteArrayOutputStream(); scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,output)
                    if(receiptPhoto && output.size() > 440_000) { output.reset(); scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,50,output) }
                    require(output.size() <= if(receiptPhoto) 440_000 else 128_000) { "Choose a smaller photo." }
                    val value = "data:image/jpeg;base64," + android.util.Base64.encodeToString(output.toByteArray(),android.util.Base64.NO_WRAP)
                    main.post { if(!closed) callback?.complete(value,"") }
                } catch(e: Exception) { main.post { if(!closed) callback?.complete("",e.message ?: "This photo could not be opened.") } }
            }
        }
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
        notificationPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val callback = notificationCallback; notificationCallback = null
            if (allowed) callback?.let(::fetchPushToken) else callback?.complete("", "Enable notifications in Android settings.")
        }
        scanner = activity.registerForActivityResult(ScanContract()) { result ->
            val callback = scanCallback
            scanCallback = null
            callback?.complete(result.contents.orEmpty(), if (result.contents == null) context.getString(R.string.group_scan_cancelled) else "")
        }
    }

    fun detach(activity: ComponentActivity) {
        if (this.activity === activity) {
            if (googleCancellation != null) {
                googleGeneration++
                googleCancellation?.cancel()
                finishGoogle("", "Google sign-in was interrupted. Please try again.")
            }
            this.activity = null
            documents = null
            scanner = null
            photoPicker = null
        }
    }
    fun choosePhoto(receipt: Boolean, callback: GroupReplyCallback) {
        if(photoCallback != null) { callback.complete("", "Finish selecting the current photo first."); return }
        val picker = photoPicker ?: run { callback.complete("", "Open the app before choosing a photo."); return }
        receiptPhoto = receipt; photoCallback = callback; picker.launch("image/*")
    }

    override fun adminRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) = transport.request(hub, body, callback, "admin/native")
    override fun notificationRequest(hub: HubPairing, body: String, callback: GroupReplyCallback) = transport.request(hub, body, callback, "notifications")
    override fun pushToken(prompt: Boolean, callback: GroupReplyCallback) {
        val enabled = context.getSharedPreferences("push-settings", Context.MODE_PRIVATE).getBoolean("enabled", false)
        if (!prompt && !enabled) { callback.complete("", ""); return }
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (prompt && notificationPermission != null) { notificationCallback = callback; notificationPermission?.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            else callback.complete("", "Enable notifications in Android settings.")
        } else fetchPushToken(callback)
    }
    private fun fetchPushToken(callback: GroupReplyCallback) {
        val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        messaging.token.addOnCompleteListener { result ->
            if (!result.isSuccessful) callback.complete("", "Notifications could not be enabled. Check Google Play services and try again.")
            else {
                val prefs = context.getSharedPreferences("push-settings", Context.MODE_PRIVATE)
                val installation = prefs.getString("installation", null) ?: UUID.randomUUID().toString()
                prefs.edit().putString("installation", installation).putBoolean("enabled", true).apply()
                callback.complete(org.json.JSONObject().put("token", result.result).put("platform", "android").put("installationId", installation).toString(), "")
            }
        }
    }
    override fun disablePush() {
        context.getSharedPreferences("push-settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        com.google.firebase.messaging.FirebaseMessaging.getInstance().let { it.isAutoInitEnabled = false; it.deleteToken() }
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
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
    override fun schedule(delayMillis: Long, callback: GroupScheduledCallback) { main.postDelayed({ callback.run() }, delayMillis) }
    override fun localOffsetSeconds(timeMillis: Long) = java.util.TimeZone.getDefault().getOffset(timeMillis) / 1000
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
        googleGeneration++; googleCallback = null; googleCancellation?.cancel(); googleCancellation = null
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
