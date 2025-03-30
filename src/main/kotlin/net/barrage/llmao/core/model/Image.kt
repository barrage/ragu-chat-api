package net.barrage.llmao.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An image obtained from a storage provider. */
@Serializable
data class Image(
  /** Image bytes. */
  val data: ByteArray,

  /** Image type for convenience */
  val type: ImageType,
)

enum class ImageType {
  @SerialName("jpeg") JPEG,
  @SerialName("png") PNG;

  override fun toString(): String {
    return when (this) {
      JPEG -> "jpeg"
      PNG -> "png"
    }
  }

  fun contentType(): String {
    return when (this) {
      JPEG -> "image/jpeg"
      PNG -> "image/png"
    }
  }

  companion object {
    fun fromContentType(contentType: String): ImageType? {
      return when (contentType) {
        "image/jpeg" -> JPEG
        "image/png" -> PNG
        else -> return null
      }
    }

    fun fromImageName(name: String): ImageType? {
      return when (name.substringAfterLast('.', "").lowercase()) {
        "jpeg" -> JPEG
        "png" -> PNG
        else -> return null
      }
    }
  }
}
