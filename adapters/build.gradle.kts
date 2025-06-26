group = "net.barrage.llmao"

version = "0.1.0"

plugins { `java-library` }

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.client.core.jvm)
  implementation(libs.ktor.client.cio.jvm)
  implementation(libs.openai.client)
  implementation(libs.weaviate.client)
  implementation(libs.minio.client)

  implementation(project(":core"))
}
