package blob

import io.ktor.util.logging.KtorSimpleLogger
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.ImageType

class MinioImageStorage(
  private val bucket: String,
  endpoint: String,
  accessKey: String,
  secretKey: String,
) : BlobStorage<Image> {
  private val client =
    MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build()

  private val log = KtorSimpleLogger("blob.MinioImageStorage")

  override fun id(): String = "minio"

  override fun store(path: String, data: Image) {
    try {
      client.putObject(
        PutObjectArgs.builder()
          .bucket(bucket)
          .`object`(path)
          .stream(data.data.inputStream(), data.data.size.toLong(), -1)
          .build()
      )
    } catch (e: MinioException) {
      log.error("Failed to upload file", e)
      throw AppError.internal("Failed to upload file")
    }
  }

  override fun retrieve(path: String): Image? {
    if (!exists(path)) {
      return null
    }

    val contentType = ImageType.fromImageName(path)

    try {
      val bytes =
        client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(path).build()).readBytes()

      return Image(bytes, contentType)
    } catch (e: MinioException) {
      log.error("Failed to download file", e)
      throw AppError.internal("Failed to download file")
    }
  }

  override fun delete(path: String) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(path).build())
    } catch (e: MinioException) {
      log.error("Failed to delete file", e)
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
        log.error("Failed to check object", e)
        throw AppError.internal("Failed to check object")
      }
    } catch (e: MinioException) {
      log.error("Failed to check object", e)
      throw AppError.internal("Failed to check object")
    }
  }
}
