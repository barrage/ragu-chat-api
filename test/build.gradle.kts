group = "net.barrage.llmao"

version = "0.1.0"

plugins { `java-library` }

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.client.core.jvm)
  implementation(libs.ktor.client.content.negotiation.jvm)
  implementation(libs.ktor.server.test.host)
  implementation(libs.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.testcontainers.postgresql)
  implementation(libs.testcontainers.weaviate)
  implementation(libs.testcontainers.minio)
  implementation(libs.wiremock)
  implementation(libs.wiremock.jwt.extension)
  implementation(libs.postgresql)
  implementation(libs.liquibase.core)
  implementation(libs.jooq.kotlin.coroutines)
  implementation(libs.r2dbc.postgresql)
  implementation(libs.r2dbc.pool)
  implementation(libs.weaviate.client)
  implementation(libs.minio.client)
  implementation(libs.junit.jupiter.api)

  implementation(project(":core"))
  implementation(project(":adapters"))
}
