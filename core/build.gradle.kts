group = "net.barrage.llmao.core"

version = "0.4.0"

sourceSets {
  main { resources { srcDir("../config") } }
  test { resources { srcDir("../config") } }
}

plugins {
  `java-library`
  alias(libs.plugins.jooq)
  alias(libs.plugins.liquibase)
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
        "changelogFile" to "changelog.yaml",
        "logLevel" to "error",
        "showBanner" to "false",
        "searchPath" to "$projectDir/src/main/resources/db/migrations/core",
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

  implementation(libs.ktor.client.logging.jvm)
  implementation(libs.ktor.client.core.jvm)
  implementation(libs.ktor.client.cio.jvm)
  implementation(libs.ktor.client.content.negotiation.jvm)

  implementation(libs.ktor.serialization.kotlinx.json.jvm)

  implementation(libs.liquibase.core)
  liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("info.picocli:picocli:4.7.5")
  liquibaseRuntime(libs.postgresql)

  implementation(libs.postgresql)
  implementation(libs.jooq.kotlin.coroutines)
  jooqGenerator(libs.postgresql)

  implementation(libs.r2dbc.postgresql)
  implementation(libs.r2dbc.pool)

  implementation(libs.ktor.openapi)
  implementation(libs.ktor.swagger.ui)
  implementation(libs.schema.kenerator.core)
  implementation(libs.schema.kenerator.reflection)
  implementation(libs.schema.kenerator.swagger)

  // Auth
  implementation(libs.java.jwt)

  // Tokenizers
  implementation(libs.jtokkit)

  // Email
  implementation(libs.commons.email)

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.kotlin.test)
  testImplementation(project(":test"))
}

tasks.named("build") { dependsOn("generateJooq") }

tasks.test {
  useJUnitPlatform()
  testLogging { showStandardStreams = true }
}
