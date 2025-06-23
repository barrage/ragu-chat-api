group = "net.barrage.llmao"

version = "0.1.0"

dependencies {
  testImplementation(kotlin("test"))
  implementation(libs.infobip.api.java.client)

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

  implementation(libs.jtokkit)

  implementation(project(":core"))
}

tasks.test { useJUnitPlatform() }
