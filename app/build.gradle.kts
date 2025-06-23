group = "net.barrage.llmao.app"

version = "0.4.0"

plugins { alias(libs.plugins.ktor) }

application {
  mainClass.set("io.ktor.server.cio.EngineMain")
  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor { fatJar { archiveFileName.set("llmao.jar") } }

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.server.auth.jvm)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.server.resources.jvm)
  implementation(libs.ktor.server.content.negotiation.jvm)
  implementation(libs.ktor.server.request.validation.jvm)
  implementation(libs.ktor.server.cors.jvm)
  implementation(libs.ktor.server.cio.jvm)
  implementation(libs.ktor.server.websockets.jvm)
  implementation(libs.ktor.server.status.pages.jvm)

  implementation(libs.ktor.client.logging.jvm)
  implementation(libs.ktor.client.core.jvm)
  implementation(libs.ktor.client.cio.jvm)
  implementation(libs.ktor.client.content.negotiation.jvm)

  implementation(libs.ktor.serialization.kotlinx.json.jvm)

  implementation(libs.logback.classic)

  implementation(libs.ktor.openapi)
  implementation(libs.ktor.swagger.ui)
  implementation(libs.schema.kenerator.core)
  implementation(libs.schema.kenerator.reflection)
  implementation(libs.schema.kenerator.swagger)

  implementation(libs.java.jwt)
  implementation(libs.jooq.kotlin.coroutines)

  // Tests
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.weaviate)
  testImplementation(libs.testcontainers.minio)

  testImplementation(libs.wiremock)
  testImplementation(libs.wiremock.jwt.extension)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test)

  testImplementation(libs.liquibase.core)
  testImplementation(libs.postgresql)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.api)

  implementation(project(":core"))
  implementation(project(":adapters"))
  implementation(project(":plugins:chat"))
  implementation(project(":plugins:jirakira"))
  implementation(project(":plugins:bonvoyage"))
  implementation(project(":plugins:hgk"))

  testImplementation(project(":test"))
}

// Ensures all properties are set in gradle.properties and throws a user friendly error if not
// val env =
//  project.properties["env"] as? String
//    ?: throw Exception("`env` variable not set; check gradle.properties")
//
// var dbUrl =
//  project.properties["db.url"] as? String
//    ?: throw Exception("`db.url` variable not set; check gradle.properties")
//
// var dbUser =
//  project.properties["db.user"] as? String
//    ?: throw Exception("`db.user` variable not set; check gradle.properties")
//
// var dbPassword =
//  project.properties["db.password"] as? String
//    ?: throw Exception("`db.password` variable not set; check gradle.properties")

val dbChangelog = "src/main/resources/db/changelog.yaml"

// var tempDb: PostgreSQLContainer<*>? = null

// When running in the cloud (gitlab runner, etc) we need to use a temporary test container
// where we'll run migrations so JOOQ can grab the schema and do the codegen.
// if (project.findProperty("env") != "local") {
//  tempDb =
//    PostgreSQLContainer<Nothing>("postgres:latest")
//      .apply {
//        withDatabaseName("test")
//        withUsername("test")
//        withPassword("test")
//      }
//      .waitingFor(org.testcontainers.containers.wait.strategy.Wait.defaultWaitStrategy())
//  tempDb!!.start()
//  dbUrl = tempDb!!.jdbcUrl
//  dbUser = "test"
//  dbPassword = "test"
// }

// tasks.register("stopBuildDb") {
//  doLast {
//    tempDb?.stop()
//    tempDb = null
//  }
// }

tasks.test {
  useJUnitPlatform() // Essential for JUnit 5
  testLogging { showStandardStreams = project.findProperty("env") == "local" }
}

// tasks.withType<Jar> { exclude("application.conf") }
