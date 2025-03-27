package net.barrage.llmao.app.api.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.toByteArray
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.ImageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.string

/** Extract all the request parameters for an image upload and run the block. */
suspend fun ApplicationCall.runWithImage(
  /**
   * The entity in question for whom the image is being uploaded. This is usually extracted from the
   * path parameters.
   */
  entityId: KUUID,

  /** The block to execute with the successfully extracted image. */
  b: suspend (image: Image) -> Unit,
) {
  val contentLength =
    request.contentLength()
      ?: throw AppError.api(ErrorReason.InvalidParameter, "Expected content in request body")

  if (contentLength > application.environment.config.string("upload.image.maxFileSize").toLong()) {
    throw AppError.api(ErrorReason.PayloadTooLarge, "Image size exceeds the maximum allowed size")
  }

  val data = receiveChannel().toByteArray()

  val imageType =
    ImageType.fromContentType(request.contentType().toString())
      ?: throw AppError.api(
        ErrorReason.InvalidContentType,
        "Expected type: image/jpeg or image/png",
      )
  val name = "${entityId}.${imageType.extension()}"

  b(Image(name, data, imageType))
}
