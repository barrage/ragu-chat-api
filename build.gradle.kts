import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask

group = "net.barrage.llmao"

repositories { mavenCentral() }

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.dokka)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.ktor)
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "com.ncorti.ktfmt.gradle")

  repositories { mavenCentral() }
  kotlin { jvmToolchain(21) }
  ktfmt { googleStyle() }

  tasks.withType<KtfmtCheckTask> { enabled = false }
}

// sourceSets { main { resources { srcDir("config") } } }
