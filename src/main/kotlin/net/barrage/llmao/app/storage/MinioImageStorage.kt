package net.barrage.llmao.app.storage

import io.ktor.server.config.*
import io.ktor.util.logging.*
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import net.barrage.llmao.core.models.Image
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.string

private val LOG = KtorSimpleLogger("net.barrage.llmao.app.storage.AvatarStorage")

class MinioImageStorage(config: ApplicationConfig) : ImageStorage {
  private val client =
    MinioClient.builder()
      .endpoint(config.string("minio.endpoint"))
      .credentials(config.string("minio.accessKey"), config.string("minio.secretKey"))
      .build()

  private val bucket = config.string("minio.bucket")

  init {
    try {
      if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        LOG.error("Bucket does not exist")
        throw AppError.internal("Bucket does not exist")
      }
    } catch (e: MinioException) {
      LOG.error("Failed to connect to Minio", e)
      throw AppError.internal("Failed to connect to Minio")
    }
  }

  override fun store(id: KUUID, imageFormat: String, bytes: ByteArray): Image {
    delete(id)

    val path = "avatars/$id.$imageFormat"

    try {
      LOG.debug("Uploading file to Minio")
      client.putObject(
        PutObjectArgs.builder()
          .bucket(bucket)
          .`object`(path)
          .stream(bytes.inputStream(), bytes.size.toLong(), -1)
          .build()
      )
    } catch (e: MinioException) {
      LOG.error("Failed to upload file", e)
      throw AppError.internal("Failed to upload file")
    }

    val contentType =
      when (imageFormat) {
        "jpg" -> "image/jpeg"
        else -> "image/png"
      }

    return Image(contentType, bytes)
  }

  override fun retrieve(id: KUUID): Image? {
    val path = formatPath(id) ?: return null
    val contentType =
      when (path) {
        "avatars/$id.jpg" -> "image/jpeg"
        "avatars/$id.png" -> "image/png"
        else -> return null
      }

    try {
      LOG.debug("Downloading file from Minio")
      val bytes =
        client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(path).build()).readBytes()

      return Image(contentType, bytes)
    } catch (e: MinioException) {
      LOG.error("Failed to download file", e)
      throw AppError.internal("Failed to download file")
    }
  }

  override fun delete(id: KUUID) {
    val path = formatPath(id) ?: return
    try {
      LOG.debug("Deleting file from Minio")
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(path).build())
    } catch (e: MinioException) {
      LOG.error("Failed to delete file", e)
      throw AppError.internal("Failed to delete file")
    }
  }

  override fun exists(path: String): Boolean {
    return try {
      LOG.debug("Checking if object exists in Minio")
      client
        .statObject(StatObjectArgs.builder().bucket(bucket).`object`(path).build())
        .contentType()

      true
    } catch (e: ErrorResponseException) {
      if (e.message.equals("Object does not exist", ignoreCase = true)) {
        return false
      } else {
        throw AppError.internal("Failed to check object")
      }
    } catch (e: MinioException) {
      LOG.error("Failed to check object", e)
      throw AppError.internal("Failed to check object")
    }
  }

  override fun formatPath(id: KUUID): String? {
    return if (exists("avatars/$id.jpg")) {
      "avatars/$id.jpg"
    } else if (exists("avatars/$id.png")) {
      "avatars/$id.png"
    } else {
      null
    }
  }
}
