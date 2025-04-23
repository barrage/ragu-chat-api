package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.core.workflow.SessionManager

interface Plugin : Identity {
  suspend fun configure(config: ApplicationConfig, state: ApplicationState)

  fun Route.routes(state: ApplicationState) {}

  fun RequestValidationConfig.requestValidation() {}

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
   * Called once on application startup.
   */
  suspend fun configure(config: ApplicationConfig, state: ApplicationState) {
    for (plugin in plugins) {
      plugin.configure(config, state)
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
  fun Route.route(state: ApplicationState) {
    for (plugin in plugins) {
      with(plugin) { routes(state) }
    }
  }

  /**
   * Register request validation for all plugins using their configuration.
   *
   * Called once on application startup.
   */
  fun RequestValidationConfig.requestValidation() {
    for (plugin in plugins) {
      with(plugin) { requestValidation() }
    }
  }
}
