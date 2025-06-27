group = "net.barrage.llmao"

version = "0.5.0"

plugins { id("net.barrage.llmao.ragu-plugin") }

sourceSets {
  main {
    java.srcDir("src/main/jooq")
    kotlin.srcDir("src/main/jooq")
  }

  test { resources.srcDir("../config") }
}

ragu {
  jooqInclude =
    "application_settings | message_groups | messages | message_attachments | message_group_evaluations | token_usage"
  jooqPackage = "net.barrage.llmao.core"
}

dependencies {
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.openapi)
  implementation(libs.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.bundles.postgres)

  // Auth
  implementation(libs.liquibase.core)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.java.jwt)

  // Tokenizers
  implementation(libs.jtokkit)

  // Email
  implementation(libs.commons.email)

  testImplementation(project(":test"))
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.logback.classic)
}

tasks.test {
  useJUnitPlatform()
  testLogging { showStandardStreams = true }
}
