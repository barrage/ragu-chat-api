package net.barrage.llmao.app.auth

import kotlinx.serialization.Serializable

@Serializable
data class JsonWebKey(
  val kty: String,
  val kid: String,
  val use: String,
  val alg: String,
  val n: String,
  val e: String,
)

@Serializable class JsonWebKeys(val keys: List<JsonWebKey>)
