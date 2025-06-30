package net.barrage.llmao.app

import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import net.barrage.llmao.adapters.blob.initializeMinio
import net.barrage.llmao.adapters.embeddings.initializeEmbedders
import net.barrage.llmao.adapters.llm.initializeInference
import net.barrage.llmao.adapters.vector.initializeVectorDatabases
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.configureCore

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
  val state =
    ApplicationState(
      environment.config,
      ProviderState(
        llm = initializeInference(environment.config),
        vector = initializeVectorDatabases(environment.config),
        embedding = initializeEmbedders(environment.config),
        image = initializeMinio(environment.config),
      ),
    )
  configureCore(state)
}
