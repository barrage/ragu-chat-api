package net.barrage.llmao

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.liquibase.gradle.LiquibaseExtension

/** A gradle plugin for developing Ragu application plugins, i.e. libraries for the Ragu runtime. */
class RaguPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.apply(JavaLibraryPlugin::class.java)
    project.plugins.apply("org.liquibase.gradle")
    project.plugins.apply("nu.studer.jooq")

    val ext = project.extensions.create<RaguPluginConfig>("ragu")

    project.afterEvaluate {
      project.dependencies.apply {
        add("implementation", "org.liquibase:liquibase-core:4.29.2")
        add("liquibaseRuntime", "org.liquibase:liquibase-core:4.29.2")
        add("liquibaseRuntime", "org.postgresql:postgresql:42.7.7")
        add("liquibaseRuntime", "info.picocli:picocli:4.7.5")
        add("jooqGenerator", "org.postgresql:postgresql:42.7.7")
      }

      if (!ext.jooqInclude.isPresent) {
        error("Ragu plugin configuration requires the `jooqInclude` property to be set")
      }

      // Only now is it safe to read values from the extension
      val searchPath =
        ext.liquibaseMigrationsPath.getOrElse(
          "${project.projectDir}/src/main/resources/db/migrations/${project.name.lowercase()}"
        )

      // Configure Liquibase
      project.extensions.configure<LiquibaseExtension> {
        activities.register("main") {
          arguments =
            mapOf(
              "url" to project.findProperty("db.url") as String,
              "username" to project.findProperty("db.user") as String,
              "password" to project.findProperty("db.password") as String,
              "changelogFile" to "changelog.yaml",
              "logLevel" to "error",
              "showBanner" to "false",
              "searchPath" to searchPath,
            )
        }
        runList = "main"
      }

      project.extensions.configure<nu.studer.gradle.jooq.JooqExtension> {
        version.set("3.20.5")
        edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)
        configurations.create("main") {
          generateSchemaSourceOnCompilation.set(true)
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
                includes = ext.jooqInclude.get()
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
                packageName = ext.jooqPackage.getOrElse(project.group.toString())
                directory = "${project.projectDir}/src/main/jooq"
              }
              strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
            }
          }
        }
      }
    }
  }
}

interface RaguPluginConfig {
  /**
   * A java regular expression that tells Jooq which tables to generate Java / Kotlin classes for.
   *
   * Example: `"my_table | my_other_table"`
   *
   * Required.
   */
  val jooqInclude: Property<String>

  /** The namespace in which the Jooq classes will be generated. */
  val jooqPackage: Property<String>

  /**
   * Path to the directory containing the `changelog.yaml` file for Liquibase migrations.
   *
   * Defaults to
   *
   * `src/main/resources/db/migrations/{projectName}`
   *
   * where project name is the name of the gradle project.
   */
  val liquibaseMigrationsPath: Property<String>
}
