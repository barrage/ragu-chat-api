package net.barrage.llmao.app.storage

import io.ktor.server.config.ApplicationConfig
import io.ktor.util.logging.KtorSimpleLogger
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.ImageType
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.string

private const val AVATAR_PREFIX = "avatars"
private val LOG = KtorSimpleLogger("net.barrage.llmao.app.storage.AvatarStorage")

class MinioImageStorage(config: ApplicationConfig) : ImageStorage {
  private val client =
    MinioClient.builder()
      .endpoint(config.string("minio.endpoint"))
      .credentials(config.string("minio.accessKey"), config.string("minio.secretKey"))
      .build()

  private val bucket = config.string("minio.bucket")

  override fun store(image: Image) {
    val path = "$AVATAR_PREFIX/${image.name}"

    try {
      client.putObject(
        PutObjectArgs.builder()
          .bucket(bucket)
          .`object`(path)
          .stream(image.data.inputStream(), image.data.size.toLong(), -1)
          .build()
      )
    } catch (e: MinioException) {
      LOG.error("Failed to upload file", e)
      throw AppError.internal("Failed to upload file")
    }
  }

  override fun retrieve(name: String): Image? {
    val nameWithPath = "$AVATAR_PREFIX/$name"

    if (!exists(nameWithPath)) {
      return null
    }

    val contentType = ImageType.fromImageName(name) ?: throw AppError.internal("Invalid image type")

    try {
      val bytes =
        client
          .getObject(GetObjectArgs.builder().bucket(bucket).`object`(nameWithPath).build())
          .readBytes()

      return Image(name, bytes, contentType)
    } catch (e: MinioException) {
      LOG.error("Failed to download file", e)
      throw AppError.internal("Failed to download file")
    }
  }

  override fun delete(name: String) {
    val path = "$AVATAR_PREFIX/$name"

    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(path).build())
    } catch (e: MinioException) {
      LOG.error("Failed to delete file", e)
      throw AppError.internal("Failed to delete file")
    }
  }

  override fun exists(path: String): Boolean {
    return try {
      client
        .statObject(StatObjectArgs.builder().bucket(bucket).`object`(path).build())
        .contentType()

      true
    } catch (e: ErrorResponseException) {
      if (e.message.equals("Object does not exist", ignoreCase = true)) {
        return false
      } else {
        LOG.error("Failed to check object", e)
        throw AppError.internal("Failed to check object")
      }
    } catch (e: MinioException) {
      LOG.error("Failed to check object", e)
      throw AppError.internal("Failed to check object")
    }
  }
}
