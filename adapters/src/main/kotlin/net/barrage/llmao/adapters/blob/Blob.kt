package net.barrage.llmao.adapters.blob

import io.ktor.server.config.ApplicationConfig
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.string

fun initializeMinio(config: ApplicationConfig): BlobStorage<Image> {
  return MinioImageStorage(
    config.string("minio.bucket"),
    config.string("minio.endpoint"),
    config.string("minio.accessKey"),
    config.string("minio.secretKey"),
  )
}
