package net.barrage.llmao.app

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.InferenceProviderFactory
import net.barrage.llmao.app.storage.MinioImageStorage
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.app.workflow.chat.ChatPlugin
import net.barrage.llmao.app.workflow.jirakira.JiraKiraPlugin
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.api.admin.AdminSettingsService
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.initDatabase
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.repository.SettingsRepository
import net.barrage.llmao.core.repository.TokenUsageRepositoryRead
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.string
import org.jooq.DSLContext

const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"
const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"

const val WHATSAPP_CHAT_TYPE = "WHATSAPP"

class ApplicationState(
  config: ApplicationConfig,
  applicationStopping: Job,
  val listener: EventListener<StateChangeEvent>,
) {
  val database: DSLContext = initDatabase(config, applicationStopping)
  val providers: ProviderState =
    ProviderState(
      llm = InferenceProviderFactory(config),
      vector = VectorDatabaseProviderFactory(config),
      embedding = EmbeddingProviderFactory(config),
      image = MinioImageStorage(config),
    )

  /** A key-value storage API for application settings. */
  val settings: AdminSettingsService = AdminSettingsService(SettingsRepository(database))

  val tokenUsageWrite: TokenUsageRepositoryWrite = TokenUsageRepositoryWrite(database)
  val tokenUsageRead: TokenUsageRepositoryRead = TokenUsageRepositoryRead(database)

  init {
    ChatMessageProcessor.init(providers)
    ContextEnrichmentFactory.init(providers)

    runBlocking {
      Plugins.register(ChatPlugin)

      if (config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
        Plugins.register(JiraKiraPlugin)
      }

      Plugins.configure(config, this@ApplicationState)
    }
  }
}

/** Keeps the state of optional modules. */
object Plugins {
  val plugins: MutableList<Plugin> = mutableListOf()

  fun register(plugin: Plugin) = plugins.add(plugin)

  suspend fun configure(config: ApplicationConfig, state: ApplicationState) {
    for (plugin in plugins) {
      plugin.configure(config, state)
    }
  }

  fun Route.route(state: ApplicationState) {
    for (plugin in plugins) {
      with(plugin) { routes(state) }
    }
  }
}
