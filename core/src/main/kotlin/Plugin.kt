package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.core.administration.settings.ApplicationSettings
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.WorkflowOutput

/**
 * A plugin that can be registered in the server runtime.
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
     * Call once on application startup when setting up websockets.
     */
    fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {}

    /** Handle an event emitted by the server. All events are broadcast to all plugins. */
    suspend fun handleEvent(manager: SessionManager, event: Event) {}

    /** Used to describe the plugin to the client and let them know of its existence. */
    fun describe(settings: ApplicationSettings): PluginConfiguration
}

@Serializable
data class PluginConfiguration(
    val id: String,
    val description: String,
    val settings: Map<String, String?>,
)

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

    /** List the configuration of all registered plugins. */
    fun list(settings: ApplicationSettings): List<PluginConfiguration> =
        plugins.map { it.describe(settings) }

    /**
     * Configure all plugins registered in this object.
     *
     * Called once on application startup and is the first plugin configuration method called.
     */
    suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
        for (plugin in plugins) {
            plugin.initialize(config, state)
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
