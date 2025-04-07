package net.barrage.llmao.app

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingRegistry
import io.ktor.server.config.ApplicationConfig
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppRepository
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.LlmProviderFactory
import net.barrage.llmao.app.storage.MinioImageStorage
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.app.workflow.jirakira.JiraKiraRepository
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.RepositoryState
import net.barrage.llmao.core.ServiceState
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.api.AdministrationService
import net.barrage.llmao.core.api.AgentService
import net.barrage.llmao.core.api.ChatService
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.initDatabase
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.string
import org.jooq.DSLContext

const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"
const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"

const val JIRA_KIRA_SPECIALIST_CHAT_TYPE = "JIRAKIRA"
const val API_CHAT_TYPE = "CHAT"
const val WHATSAPP_CHAT_TYPE = "WHATSAPP"

class ApplicationState(
  config: ApplicationConfig,
  applicationStopping: Job,
  listener: EventListener<StateChangeEvent>,
) {
  val providers: ProviderState
  val repository: RepositoryState
  val services: ServiceState
  val adapters: AdapterState
  val chatWorkflowFactory: ChatWorkflowFactory
  val workflowManager: WorkflowFactoryManager

  init {
    val database = initDatabase(config, applicationStopping)
    val encodingRegistry = Encodings.newDefaultEncodingRegistry()
    workflowManager = WorkflowFactoryManager()

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
        chat = ChatService(repository.chatRead(API_CHAT_TYPE), repository.agent),
        agent =
          AgentService(
            providers,
            repository.agent,
            repository.chatRead(API_CHAT_TYPE),
            listener,
            providers.image,
          ),
        admin =
          AdministrationService(
            providers,
            repository.agent,
            repository.chatRead("CHAT"),
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
        encodingRegistry = encodingRegistry,
        workflowManager = workflowManager,
      )

    chatWorkflowFactory =
      ChatWorkflowFactory(
        providers = providers,
        services = services,
        repository = repository,
        toolchainFactory = ToolchainFactory(services, repository.agent),
        settings = services.settings,
        encodingRegistry = encodingRegistry,
        messageProcessor = ChatMessageProcessor(providers),
        contextEnrichmentFactory = ContextEnrichmentFactory(providers),
      )

    workflowManager.register(chatWorkflowFactory)
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
  encodingRegistry: EncodingRegistry,
  workflowManager: WorkflowFactoryManager,
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
          chatRepositoryRead = repository.chatRead(WHATSAPP_CHAT_TYPE),
          chatRepositoryWrite = repository.chatWrite(WHATSAPP_CHAT_TYPE),
          whatsAppRepository = WhatsAppRepository(database),
          settings = settings,
          tokenUsageRepositoryW = TokenUsageRepositoryWrite(database),
          encodingRegistry = encodingRegistry,
          messageProcessor = ChatMessageProcessor(providers),
          contextEnrichmentFactory = ContextEnrichmentFactory(providers),
        )
    }

    if (config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
      val endpoint = config.string("jirakira.endpoint")
      val jiraKiraRepository = JiraKiraRepository(database)
      val jiraKiraWorkflowFactory =
        JiraKiraWorkflowFactory(
          endpoint = endpoint,
          providers = providers,
          settings = settings,
          tokenUsageRepositoryW = TokenUsageRepositoryWrite(database),
          jiraKiraRepository = jiraKiraRepository,
          specialistRepositoryWrite = repository.specialistWrite(JIRA_KIRA_SPECIALIST_CHAT_TYPE),
          messageProcessor = ChatMessageProcessor(providers),
          encodingRegistry = encodingRegistry,
        )
      adapters[JiraKiraWorkflowFactory::class] = jiraKiraWorkflowFactory
      workflowManager.register(jiraKiraWorkflowFactory)
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
