group = "net.barrage.llmao"

version = "0.5.0"

plugins { alias(libs.plugins.ktor) }

application {
  mainClass.set("io.ktor.server.cio.EngineMain")
  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

sourceSets { main { resources.srcDir("../config") } }

ktor { fatJar { archiveFileName.set("llmao.jar") } }

dependencies {
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.server.cio.jvm)
  implementation(libs.logback.classic)

  implementation(project(":core"))
  implementation(project(":adapters"))
  implementation(project(":plugins:chat"))
  implementation(project(":plugins:jirakira"))
  implementation(project(":plugins:bonvoyage"))
  implementation(project(":plugins:hgk"))
}
