# Ragu plugin

Gradle plugin for developing Ragu application plugins.

## Usage

Add the plugin to your `build.gradle.kts` file:

```kotlin
plugins {
    id("net.barrage.llmao.ragu-plugin")
}
```

When applied, the following gradle plugins are automatically applied:

- `java-library`
- `org.liquibase.gradle`
- `nu.studer.jooq`

Configure the plugin:

```kotlin
ragu {
    jooqInclude = "my_table | my_other_table"
    jooqPackage = "my.plugin.namespace"
    liquibaseMigrationsPath = "src/main/resource/path/to/migrations"
}
```

| Property                  | Description                                                                                   | Default value                                    |
|---------------------------|-----------------------------------------------------------------------------------------------|--------------------------------------------------|
| `jooqInclude`             | A java regular expression that tells Jooq which tables to generate Java / Kotlin classes for. | -                                                |
| `jooqPackage`             | The namespace in which the Jooq classes will be generated.                                    | `projectGroup.projectName`                       |
| `liquibaseMigrationsPath` | Path to the directory containing the `changelog.yaml` file for Liquibase migrations.          | `src/main/resources/db/migrations/{projectName}` |

## Tasks

The plugin does not add any tasks, it only configures the `liquibase` and `jooq` plugins so you can get on with writing
your Ragu plugin.
