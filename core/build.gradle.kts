val ktorVersion = "3.1.1"
val openApiVersion = "5.0.1"
val logbackVersion = "1.5.13"
val postgresVersion = "42.7.4"
val jooqVersion = "3.19.16"

plugins {
    `java-library`
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.dokka") version "2.0.0"
    id("nu.studer.jooq") version "9.0"
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

    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

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

    // OpenAI
    implementation("com.aallam.openai:openai-client-jvm:4.0.1")
    implementation("com.knuddels:jtokkit:1.1.0")

    // Email
    implementation("org.apache.commons:commons-email:1.5")

    // Scheduling
    // https://mvnrepository.com/artifact/org.quartz-scheduler/quartz
    implementation("org.quartz-scheduler:quartz:2.5.0")

    // PDF
    // https://mvnrepository.com/artifact/com.itextpdf/itext-core
    implementation("com.itextpdf:itext-core:9.1.0")

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

ktfmt { googleStyle() }
