package net.barrage.llmao.core.models

import java.util.*
import kotlinx.serialization.Serializable

@Serializable
class Image(val contentType: String, val data: String) {
  constructor(
    contentType: String,
    bytes: ByteArray,
  ) : this(contentType, Base64.getEncoder().encodeToString(bytes))
}
