package net.barrage.llmao.app

import com.knuddels.jtokkit.Encodings
import io.ktor.server.config.ApplicationConfig
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppRepository
import net.barrage.llmao.app.chat.ChatType
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.LlmProviderFactory
import net.barrage.llmao.app.specialist.SpecialistType
import net.barrage.llmao.app.specialist.jirakira.JiraKiraRepository
import net.barrage.llmao.app.specialist.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.app.storage.MinioImageStorage
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.RepositoryState
import net.barrage.llmao.core.ServiceState
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.admin.AdministrationService
import net.barrage.llmao.core.agent.AgentService
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.ChatService
import net.barrage.llmao.core.initDatabase
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.string
import org.jooq.DSLContext

const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"
const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"

class ApplicationState(
  config: ApplicationConfig,
  applicationStopping: Job,
  listener: EventListener<StateChangeEvent>,
) {
  val providers: ProviderState
  val repository: RepositoryState
  val services: ServiceState
  val adapters: AdapterState

  init {
    val database = initDatabase(config, applicationStopping)

    repository = RepositoryState(database)
    providers =
      ProviderState(
        llm = LlmProviderFactory(config),
        vector = VectorDatabaseProviderFactory(config),
        embedding = EmbeddingProviderFactory(config),
        image = MinioImageStorage(config),
      )
    services =
      ServiceState(
        chat = ChatService(repository.chatRead(ChatType.CHAT.value), repository.agent),
        agent =
          AgentService(
            providers,
            repository.agent,
            repository.chatRead(ChatType.CHAT.value),
            listener,
            providers.image,
          ),
        admin =
          AdministrationService(
            providers,
            repository.agent,
            repository.chatRead(ChatType.CHAT.value),
            repository.tokenUsageR,
          ),
        settings = Settings(repository.settings),
      )
    adapters =
      AdapterState(
        config = config,
        database = database,
        providers = providers,
        settings = services.settings,
        repository = repository,
      )
  }
}

/**
 * Keeps the state of optional modules. Modules are configured via the `ktor.features` flags.
 * Whenever a flag is enabled, the corresponding adapter is enabled for use.
 */
class AdapterState(
  config: ApplicationConfig,
  database: DSLContext,
  providers: ProviderState,
  settings: Settings,
  repository: RepositoryState,
) {
  val adapters = mutableMapOf<KClass<*>, Any>()

  init {
    if (config.string(WHATSAPP_FEATURE_FLAG).toBoolean()) {
      adapters[WhatsAppAdapter::class] =
        WhatsAppAdapter(
          apiKey = config.string("infobip.apiKey"),
          endpoint = config.string("infobip.endpoint"),
          config =
            WhatsAppSenderConfig(
              config.string("infobip.sender"),
              config.string("infobip.template"),
              config.string("infobip.appName"),
            ),
          providers = providers,
          agentRepository = repository.agent,
          chatRepositoryRead = repository.chatRead(ChatType.WHATSAPP.value),
          chatRepositoryWrite = repository.chatWrite(ChatType.WHATSAPP.value),
          whatsAppRepository = WhatsAppRepository(database),
          settings = settings,
          tokenUsageRepositoryW = TokenUsageRepositoryWrite(database),
          encodingRegistry = Encodings.newDefaultEncodingRegistry(),
          messageProcessor = ChatMessageProcessor(providers),
        )
    }

    if (config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
      val endpoint = config.string("jirakira.endpoint")
      val jiraKiraKeyStore = JiraKiraRepository(database)
      val jiraKiraWorkflowFactory =
        JiraKiraWorkflowFactory(
          endpoint = endpoint,
          providers = providers,
          settings = settings,
          tokenUsageRepositoryW = TokenUsageRepositoryWrite(database),
          jiraKiraRepository = jiraKiraKeyStore,
          specialistRepositoryWrite = repository.specialistWrite(SpecialistType.JIRAKIRA.value),
        )
      adapters[JiraKiraWorkflowFactory::class] = jiraKiraWorkflowFactory
    }
  }

  /**
   * Execute the given block if the given feature is enabled.
   *
   * @param block Block to run if the feature is enabled. Gets access to whichever adapter is mapped
   *   to the feature.
   * @param T The type of adapter to run the block with. Callers need to make sure they are passing
   *   the correct type or the cast will fail.
   * @param O The return type of the block.
   */
  inline fun <reified T, O> runIfEnabled(block: (T) -> O): O? {
    return adapters[T::class]?.let { adapter ->
      return block(adapter as T)
    }
  }

  inline fun <reified T> adapterForFeature(): T? {
    return adapters[T::class] as T
  }
}
