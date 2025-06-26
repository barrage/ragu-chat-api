group = "net.barrage.llmao"

version = "0.1.0"

plugins { id("net.barrage.llmao.ragu-plugin") }

ragu {
  jooqInclude = "jirakira_workflows | jira_worklog_attributes | jira_api_keys"
  jooqPackage = "net.barrage.llmao.jirakira"
}

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.server.auth.jvm)
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.openapi)
  implementation(libs.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.bundles.postgres)

  implementation(libs.jtokkit)

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
