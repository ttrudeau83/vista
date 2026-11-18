package dev.drewett.vista.data.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The paired TV's address and pairing key ("client-key"), encrypted with a device-bound key.
 * [permissions] is the permission-set version the key was granted for; older keys must pair again.
 */
internal data class LgTvPairing(val host: String, val clientKey: String?, val permissions: Int = 0)

internal class LgTvVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "lgtv-v1"))

    fun read(): LgTvPairing? = runCatching {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        require(bytes.size in 28..65_536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        val json = JSONObject(cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8))
        LgTvPairing(json.getString("host"), json.optString("clientKey").ifBlank { null }, json.optInt("permissions", 0))
    }.getOrNull()

    fun write(pairing: LgTvPairing) {
        val plain = JSONObject().put("host", pairing.host).put("clientKey", pairing.clientKey ?: "").put("permissions", pairing.permissions).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val output = file.startWrite()
        try { output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }

    fun delete() = file.delete()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vista.lgtv.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vista.lgtv.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
