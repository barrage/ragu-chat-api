package net.barrage.llmao.test

import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.wait.strategy.Wait

class TestMinio {
  val container: MinIOContainer =
    MinIOContainer("minio/minio:latest")
      .withUserName("testMinio")
      .withPassword("testMinio")
      .waitingFor(Wait.defaultWaitStrategy())

  val client: MinioClient

  init {
    container.start()

    client =
      MinioClient.builder().endpoint(container.s3URL).credentials("testMinio", "testMinio").build()

    client.makeBucket(MakeBucketArgs.builder().bucket("test").build())
  }
}
