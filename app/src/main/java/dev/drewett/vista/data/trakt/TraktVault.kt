package dev.drewett.vista.data.trakt

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

internal class TraktVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"trakt-v1"))
    fun read(): JSONObject? {
        if (!file.baseFile.exists()) return null
        val bytes=file.readFully()
        require(bytes.size in 28..8_388_608)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        val plain=cipher.doFinal(bytes,12,bytes.size-12)
        return try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }
    fun write(json: JSONObject) {
        val plain=json.toString().toByteArray(Charsets.UTF_8)
        require(plain.size<=8_388_580) { "The Trakt library is too large to save on this device." }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val output=file.startWrite()
        try { output.write(cipher.iv);output.write(encrypted);file.finishWrite(output) }
        catch(e:Exception) { file.failWrite(output);throw e }
    }
    fun delete() = file.delete()
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vista.trakt.v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vista.trakt.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
