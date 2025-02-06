package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable
class Image(
  /** The image name will usually be the entity's ID with the image type appended. */
  val name: String,

  /** Image bytes. */
  val data: ByteArray,

  /** Image type for convenience */
  val type: ImageType,
)

enum class ImageType {
  JPEG,
  PNG;

  fun extension(): String {
    return when (this) {
      JPEG -> "jpeg"
      PNG -> "png"
    }
  }

  fun toContentType(): String {
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
