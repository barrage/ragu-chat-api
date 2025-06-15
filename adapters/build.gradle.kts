group = "net.barrage.llmao.adapters"

version = "0.4.0"

plugins { `java-library` }

dependencies {
  implementation(libs.ktor.client.core.jvm)
  implementation(libs.ktor.client.cio.jvm)
  implementation(libs.openai.client)
  implementation(libs.weaviate.client)
  implementation(libs.minio.client)

  implementation(project(":core"))
}
