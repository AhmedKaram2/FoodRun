package com.karim.foodrun

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only authenticated encryption; AtomicFile preserves the last complete save. */
internal class GroupSecureStore(context: Context) {
    private val directory = context.noBackupFilesDir

    @Synchronized
    fun read(name: String): String {
        val file = file(name)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ""
        val data = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
        require(data.size >= IV_BYTES + TAG_BYTES) { "Encrypted group data is incomplete." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BYTES * 8, data.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(data.copyOfRange(IV_BYTES, data.size)).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun write(name: String, value: String): Boolean = runCatching {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STORAGE_BYTES - IV_BYTES - TAG_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == IV_BYTES)
        val encrypted = cipher.iv + cipher.doFinal(bytes)
        val file = file(name)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }.isSuccess

    private fun file(name: String): AtomicFile {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        return AtomicFile(File(directory, "$name.enc"))
    }

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        val KEY_LOCK = Any()
        const val KEY_ALIAS = "foodrun.groups"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val MAX_STORAGE_BYTES = 32 * 1024 * 1024
    }
}

/** InputStream.readNBytes requires API 33; this remains bounded on Android 8+. */
internal fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit >= 0)
    val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - output.size() + 1L).toInt())
        if (count < 0) return output.toByteArray()
        require(output.size().toLong() + count <= limit) { "Content exceeds the allowed size." }
        output.write(buffer, 0, count)
    }
}

/** Reject damaged text instead of silently replacing restaurant names or menu options. */
internal fun InputStream.readUtf8Bounded(limit: Int): String = Charsets.UTF_8.newDecoder()
    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    .decode(java.nio.ByteBuffer.wrap(readBounded(limit)))
    .toString()
