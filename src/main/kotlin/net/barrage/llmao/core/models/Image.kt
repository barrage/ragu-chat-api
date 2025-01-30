package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable
class Image(val name: String, val data: ByteArray) {
  val contentType: String
    get() =
      when (name.substringAfterLast('.', "").lowercase()) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
      }
}
