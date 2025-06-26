group = "net.barrage.llmao.plugins.bonvoyage"

version = "0.1.0"

plugins { id("net.barrage.llmao.ragu-plugin") }

ragu {
  jooqInclude = "bonvoyage_.*"
  jooqPackage = "net.barrage.llmao.bonvoyage"
}

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.server.auth.jvm)
  implementation(libs.ktor.server.request.validation.jvm)
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.openapi)
  implementation(libs.ktor.serialization.kotlinx.json.jvm)

  implementation(libs.bundles.postgres)

  implementation(libs.jtokkit)

  implementation(libs.commons.email)
  implementation(libs.itext.core)
  implementation(project(":core"))

  testImplementation(project(":test"))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.logback.classic)
}
