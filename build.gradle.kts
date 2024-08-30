import nu.studer.gradle.jooq.JooqEdition
import org.gradle.initialization.Environment
import org.jooq.codegen.GenerationTool.main

val ktorVersion = "2.3.12"
val kotlinVersion = "2.0.20"
val logbackVersion = "1.4.14"
val postgresVersion = "42.5.1"
val h2Version = "2.1.214"
val exposedVersion = "0.52.0"
val jooqVersion = "3.18.4"

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.ktor.plugin") version "2.3.12"
    kotlin("plugin.serialization") version "2.0.20"
    id("nu.studer.jooq") version "9.0"
    id("org.flywaydb.flyway") version "9.22.3"
}

group = "example.com"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        resources {
            srcDir("config")
        }
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-resources-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    jooqGenerator("org.postgresql:postgresql:$postgresVersion")
    // Database migration
    implementation("org.flywaydb:flyway-database-postgresql:10.17.2")
}

flyway {
    url=project.properties["db.url"] as String
    user=project.properties["db.user"] as String
    password=project.properties["db.password"] as String
    baselineOnMigrate = true
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}

jooq {
    version = jooqVersion
    edition = JooqEdition.OSS

    configurations {
        create("main").apply {
            generateSchemaSourceOnCompilation = true
            jooqConfiguration.apply {
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

tasks.named("generateJooq") {
    dependsOn("flywayMigrate")
}