package net.barrage.llmao.app.auth.apple

import kotlinx.serialization.Serializable

@Serializable
data class ApplePublicKey(
  val kty: String,
  val kid: String,
  val use: String,
  val alg: String,
  val n: String,
  val e: String,
)

@Serializable class AppleKeysResponse(val keys: List<ApplePublicKey>)
