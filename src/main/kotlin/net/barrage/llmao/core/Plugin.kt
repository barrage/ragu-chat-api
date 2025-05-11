package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.WorkflowOutput

/**
 * Defines a plugin that can be registered with the application.
 *
 * All plugins must be classes that can be instantiated with no arguments.
 */
interface Plugin : Identity {
  /**
   * Configure the plugin's state using the config file and the application state.
   *
   * This is the first plugin method called and is called once on application startup, before any
   * other configuration methods are called.
   */
  suspend fun configureState(config: ApplicationConfig, state: ApplicationState)

  /**  */
  fun Route.configureRoutes(state: ApplicationState) {}

  fun RequestValidationConfig.configureRequestValidation() {}

  /**
   * Register a subtype for [WorkflowOutput] for serialization to enable sending it via emitters.
   */
  fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {}

  suspend fun handleEvent(manager: SessionManager, event: Event) {}
}

/** Keeps the state of optional modules and is used to configure plugins on app startup. */
class Plugins {
  /** Plugin registry. */
  val plugins: MutableSet<Plugin> = HashSet()

  /**
   * Add a plugin to the registry.
   *
   * TODO: Currently called via feature flags, should be called via annotation processor.
   */
  fun register(plugin: Plugin) =
    if (plugins.contains(plugin))
      throw AppError.internal("Invalid configuration; Plugin already registered: ${plugin.id()}")
    else plugins.add(plugin)

  /**
   * Configure all plugins registered in this object.
   *
   * Called once on application startup and is the first plugin configuration method called.
   */
  suspend fun configureState(config: ApplicationConfig, state: ApplicationState) {
    for (plugin in plugins) {
      plugin.configureState(config, state)
    }
  }

  /** Emit an event to all the plugins in the registry. */
  suspend fun emitEvent(manager: SessionManager, event: Event) {
    for (plugin in plugins) {
      plugin.handleEvent(manager, event)
    }
  }

  /**
   * Register all plugin routes.
   *
   * Called once on application startup.
   */
  fun Route.configureRoutes(state: ApplicationState) {
    for (plugin in plugins) {
      with(plugin) { configureRoutes(state) }
    }
  }

  /**
   * Register request validation for all plugins using their configuration.
   *
   * Called once on application startup.
   */
  fun RequestValidationConfig.configureRequestValidation() {
    for (plugin in plugins) {
      with(plugin) { configureRequestValidation() }
    }
  }

  /**
   * Register subtypes of [WorkflowOutput] for all plugins.
   *
   * Called once on application startup.
   */
  fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    for (plugin in plugins) {
      with(plugin) { configureOutputSerialization() }
    }
  }
}
