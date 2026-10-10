package app.dictation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Base URL (plain) and API token (AES-GCM with an Android Keystore key) in app preferences. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_ALIAS = "dictation-token"
    private const val K_TOKEN = "token"
    private const val K_URL = "baseUrl"
    private const val K_BUBBLE = "bubble"

    fun bubbleEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_BUBBLE, false)

    fun setBubbleEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_BUBBLE, on).apply()
    }

    fun baseUrl(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_URL, null)
            ?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL

    fun setBaseUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_URL, url.trim().trimEnd('/')).apply()
    }

    fun token(ctx: Context): String? {
        val stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_TOKEN, null)
            ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(cipher.doFinal(raw, 12, raw.size - 12))
        } catch (e: Exception) {
            null
        }
    }

    fun setToken(ctx: Context, token: String) {
        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (token.isBlank()) {
            edit.remove(K_TOKEN)
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(token.trim().toByteArray())
            edit.putString(K_TOKEN, Base64.encodeToString(out, Base64.NO_WRAP))
        }
        edit.apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }
}
