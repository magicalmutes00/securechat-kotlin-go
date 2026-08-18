package com.securechat.di

import android.content.Context
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

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        val sharedPreferences = EncryptedSharedPreferences.create(
            "securechat_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return KeystoreTokenStorage(sharedPreferences)
    }
}

class KeystoreTokenStorage(
    private val encryptedPrefs: android.content.SharedPreferences
) : TokenStorage {

    private val ACCESS_TOKEN_KEY = "access_token"
    private val REFRESH_TOKEN_KEY = "refresh_token"
    private val ACCESS_TOKEN_EXPIRY_KEY = "access_token_expiry"

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
}