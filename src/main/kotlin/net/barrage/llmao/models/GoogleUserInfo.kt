package net.barrage.llmao.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleUserInfo(
    val id: String,
    val name: String,
    @SerialName("given_name")
    val givenName: String,
    @SerialName("family_name")
    val familyName: String,
    val email: String,
    @SerialName("verified_email")
    val verifiedEmail: Boolean,
    val picture: String,
    val hd: String
)