package net.barrage.llmao.app

import ChatPlugin
import JiraKiraPlugin
import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import net.barrage.llmao.HgkPlugin
import net.barrage.llmao.adapters.blob.initializeMinio
import net.barrage.llmao.adapters.embeddings.initializeEmbedders
import net.barrage.llmao.adapters.llm.initializeInference
import net.barrage.llmao.adapters.vector.initializeVectorDatabases
import net.barrage.llmao.app.workflow.bonvoyage.BonvoyagePlugin
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.configureCore
import net.barrage.llmao.core.string

// TODO: Remove in favor of annotation processing at one point
private const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"
private const val BONVOYAGE_FEATURE_FLAG = "ktor.features.specialists.bonvoyage"

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
  Plugins.register(ChatPlugin())
  Plugins.register(HgkPlugin())

  if (environment.config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
    Plugins.register(JiraKiraPlugin())
  }

  if (environment.config.string(BONVOYAGE_FEATURE_FLAG).toBoolean()) {
    Plugins.register(BonvoyagePlugin())
  }

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
