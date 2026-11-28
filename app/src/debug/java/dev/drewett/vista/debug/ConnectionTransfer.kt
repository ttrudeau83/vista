package dev.drewett.vista.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/**
 * Moves Vista's connections (Music, Trakt setup, Immich, calendar, LG TV, weather, appearance,
 * favourites) from one TV to another over ADB. Each vault's key is bound to its device's hardware,
 * so the source decrypts and the target re-encrypts with its own key.
 *
 * Debug builds only (never in a release APK), callable only from the ADB shell. Driven by
 * scripts/Transfer-Connections.ps1, which pipes `content read` on the source straight into
 * `content write` on the target without saving anything on the computer.
 */
internal object ConnectionCodec {
    private class Vault(val file: String, val alias: String, val versionPrefix: Boolean)

    /** Exactly the encrypted files the app writes; nothing else is read or written. */
    private val vaults = listOf(
        Vault("youtube-music-client-v1", "vista.youtube.music.v1", false),
        Vault("trakt-v1", "vista.trakt.v1", false),
        Vault("immich-connection", "vista.immich.connection.v1", true),
        Vault("lgtv-v1", "vista.lgtv.v1", false),
        Vault("family-calendar-v1", "vista.family.calendar.v1", false),
    )
    /** Unencrypted, non-secret files (relative to the app's private storage). */
    private val plainFiles = listOf(
        "no_backup/ambient-weather.json",
        "files/datastore/settings.preferences_pb",
        "files/datastore/favourites.preferences_pb",
    )
    /** Trakt replaces its refresh token on every refresh, so two TVs can't share one sign-in. */
    private val traktTokenFields = listOf("accessToken", "refreshToken", "expiresAt")

    private fun dataDir(context: Context) = requireNotNull(context.noBackupFilesDir.parentFile)

    fun export(context: Context): ByteArray {
        val vaultJson = JSONObject()
        for (vault in vaults) {
            val file = AtomicFile(File(context.noBackupFilesDir, vault.file))
            if (!file.baseFile.exists()) continue
            var plain = decrypt(file.readFully(), vault)
            if (vault.file == "trakt-v1") {
                val json = JSONObject(plain.toString(Charsets.UTF_8))
                traktTokenFields.forEach(json::remove)
                plain.fill(0); plain = json.toString().toByteArray(Charsets.UTF_8)
            }
            vaultJson.put(vault.file, Base64.encodeToString(plain, Base64.NO_WRAP))
            plain.fill(0)
        }
        val fileJson = JSONObject()
        for (path in plainFiles) {
            val file = File(dataDir(context), path)
            if (file.isFile) fileJson.put(path, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
        return JSONObject().put("format", "vista-connections-v1").put("source", Build.MODEL)
            .put("vaults", vaultJson).put("files", fileJson).toString().toByteArray(Charsets.UTF_8)
    }

    fun import(context: Context, payload: ByteArray): List<String> {
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        require(json.optString("format") == "vista-connections-v1") { "Not a Vista connections export" }
        val imported = mutableListOf<String>()
        val vaultJson = json.getJSONObject("vaults")
        for (vault in vaults) {
            val encoded = vaultJson.optString(vault.file).takeIf { it.isNotEmpty() } ?: continue
            val plain = Base64.decode(encoded, Base64.NO_WRAP)
            try { write(AtomicFile(File(context.noBackupFilesDir, vault.file)), encrypt(plain, vault)) } finally { plain.fill(0) }
            imported += vault.file
        }
        val fileJson = json.getJSONObject("files")
        for (path in plainFiles) {
            val encoded = fileJson.optString(path).takeIf { it.isNotEmpty() } ?: continue
            val target = File(dataDir(context), path).apply { parentFile?.mkdirs() }
            write(AtomicFile(target), Base64.decode(encoded, Base64.NO_WRAP))
            imported += path.substringAfterLast('/')
        }
        return imported
    }

    /** Which connections exist and decrypt on this device. Never prints contents. */
    fun summary(context: Context): String = vaults.joinToString("\n") { vault ->
        val file = AtomicFile(File(context.noBackupFilesDir, vault.file))
        val state = if (!file.baseFile.exists()) "absent" else
            runCatching { decrypt(file.readFully(), vault).fill(0); "ok" }.getOrElse { "unreadable (${it.javaClass.simpleName})" }
        "${vault.file}: $state"
    } + "\n" + plainFiles.joinToString("\n") { "${it.substringAfterLast('/')}: ${if (File(dataDir(context), it).isFile) "ok" else "absent"}" }

    private fun decrypt(bytes: ByteArray, vault: Vault): ByteArray {
        val offset = if (vault.versionPrefix) { require(bytes[0].toInt() == 1); 1 } else 0
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(vault.alias), GCMParameterSpec(128, bytes, offset, 12))
        return cipher.doFinal(bytes, offset + 12, bytes.size - offset - 12)
    }

    private fun encrypt(plain: ByteArray, vault: Vault): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(vault.alias)) }
        val encrypted = cipher.doFinal(plain)
        val prefix = if (vault.versionPrefix) 1 else 0
        return ByteBuffer.allocate(prefix + 12 + encrypted.size)
            .apply { if (vault.versionPrefix) put(1.toByte()) }.put(cipher.iv).put(encrypted).array()
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }

    /** Same key spec the app's vaults create, so the app reads imported files normally. */
    private fun key(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}

class ConnectionTransferProvider : ContentProvider() {
    private fun shellOnly() = require(Binder.getCallingUid() == 2000 || Binder.getCallingUid() == 0) { "ADB shell only" }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        shellOnly()
        val context = requireNotNull(context)
        val (read, write) = ParcelFileDescriptor.createPipe()
        return when (uri.lastPathSegment) {
            "export" -> {
                thread(name = "vista-export") {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                        val payload = ConnectionCodec.export(context)
                        try { out.write(payload) } finally { payload.fill(0) }
                    }
                }
                read
            }
            "import" -> {
                status = "receiving"
                thread(name = "vista-import") {
                    status = runCatching {
                        val payload = ParcelFileDescriptor.AutoCloseInputStream(read).use { it.readBytes() }
                        try { "imported " + ConnectionCodec.import(context, payload).joinToString(", ") } finally { payload.fill(0) }
                    }.getOrElse { "failed: ${it.javaClass.simpleName}: ${it.message}" }
                }
                write
            }
            else -> { read.close(); write.close(); throw FileNotFoundException(uri.toString()) }
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        shellOnly()
        return Bundle().apply {
            when (method) {
                "importStatus" -> putString("status", status)
                "summary" -> putString("summary", ConnectionCodec.summary(requireNotNull(context)))
                else -> throw IllegalArgumentException(method)
            }
        }
    }

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object { @Volatile var status = "idle" }
}
