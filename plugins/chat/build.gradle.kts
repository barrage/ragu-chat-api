group = "net.barrage.llmao"

version = "0.5.0"

plugins { id("net.barrage.llmao.ragu-plugin") }

ragu {
  jooqInclude =
    "agents | agent_configurations | agent_collections | agent_tools | agent_permissions | chats | whats_app_numbers"
  jooqPackage = "net.barrage.llmao.chat"
}

dependencies {
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.openapi)
  implementation(libs.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.bundles.postgres)

  implementation(libs.jtokkit)

  implementation(libs.infobip.api.java.client)

  implementation(project(":core"))

  testImplementation(project(":test"))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotlin.test)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.logback.classic)
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    showStandardStreams = true
    events("passed", "skipped", "failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
