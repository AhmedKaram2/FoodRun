package com.karim.foodrun

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** DownloadManager owns the download; only a verified FoodRun APK reaches Android's installer. */
internal class FoodRunUpdates(private val context: Context) {
    private val preferences = context.getSharedPreferences("foodrun-updates", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private var verifiedPath = ""
    private var verifiedModified = 0L
    @Suppress("DEPRECATION") private fun installed(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, signatureFlags())
    private fun signatureFlags() = if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION") private fun code(info: PackageInfo) = if(Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun certificates(info: PackageInfo): Set<String> = (if(Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty().map { hash(it.toByteArray()) }.toSet()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun candidate() = JSONObject(preferences.getString("candidate", "{}")!!)
    private fun file(release: JSONObject) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/foodrun-${release.getLong("versionCode")}.apk")
    private fun info(release: JSONObject, state: String, progress: Double = 0.0): JSONObject = JSONObject().put("available", release.optLong("versionCode") > code(installed())).put("versionName",release.optString("versionName")).put("state",state).put("progress",progress).put("canInstall",context.packageManager.canRequestPackageInstalls())
    @Synchronized fun check(): JSONObject {
        val previous = candidate()
        val previousDownload=preferences.getLong("download",-1)
        if(previousDownload > 0) {
            if(previous.optLong("versionCode") > code(installed())) {
                try { return status() } catch(_: Exception) { /* Retry a missing or invalid download. */ }
            }
            downloads.remove(previousDownload)
            preferences.edit().remove("download").apply()
            verifiedPath=""
        }
        val connection = URL("https://intrvioo.com/app-release.json").openConnection() as HttpURLConnection
        connection.connectTimeout=15000;connection.readTimeout=15000
        val release = try {
            require(connection.responseCode==200)
            connection.inputStream.bufferedReader().use { reader -> JSONObject(reader.readText().also { require(it.length<16000) }).getJSONObject("android") }
        } finally { connection.disconnect() }
        require(release.getString("versionName").matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        val url=Uri.parse(release.getString("url"));val version=release.getString("versionName")
        require(url.scheme=="https" && url.host=="github.com" && url.path=="/AhmedKaram2/FoodRun/releases/download/v$version/FoodRun-Android-$version.apk")
        require(release.getString("sha256").matches(Regex("[a-f0-9]{64}")))
        require(release.getLong("versionCode") in 1..Int.MAX_VALUE.toLong())
        preferences.edit().putString("candidate",release.toString()).apply()
        return info(release,"available")
    }
    @Synchronized fun start(): JSONObject {
        val release=candidate();require(release.getLong("versionCode") > code(installed()))
        if(preferences.getLong("download",-1)>0) {
            try { val current=status();if(current.getString("state") in listOf("downloading","ready")) return current } catch(_: Exception) { /* Replace an invalid package on explicit retry. */ }
            downloads.remove(preferences.getLong("download",-1))
        }
        val target=file(release);target.parentFile?.mkdirs();if(target.exists()) target.delete()
        val request=DownloadManager.Request(Uri.parse(release.getString("url"))).setTitle("FoodRun ${release.getString("versionName")}").setDescription("FoodRun update").setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"updates/${target.name}")
        val id=downloads.enqueue(request);preferences.edit().putLong("download",id).apply();verifiedPath=""
        return info(release,"downloading")
    }
    @Synchronized fun status(): JSONObject {
        val release=candidate();if(!release.has("versionCode"))return JSONObject().put("available",false)
        if(release.getLong("versionCode")<=code(installed()))return info(release,"current")
        val id=preferences.getLong("download",-1);if(id<0)return info(release,"available")
        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if(!cursor.moveToFirst())return info(release,"error")
            when(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_FAILED -> return info(release,"error")
                DownloadManager.STATUS_SUCCESSFUL -> { verify(file(release),release);return info(release,"ready",1.0) }
                else -> {
                    val received=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));val total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    return info(release,"downloading",if(total>0)(received.toDouble()/total).coerceIn(0.0,1.0) else 0.0)
                }
            }
        }
    }
    @Suppress("DEPRECATION") private fun verify(apk: File,release: JSONObject) {
        require(apk.isFile)
        if(verifiedPath==apk.path && verifiedModified==apk.lastModified())return
        val digest=MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input -> val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)} }
        require(digest.digest().joinToString(""){"%02x".format(it)}==release.getString("sha256"))
        val archive=requireNotNull(context.packageManager.getPackageArchiveInfo(apk.path,signatureFlags()))
        require(archive.packageName==context.packageName && code(archive)==release.getLong("versionCode") && code(archive)>code(installed()))
        val expected=certificates(installed());require(expected.isNotEmpty() && certificates(archive)==expected)
        verifiedPath=apk.path;verifiedModified=apk.lastModified()
    }
    @Synchronized fun install(): JSONObject {
        val release=candidate();verify(file(release),release)
        if(!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return info(release,"permission",1.0)
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file(release))
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return info(release,"installer",1.0)
    }
}
