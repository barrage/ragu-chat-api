package net.barrage.llmao.core.model

import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason

/** An image obtained from a storage provider. */
@Serializable
data class Image(
    /** Image bytes. */
    val data: ByteArray,

    /** Image type for convenience */
    val type: ImageType,
) {
    companion object {
        fun fromBase64Uri(uri: String): Image {
            val split = uri.split(";")

            if (split.size != 2) {
                throw AppError.api(ErrorReason.InvalidParameter, "Invalid image URI")
            }

            val (type, data) = split

            if (!data.startsWith("base64,")) {
                throw AppError.api(ErrorReason.InvalidParameter, "Only base64 images are supported")
            }

            return Image(
                data = data.substringAfter("base64,").decodeBase64Bytes(),
                type = ImageType.fromContentType(type.substringAfter("data:")),
            )
        }
    }
}

enum class ImageType {
    @SerialName("jpeg")
    JPEG,
    @SerialName("png")
    PNG,
    @SerialName("webp")
    WEBP;

    override fun toString(): String {
        return when (this) {
            JPEG -> "jpeg"
            PNG -> "png"
            WEBP -> "webp"
        }
    }

    fun contentType(): String {
        return when (this) {
            JPEG -> "image/jpeg"
            PNG -> "image/png"
            WEBP -> "image/webp"
        }
    }

    companion object {
        fun fromContentType(contentType: String): ImageType {
            return when (contentType) {
                "image/jpeg",
                "image/jpg" -> JPEG

                "image/png" -> PNG
                "image/webp" -> WEBP

                else -> throw AppError.api(
                    ErrorReason.InvalidParameter,
                    "Unsupported image content type"
                )
            }
        }

        fun fromImageName(name: String): ImageType {
            return when (name.substringAfterLast('.')) {
                "jpeg",
                "jpg" -> JPEG

                "png" -> PNG
                "webp" -> WEBP

                else -> throw AppError.api(
                    ErrorReason.InvalidParameter,
                    "Unsupported image content type"
                )
            }
        }
    }
}
