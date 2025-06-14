val ktorVersion = "3.1.1"
val openApiVersion = "5.0.1"
val postgresVersion = "42.7.7"
val jooqVersion = "3.20.5"

kotlin { jvmToolchain(21) }

plugins {
  `java-library`
  kotlin("jvm") version "2.1.20"
  kotlin("plugin.serialization") version "2.1.20"
  id("org.jetbrains.dokka") version "2.0.0"
  id("nu.studer.jooq") version "10.1"
  id("com.ncorti.ktfmt.gradle") version "0.20.1"
  id("org.liquibase.gradle") version "2.2.2"
}

group = "net.barrage"

version = "0.4.0"

repositories { mavenCentral() }

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
    version = jooqVersion
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
  implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-resources-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-request-validation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

  implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

  implementation("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("info.picocli:picocli:4.7.5")
  liquibaseRuntime("org.postgresql:postgresql:$postgresVersion")

  implementation("org.postgresql:postgresql:$postgresVersion")
  implementation("org.jooq:jooq-kotlin-coroutines:$jooqVersion")
  jooqGenerator("org.postgresql:postgresql:$postgresVersion")

  implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
  implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")

  implementation("io.github.smiley4:ktor-openapi:$openApiVersion")
  implementation("io.github.smiley4:ktor-swagger-ui:$openApiVersion")
  implementation("io.github.smiley4:schema-kenerator-core:2.1.1")
  implementation("io.github.smiley4:schema-kenerator-reflection:2.1.1")
  implementation("io.github.smiley4:schema-kenerator-swagger:2.1.1")

  // Auth
  implementation("com.auth0:java-jwt:4.4.0")

  // Tokenizers
  implementation("com.knuddels:jtokkit:1.1.0")

  // Email
  implementation("org.apache.commons:commons-email:1.5")
}

ktfmt { googleStyle() }

tasks.named("build") { dependsOn("generateJooq") }

tasks.named("generateJooq") { dependsOn("liquibaseUpdate") }
