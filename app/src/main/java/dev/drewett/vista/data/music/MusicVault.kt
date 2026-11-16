package dev.drewett.vista.data.music

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

/** Deliberately not a data class; no generated credential-bearing toString. */
internal class MusicCredentials(val clientId: String, val clientSecret: String,
    var refreshToken: String = "", var accessToken: String = "", var expiresAt: Long = 0,
    var channelId: String = "", var channelTitle: String = "",
    var pendingDeviceCode: String = "", var pendingUserCode: String = "", var pendingVerificationUrl: String = "",
    var pendingExpiresAt: Long = 0, var pendingInterval: Long = 5) {
    override fun toString() = "MusicCredentials(redacted)"
    fun pendingPairing(now: Long = System.currentTimeMillis()): MusicPairing? =
        if (pendingDeviceCode.isNotBlank() && pendingUserCode.isNotBlank() && pendingVerificationUrl.startsWith("https://") && pendingExpiresAt > now)
            MusicPairing(pendingUserCode,pendingVerificationUrl,pendingExpiresAt) else null
    fun clearPending() { pendingDeviceCode="";pendingUserCode="";pendingVerificationUrl="";pendingExpiresAt=0;pendingInterval=5 }
    fun toJson(): JSONObject = JSONObject().put("clientId",clientId).put("clientSecret",clientSecret)
        .put("refreshToken",refreshToken).put("accessToken",accessToken).put("expiresAt",expiresAt)
        .put("channelId",channelId).put("channelTitle",channelTitle)
        .put("pendingDeviceCode",pendingDeviceCode).put("pendingUserCode",pendingUserCode)
        .put("pendingVerificationUrl",pendingVerificationUrl).put("pendingExpiresAt",pendingExpiresAt).put("pendingInterval",pendingInterval)
    companion object {
        fun fromJson(json: JSONObject) = MusicCredentials(json.getString("clientId"),json.getString("clientSecret"),
            json.optString("refreshToken"),json.optString("accessToken"),json.optLong("expiresAt"),json.optString("channelId"),json.optString("channelTitle"),
            json.optString("pendingDeviceCode"),json.optString("pendingUserCode"),json.optString("pendingVerificationUrl"),json.optLong("pendingExpiresAt"),json.optLong("pendingInterval",5).coerceIn(5,120))
    }
}

internal class MusicVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"youtube-music-client-v1"))
    fun read(): MusicCredentials? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        require(bytes.size in 28..65_536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        val plain = cipher.doFinal(bytes,12,bytes.size-12)
        try {
            val json = JSONObject(plain.toString(Charsets.UTF_8))
            return MusicCredentials.fromJson(json)
        } finally { plain.fill(0) }
    }
    fun write(value: MusicCredentials) {
        val plain = value.toJson().toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val output = file.startWrite()
        try { output.write(ByteBuffer.allocate(12+encrypted.size).put(cipher.iv).put(encrypted).array()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun delete() = file.delete()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vista.youtube.music.v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vista.youtube.music.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
