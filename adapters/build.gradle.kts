import org.gradle.kotlin.dsl.invoke

val ktorVersion = "3.1.1"

kotlin { jvmToolchain(21) }

plugins {
  `java-library`
  kotlin("jvm") version "2.1.20"
  kotlin("plugin.serialization") version "2.1.20"
  id("org.jetbrains.dokka") version "2.0.0"
  id("com.ncorti.ktfmt.gradle") version "0.20.1"
}

group = "net.barrage"

version = "0.4.0"

repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-resources-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-request-validation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-auth:$ktorVersion")
  implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")

  implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

  // OpenAI
  implementation("com.aallam.openai:openai-client-jvm:4.0.1")
  // implementation("com.knuddels:jtokkit:1.1.0")

  // Weaviate client
  implementation("io.weaviate:client:4.8.3")

  // MinIO
  implementation("io.minio:minio:8.5.15")

  implementation(project(":core"))
}

ktfmt { googleStyle() }
