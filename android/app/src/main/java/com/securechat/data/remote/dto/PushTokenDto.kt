package com.securechat.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushTokenRequest(
    val fcm_token: String,
    val platform: String
)

@Serializable
data class RegisterPushTokenResponse(
    val registered: Boolean
)

@Serializable
data class UnregisterPushTokenRequest(
    val fcm_token: String
)
