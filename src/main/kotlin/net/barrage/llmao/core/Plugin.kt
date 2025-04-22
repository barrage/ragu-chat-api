package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.app.ApplicationState

interface Plugin : Identity {
  suspend fun configure(config: ApplicationConfig, state: ApplicationState)

  fun Route.routes(state: ApplicationState)
}
