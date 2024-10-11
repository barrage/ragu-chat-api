import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import nu.studer.gradle.jooq.JooqEdition
import org.jooq.meta.jaxb.Logging

val ktorVersion = "2.3.12"
val kotlinVersion = "2.0.20"
val logbackVersion = "1.5.8"
val postgresVersion = "42.7.4"
val h2Version = "2.1.214"
val exposedVersion = "0.52.0"
val jooqVersion = "3.19.11"
val flywayVersion = "10.17.3"
val junitVersion = "5.8.1"

plugins {
  kotlin("jvm") version "2.0.20"
  id("io.ktor.plugin") version "2.3.12"
  kotlin("plugin.serialization") version "2.0.20"
  id("nu.studer.jooq") version "9.0"
  id("com.ncorti.ktfmt.gradle") version "0.20.1"
  id("com.gradleup.shadow") version "8.3.3"
  id("org.liquibase.gradle") version "3.0.1"
}

group = "net.barrage"

version = "0.0.1"

application {
  mainClass.set("io.ktor.server.netty.EngineMain")

  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories { mavenCentral() }

sourceSets { main { resources { srcDir("config") } } }

buildscript { dependencies { dependencies { classpath("org.liquibase:liquibase-core:4.29.2") } } }

ktor { fatJar { archiveFileName.set("llmao.jar") } }

dependencies {
  implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-resources-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
  implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
  implementation("io.ktor:ktor-server-websockets:$ktorVersion")

  implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

  implementation("ch.qos.logback:logback-classic:$logbackVersion")
  implementation("org.postgresql:postgresql:$postgresVersion")
  implementation("io.github.smiley4:ktor-swagger-ui:3.3.1")
  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
  implementation("com.auth0:java-jwt:4.4.0")

  // AI
  implementation("com.aallam.openai:openai-client:3.8.2")
  implementation("io.ktor:ktor-client-okhttp")
  implementation("com.knuddels:jtokkit:1.1.0")

  // Tests
  testImplementation("org.testcontainers:postgresql:1.20.2")
  testImplementation("org.testcontainers:weaviate:1.20.2")
  testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
  testImplementation("org.liquibase:liquibase-core:4.29.2")
  testImplementation("org.postgresql:postgresql:$postgresVersion")

  // Error handling
  implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

  // Database communication
  liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("ch.qos.logback:logback-core:1.2.3")
  liquibaseRuntime("ch.qos.logback:logback-classic:1.2.3")
  liquibaseRuntime("info.picocli:picocli:4.7.5")
  liquibaseRuntime("javax.xml.bind:jaxb-api:2.2.4")
  liquibaseRuntime("org.postgresql:postgresql:$postgresVersion")
  jooqGenerator("org.postgresql:postgresql:$postgresVersion")

  // Weaviate client
  implementation("io.weaviate:client:4.8.3")
}

liquibase {
  activities.register("main") {
    arguments =
      mapOf(
        "url" to project.properties["db.url"] as String,
        "username" to project.properties["db.user"] as String,
        "password" to project.properties["db.password"] as String,
        "changelogFile" to "src/main/resources/db/changelog.xml",
        "logLevel" to "info",
      )
  }
}

jooq {
  version = jooqVersion
  edition = JooqEdition.OSS

  configurations {
    create("main").apply {
      generateSchemaSourceOnCompilation = true
      jooqConfiguration.apply {
        logging = Logging.ERROR
        jdbc.apply {
          url = project.properties["db.url"] as String
          user = project.properties["db.user"] as String
          password = project.properties["db.password"] as String
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
            isFluentSetters = true
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

ktfmt { googleStyle() }

tasks.named("generateJooq") { dependsOn("update") }

tasks.withType<Jar> { exclude("application.yaml") }

tasks.withType<ShadowJar> { mergeServiceFiles() }
