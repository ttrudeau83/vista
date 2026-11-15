package dev.drewett.vista.data.immich

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps settings and the key in one encrypted atomic transaction, excluded from backup. */
internal class ImmichVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "immich-connection"))

    fun read(): SavedImmich? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        require(bytes.size in 30..65_536)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.get().toInt() == 1)
        val iv = ByteArray(12).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted)
        try {
            val json = JSONObject(plain.toString(Charsets.UTF_8))
            val server = ImmichProtocol.normalizeServer(json.getString("server"))
            val apiKey = json.getString("key").also { require(it.isNotBlank()) }
            val albumId = json.optString("albumId").takeIf { ImmichProtocol.id(it) != null }
            val namespace = json.optString("namespace").takeIf { ImmichProtocol.id(it) != null }
            return SavedImmich(server, apiKey, albumId, json.optString("albumName").takeIf { it.isNotBlank() },
                json.optBoolean("fit", false), namespace)
        } finally { plain.fill(0) }
    }

    fun write(saved: SavedImmich) {
        val plain = JSONObject().put("server", saved.server).put("key", saved.key)
            .put("albumId", saved.albumId).put("albumName", saved.albumName)
            .put("fit", saved.fit).put("namespace", saved.namespace)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(1.toByte()).put(cipher.iv).put(encrypted).array()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun delete() = file.delete()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "vista.immich.connection.v1" }
}

/** Intentionally not a data class: generated toString/copy must not expose the API key. */
internal class SavedImmich(
    val server: String,
    val key: String,
    val albumId: String? = null,
    val albumName: String? = null,
    val fit: Boolean = false,
    val namespace: String? = null,
) {
    fun publicConfig() = ImmichConfig(server, true, albumId, albumName, fit, false, false, namespace)
    override fun toString() = "SavedImmich(redacted)"
}
