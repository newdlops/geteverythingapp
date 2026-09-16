package com.getevapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(context: Context, preferenceName: String = "native_app") {
    private val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutableSession = MutableStateFlow(readSession())
    val session = mutableSession.asStateFlow()
    private val mutableFavorites = MutableStateFlow(readFavorites(mutableSession.value?.id))
    val favorites = mutableFavorites.asStateFlow()

    @Synchronized
    fun saveSession(value: Session) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(gson.toJson(value).toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("session", encoded).commit()) { "로그인 정보를 저장할 수 없습니다." }
        mutableSession.value = value
        mutableFavorites.value = readFavorites(value.id)
    }

    @Synchronized
    fun clearSession(expectedToken: String? = null) {
        if (expectedToken != null && mutableSession.value?.accessToken != expectedToken) return
        prefs.edit().remove("session").apply()
        mutableSession.value = null
        mutableFavorites.value = emptyList()
    }

    @Synchronized
    fun toggleFavorite(deal: Deal) {
        val userId = mutableSession.value?.id ?: return
        val current = mutableFavorites.value
        val next = if (current.any { it.key == deal.key }) current.filterNot { it.key == deal.key } else listOf(deal) + current
        check(prefs.edit().putString("favorites_$userId", gson.toJson(next)).commit()) { "관심상품을 저장할 수 없습니다." }
        mutableFavorites.value = next
    }

    private fun readFavorites(id: String?): List<Deal> = if (id == null) emptyList() else runCatching {
        gson.fromJson<List<Deal>>(prefs.getString("favorites_$id", "[]"), object : TypeToken<List<Deal>>() {}.type)
    }.getOrNull().orEmpty()

    private fun readSession(): Session? = runCatching {
        val raw = prefs.getString("session", null) ?: return null
        val parts = raw.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        gson.fromJson(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8), Session::class.java)
            .takeIf { !it.accessToken.isNullOrBlank() && !it.id.isNullOrBlank() }
    }.getOrElse {
        prefs.edit().remove("session").apply()
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("geteverything_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("geteverything_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
