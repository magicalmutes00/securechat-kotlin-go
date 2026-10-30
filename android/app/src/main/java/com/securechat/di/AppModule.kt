package com.securechat.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.securechat.core.security.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * The device's single encrypted preference store. Both the auth tokens and
     * the Signal Protocol private keys live here, which is deliberate: one
     * identity per device, encrypted under one Keystore-backed master key.
     */
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return EncryptedSharedPreferences.create(
            "securechat_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideTokenStorage(sharedPreferences: SharedPreferences): TokenStorage {
        return KeystoreTokenStorage(sharedPreferences)
    }
}

class KeystoreTokenStorage(
    private val encryptedPrefs: android.content.SharedPreferences
) : TokenStorage {

    private val ACCESS_TOKEN_KEY = "access_token"
    private val REFRESH_TOKEN_KEY = "refresh_token"
    private val ACCESS_TOKEN_EXPIRY_KEY = "access_token_expiry"
    private val USER_ID_KEY = "user_id"

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) =
        com.securechat.core.common.Result.runCatching {
            encryptedPrefs.edit()
                .putString(ACCESS_TOKEN_KEY, accessToken)
                .putString(REFRESH_TOKEN_KEY, refreshToken)
                .putLong(ACCESS_TOKEN_EXPIRY_KEY, System.currentTimeMillis() + expiresIn * 1000)
                .apply()
        }

    override suspend fun getAccessToken() =
        com.securechat.core.common.Result.runCatching {
            encryptedPrefs.getString(ACCESS_TOKEN_KEY, null)
                ?: throw IllegalStateException("Access token not found")
        }

    override suspend fun getRefreshToken() =
        com.securechat.core.common.Result.runCatching {
            encryptedPrefs.getString(REFRESH_TOKEN_KEY, null)
                ?: throw IllegalStateException("Refresh token not found")
        }

    override suspend fun clear() =
        com.securechat.core.common.Result.runCatching {
            encryptedPrefs.edit().clear().apply()
        }

    override suspend fun isAccessTokenExpired(): Boolean {
        val expiry = encryptedPrefs.getLong(ACCESS_TOKEN_EXPIRY_KEY, 0)
        return System.currentTimeMillis() >= expiry
    }

    override suspend fun saveUserId(userId: Long) =
        com.securechat.core.common.Result.runCatching {
            encryptedPrefs.edit().putLong(USER_ID_KEY, userId).apply()
        }

    override suspend fun getUserId(): Long? =
        encryptedPrefs.getLong(USER_ID_KEY, 0L).takeIf { it > 0L }
}