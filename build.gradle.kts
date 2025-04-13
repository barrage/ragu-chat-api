import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import nu.studer.gradle.jooq.JooqEdition
import org.jooq.meta.jaxb.Logging
import org.liquibase.gradle.LiquibaseTask
import org.testcontainers.containers.PostgreSQLContainer

val ktorVersion = "3.1.1"
val openApiVersion = "5.0.1"
val logbackVersion = "1.5.8"
val postgresVersion = "42.7.4"
val jooqVersion = "3.19.16"

plugins {
  kotlin("jvm") version "2.1.20"
  kotlin("plugin.serialization") version "2.1.20"
  id("org.jetbrains.dokka") version "2.0.0"
  id("io.ktor.plugin") version "3.1.1"
  id("nu.studer.jooq") version "9.0"
  id("com.ncorti.ktfmt.gradle") version "0.20.1"
  id("com.gradleup.shadow") version "8.3.3"
  id("org.liquibase.gradle") version "2.2.2"
}

group = "net.barrage"

version = "0.2.0"

application {
  mainClass.set("io.ktor.server.netty.EngineMain")
  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories { mavenCentral() }

sourceSets { main { resources { srcDir("config") } } }

// We need these during the build step because of JOOQ class generation.
buildscript {
  dependencies {
    classpath("org.testcontainers:postgresql:1.20.2")
    classpath("org.liquibase:liquibase-core:4.29.2")
  }
}

ktor { fatJar { archiveFileName.set("llmao.jar") } }

dependencies {
  implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-resources-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
  implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-websockets:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

  implementation("io.ktor:ktor-client-logging:$ktorVersion")
  implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

  implementation("ch.qos.logback:logback-classic:$logbackVersion")
  implementation("org.postgresql:postgresql:$postgresVersion")
  implementation("org.liquibase:liquibase-core:4.29.2")
  implementation("org.jooq:jooq-kotlin-coroutines:$jooqVersion")

  implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
  implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")

  implementation("io.github.smiley4:ktor-openapi:$openApiVersion")
  implementation("io.github.smiley4:ktor-swagger-ui:$openApiVersion")
  implementation("io.github.smiley4:schema-kenerator-core:2.1.1")
  implementation("io.github.smiley4:schema-kenerator-reflection:2.1.1")
  implementation("io.github.smiley4:schema-kenerator-swagger:2.1.1")
  implementation("com.auth0:java-jwt:4.4.0")

  // Infobip
  implementation("com.infobip:infobip-api-java-client:4.4.0")

  // AI
  implementation("com.aallam.openai:openai-client:4.0.1")
  implementation("io.ktor:ktor-client-okhttp")
  implementation("com.knuddels:jtokkit:1.1.0")

  // Tests
  testImplementation("org.testcontainers:postgresql:1.20.2")
  testImplementation("org.testcontainers:weaviate:1.20.2")

  // https://mvnrepository.com/artifact/org.wiremock.integrations.testcontainers/wiremock-testcontainers-module
  testImplementation("org.wiremock:wiremock:3.12.1")
  testImplementation("org.wiremock.extensions:wiremock-jwt-extension-standalone:0.2.0")
  testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
  testImplementation(
    "org.jetbrains.kotlin:kotlin-test:2.1.20"
  ) // Has to be the same as the kotlin version

  testImplementation("org.liquibase:liquibase-core:4.29.2")
  testImplementation("org.postgresql:postgresql:$postgresVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testImplementation("org.testcontainers:minio:1.20.4")

  // Database communication
  liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
  liquibaseRuntime("ch.qos.logback:logback-core:1.5.13")
  liquibaseRuntime("ch.qos.logback:logback-classic:1.4.12")
  liquibaseRuntime("info.picocli:picocli:4.7.5")
  liquibaseRuntime("javax.xml.bind:jaxb-api:2.2.4")
  liquibaseRuntime("org.postgresql:postgresql:$postgresVersion")
  jooqGenerator("org.postgresql:postgresql:$postgresVersion")
  implementation("io.ktor:ktor-server-auth:$ktorVersion")
  implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
  // Weaviate client
  implementation("io.weaviate:client:4.8.3")

  // MinIO
  implementation("io.minio:minio:8.5.15")
}

// Ensures all properties are set in gradle.properties and throws a user friendly error if not
val env =
  project.properties["env"] as? String
    ?: throw Exception("`env` variable not set; check gradle.properties")

var dbUrl =
  project.properties["db.url"] as? String
    ?: throw Exception("`db.url` variable not set; check gradle.properties")

var dbUser =
  project.properties["db.user"] as? String
    ?: throw Exception("`db.user` variable not set; check gradle.properties")

var dbPassword =
  project.properties["db.password"] as? String
    ?: throw Exception("`db.password` variable not set; check gradle.properties")

val dbChangelog = "src/main/resources/db/changelog.yaml"

var tempDb: PostgreSQLContainer<*>? = null

// When running in the cloud (gitlab runner, etc) we need to use a temporary test container
// where we'll run migrations so JOOQ can grab the schema and do the codegen.
if (env != "local") {
  tempDb =
    PostgreSQLContainer<Nothing>("postgres:latest")
      .apply {
        withDatabaseName("test")
        withUsername("test")
        withPassword("test")
      }
      .waitingFor(org.testcontainers.containers.wait.strategy.Wait.defaultWaitStrategy())
  tempDb!!.start()
  dbUrl = tempDb!!.jdbcUrl
  dbUser = "test"
  dbPassword = "test"
}

tasks.register("stopBuildDb") {
  doLast {
    tempDb?.stop()
    tempDb = null
  }
}

liquibase {
  activities.register("main") {
    arguments =
      mapOf(
        "url" to dbUrl,
        "username" to dbUser,
        "password" to dbPassword,
        "changelogFile" to dbChangelog,
        "logLevel" to "error",
        "showBanner" to "false",
      )
  }
  runList = "main"
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
          url = dbUrl
          user = dbUser
          password = dbPassword
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

ktfmt { googleStyle() }

tasks.test {
  useJUnitPlatform() // This is essential for JUnit 5
  testLogging { showStandardStreams = true }
}

tasks.named("build") { dependsOn("generateJooq") }

tasks.named("generateJooq") { dependsOn("liquibaseUpdate") }

tasks.matching { it.name != "stopBuildDb" }.all { finalizedBy("stopBuildDb") }

tasks.withType<Jar> { exclude("application.conf") }

tasks.withType<ShadowJar> { mergeServiceFiles() }

tasks.withType<LiquibaseTask> {
  logging.captureStandardError(LogLevel.QUIET)
  logging.captureStandardOutput(LogLevel.QUIET)
}

tasks.named("liquibaseUpdate") {
  logging.captureStandardError(LogLevel.QUIET)
  logging.captureStandardOutput(LogLevel.QUIET)
}
