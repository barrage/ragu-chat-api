group = "net.barrage.llmao"

plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

gradlePlugin {
  plugins {
    create("ragu-plugin") {
      id = "net.barrage.llmao.ragu-plugin"
      displayName = "Ragu library plugin"
      description = "Gradle plugin for developing Ragu application plugins."
      tags = listOf("ragu", "llmao")
      implementationClass = "net.barrage.llmao.RaguPlugin"
    }
  }
}

dependencies {
  // Has to use raw strings because plugins and the catalog just don't play well together
  implementation("org.liquibase:liquibase-core:4.29.2")
  implementation("org.liquibase:liquibase-gradle-plugin:2.2.2")

  // https://mvnrepository.com/artifact/nu.studer.jooq/nu.studer.jooq.gradle.plugin
  implementation("nu.studer.jooq:nu.studer.jooq.gradle.plugin:10.1")
}
