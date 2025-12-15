package com.telnyx.voice.demo.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class TokenRequest(
    val identity: String,
    val deviceToken: String? = null
)

data class TokenResponse(
    val token: String,
    val identity: String,
    @SerializedName("expiresIn")
    val expiresIn: Int
)

interface TwilioBackendApi {
    @POST("/api/twilio/access-token")
    suspend fun getAccessToken(@Body request: TokenRequest): Response<TokenResponse>
}
