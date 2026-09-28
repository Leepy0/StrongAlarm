package io.github.leepy0.strongalarm.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore(AES-GCM)로 암호화해 파일 저장. SmartThings client secret·토큰 보관용 */
object SecureStore {
    private const val KEY_ALIAS = "strongalarm_secure"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    fun write(ctx: Context, name: String, plain: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + enc
            Storage.writeText(ctx, name, Base64.getEncoder().encodeToString(payload))
        } catch (e: Exception) {
            Log.e("SecureStore", "암호화 저장 실패", e)
        }
    }

    fun read(ctx: Context, name: String): String? {
        val text = Storage.readText(ctx, name) ?: return null
        return try {
            val payload = Base64.getDecoder().decode(text)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_LEN))
            }
            cipher.doFinal(payload, IV_LEN, payload.size - IV_LEN).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("SecureStore", "복호화 실패", e)
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
