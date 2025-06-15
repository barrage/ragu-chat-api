group = "net.barrage.llmao.core"

version = "0.4.0"

plugins {
  `java-library`
  id("nu.studer.jooq") version "10.1"
  id("org.liquibase.gradle") version "2.2.2"
}

// We need these during the build step because of JOOQ class generation.
buildscript {
  dependencies {
    classpath("org.testcontainers:postgresql:1.20.2")
    classpath("org.liquibase:liquibase-core:4.29.2")
  }
}

liquibase {
  activities.register("main") {
    arguments =
      mapOf(
        "url" to project.findProperty("db.url") as String,
        "username" to project.findProperty("db.user") as String,
        "password" to project.findProperty("db.password") as String,
        "changelogFile" to "src/main/resources/db/changelog.yaml",
        "logLevel" to "error",
        "showBanner" to "false",
      )
  }
  runList = "main"
}

jooq {
  extensions.configure(nu.studer.gradle.jooq.JooqExtension::class.java) {
    version = libs.versions.jooq.get()
    edition = nu.studer.gradle.jooq.JooqEdition.OSS

    configurations.create("main") {
      generateSchemaSourceOnCompilation = true
      jooqConfiguration.apply {
        logging = org.jooq.meta.jaxb.Logging.ERROR
        jdbc.apply {
          url = project.findProperty("db.url") as String
          user = project.findProperty("db.user") as String
          password = project.findProperty("db.password") as String
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.postgres.PostgresDatabase"
            inputSchema = "public"
            excludes = "databasechangelog | databasechangeloglock"
          }
          generate.apply {
            isDeprecated = false
            isKotlinSetterJvmNameAnnotationsOnIsPrefix = true
            isPojosAsKotlinDataClasses = true
            isKotlinNotNullRecordAttributes = true
            isFluentSetters = true
            isRoutines = false
          }
          target.apply {
            packageName = "net.barrage.llmao"
            directory = "build/generated-src/jooq"
          }
          strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
        }
      }
    }
  }
}

dependencies {
  implementation(rootProject.libs.ktor.server.core.jvm)
  implementation(rootProject.libs.ktor.server.auth.jvm)
  implementation(rootProject.libs.ktor.server.auth.jwt)
  implementation(rootProject.libs.ktor.server.resources.jvm)
  implementation(rootProject.libs.ktor.server.content.negotiation.jvm)
  implementation(rootProject.libs.ktor.server.request.validation.jvm)
  implementation(rootProject.libs.ktor.server.cors.jvm)
  implementation(rootProject.libs.ktor.server.cio.jvm)
  implementation(rootProject.libs.ktor.server.websockets.jvm)
  implementation(rootProject.libs.ktor.server.status.pages.jvm)

  implementation(rootProject.libs.ktor.client.logging.jvm)
  implementation(rootProject.libs.ktor.client.core.jvm)
  implementation(rootProject.libs.ktor.client.cio.jvm)
  implementation(rootProject.libs.ktor.client.content.negotiation.jvm)

  implementation(rootProject.libs.ktor.serialization.kotlinx.json.jvm)

  implementation(rootProject.libs.liquibase.core)
  liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("info.picocli:picocli:4.7.5")
  liquibaseRuntime(rootProject.libs.postgresql)

  implementation(rootProject.libs.postgresql)
  implementation(rootProject.libs.jooq.kotlin.coroutines)
  jooqGenerator(rootProject.libs.postgresql)

  implementation(rootProject.libs.r2dbc.postgresql)
  implementation(rootProject.libs.r2dbc.pool)

  implementation(rootProject.libs.ktor.openapi)
  implementation(rootProject.libs.ktor.swagger.ui)
  implementation(rootProject.libs.schema.kenerator.core)
  implementation(rootProject.libs.schema.kenerator.reflection)
  implementation(rootProject.libs.schema.kenerator.swagger)

  // Auth
  implementation(rootProject.libs.java.jwt)

  // Tokenizers
  implementation(rootProject.libs.jtokkit)

  // Email
  implementation(rootProject.libs.commons.email)
}

tasks.named("build") { dependsOn("generateJooq") }
