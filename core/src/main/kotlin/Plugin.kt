package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.workflow.Event
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.WorkflowOutput
import org.postgresql.ds.PGSimpleDataSource

/**
 * A plugin that can be registered in the server runtime.
 *
 * All implementations must be classes with null constructors, i.e. they can be instantiated with no
 * arguments.
 */
interface Plugin : Identity {
  /**
   * Configure the plugin's state using the config file and the application state.
   *
   * This is the first plugin method called and is called once on application startup, before any
   * other configuration methods are called.
   */
  suspend fun initialize(config: ApplicationConfig, state: ApplicationState)

  /**
   * Configure the plugin's routes using Ktor.
   *
   * Called once on application startup, after request validation ([configureRequestValidation]) has
   * been installed.
   */
  fun Route.configureRoutes(state: ApplicationState) {}

  /**
   * Configure request body validation for all routes in the plugin.
   *
   * Called once on application startup, after state initialization ([initialize]) and before
   * configuring routes ([configureRoutes]).
   */
  fun RequestValidationConfig.configureRequestValidation() {}

  /**
   * Register a subtype for [WorkflowOutput] for serialization to enable sending it via emitters.
   *
   * Called once on application startup when setting up websockets.
   */
  fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {}

  /**
   * Register a subtype for [Event] for serialization to enable sending it via emitters.
   *
   * Called once on application startup when setting up websockets.
   */
  fun PolymorphicModuleBuilder<Event>.configureEventSerialization() {}

  /** Handle an event emitted by the server. All events are broadcast to all plugins. */
  suspend fun handleEvent(manager: SessionManager, event: Event) {}

  /** Used to describe the plugin to the client and let them know of its existence. */
  fun describe(settings: ApplicationSettings): PluginConfiguration

  /**
   * Run migrations for the plugin.
   *
   * Default implementation uses liquibase to find the `changelog.yaml` using the instance's
   * classloader and uses the `main` activity to run the migrations.
   *
   * The default path for the changelog search path is
   * `src/main/resources/db/migrations/{plugin_id_lowercased}`.
   *
   * Called once on application startup, after state initialization ([initialize]).
   */
  fun migrate(config: ApplicationConfig) {
    val url = config.property("db.url").getString()
    val user = config.property("db.user").getString()
    val pw = config.property("db.password").getString()
    val path = id().lowercase()
    val changeLogFile = "db/migrations/$path/changelog.yaml"
    val dataSource =
      PGSimpleDataSource().apply {
        setURL(url)
        this.user = user
        this.password = pw
      }

    dataSource.connection.use { connection ->
      val liquibase =
        Liquibase(
          changeLogFile,
          ClassLoaderResourceAccessor(this::class.java.classLoader),
          JdbcConnection(connection),
        )
      liquibase.update("main")
    }
  }
}

@Serializable
data class PluginConfiguration(
  val id: String,
  val description: String,
  val settings: Map<String, String?>,
)

/** Keeps the state of optional modules and is used to configure plugins on app startup. */
object Plugins {
  /** Plugin registry. */
  val plugins: MutableMap<String, Plugin> = HashMap()

  private val log = KtorSimpleLogger("net.barrage.core.Plugins")

  /**
   * Add a plugin to the registry.
   *
   * TODO: Currently called via feature flags, should be called via annotation processor.
   */
  fun register(plugin: Plugin) {
    if (plugins.contains(plugin.id())) {
      log.warn("Plugin {} already registered, skipping", plugin.id())
    } else {
      log.info("Registering plugin {}", plugin.id())
    }
    plugins.put(plugin.id(), plugin)
  }

  /** Run database migrations for all plugins. */
  fun migrate(config: ApplicationConfig) {
    log.info("Running plugin migrations")
    plugins.values.forEachIndexed { i, plugin ->
      log.info(
        "Running database migrations for plugin {} ({}/{})",
        plugin.id(),
        i + 1,
        plugins.size,
      )
      plugin.migrate(config)
    }
  }

  /** List the configuration of all registered plugins. */
  fun list(settings: ApplicationSettings): List<PluginConfiguration> =
    plugins.values.map { it.describe(settings) }

  /**
   * Configure all plugins registered in this object.
   *
   * Called once on application startup and is the first plugin configuration method called.
   */
  suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    log.info("Initializing {} total plugins", plugins.size)
    for (plugin in plugins.values) {
      plugin.initialize(config, state)
      log.debug("Initialized plugin {}", plugin.id())
    }
  }

  /** Emit an event to all the plugins in the registry. */
  suspend fun emitEvent(manager: SessionManager, event: Event) {
    log.info("Emitting event to plugins: {}", event)
    for (plugin in plugins.values) {
      plugin.handleEvent(manager, event)
    }
  }

  /**
   * Register all plugin routes.
   *
   * Called once on application startup.
   */
  fun Route.configureRoutes(state: ApplicationState) {
    for (plugin in plugins.values) {
      with(plugin) { configureRoutes(state) }
    }
    log.debug("Plugin routes successfully configured")
  }

  /**
   * Register request validation for all plugins using their configuration.
   *
   * Called once on application startup.
   */
  fun RequestValidationConfig.configureRequestValidation() {
    for (plugin in plugins.values) {
      with(plugin) { configureRequestValidation() }
    }
    log.debug("Plugin request validation successfully configured")
  }

  /**
   * Register subtypes of [WorkflowOutput] for all plugins.
   *
   * Called once on application startup.
   */
  fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    for (plugin in plugins.values) {
      with(plugin) { configureOutputSerialization() }
    }
    log.debug("Plugin output serializers configured")
  }

  fun PolymorphicModuleBuilder<Event>.configureEventSerialization() {
    for (plugin in plugins.values) {
      with(plugin) { configureEventSerialization() }
    }
    log.debug("Plugin event serializers configured")
  }

  /** Clear all plugins from the registry. */
  fun reset() {
    plugins.clear()
  }
}
