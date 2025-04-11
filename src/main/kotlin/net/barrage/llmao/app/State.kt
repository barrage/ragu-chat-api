package net.barrage.llmao.app

import io.ktor.server.config.ApplicationConfig
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppRepository
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.InferenceProviderFactory
import net.barrage.llmao.app.storage.MinioImageStorage
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.app.workflow.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.core.AdminApi
import net.barrage.llmao.core.Api
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.PublicApi
import net.barrage.llmao.core.RepositoryState
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.api.admin.AdminAgentService
import net.barrage.llmao.core.api.admin.AdminChatService
import net.barrage.llmao.core.api.admin.AdminSettingsService
import net.barrage.llmao.core.api.admin.AdminStatService
import net.barrage.llmao.core.api.pub.PublicAgentService
import net.barrage.llmao.core.api.pub.PublicChatService
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.initDatabase
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.string
import org.jooq.DSLContext

const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"
const val JIRAKIRA_FEATURE_FLAG = "ktor.features.specialists.jirakira"

const val JIRAKIRA_WORKFLOW_ID = "JIRAKIRA"
const val CHAT_WORKFLOW_ID = "CHAT"

const val WHATSAPP_CHAT_TYPE = "WHATSAPP"

class ApplicationState(
  config: ApplicationConfig,
  applicationStopping: Job,
  listener: EventListener<StateChangeEvent>,
) {
  val database: DSLContext = initDatabase(config, applicationStopping)
  val providers: ProviderState =
    ProviderState(
      llm = InferenceProviderFactory(config),
      vector = VectorDatabaseProviderFactory(config),
      embedding = EmbeddingProviderFactory(config),
      image = MinioImageStorage(config),
    )
  val repository: RepositoryState = RepositoryState(database)
  val services: Api =
    Api(
      admin =
        AdminApi(
          chat = AdminChatService(repository.chatRead(CHAT_WORKFLOW_ID), repository.agent),
          agent =
            AdminAgentService(
              providers,
              repository.agent,
              repository.chatRead(CHAT_WORKFLOW_ID),
              listener,
              providers.image,
            ),
          admin =
            AdminStatService(
              providers,
              repository.agent,
              repository.chatRead("CHAT"),
              repository.tokenUsageR,
            ),
          settings = AdminSettingsService(repository.settings),
        ),
      user =
        PublicApi(
          chat = PublicChatService(repository.chatRead(CHAT_WORKFLOW_ID), repository.agent),
          agent = PublicAgentService(providers, repository.agent),
        ),
    )

  init {
    ChatMessageProcessor.init(providers)
    ContextEnrichmentFactory.init(providers)

    runBlocking {
      ChatWorkflowFactory.init(config, this@ApplicationState)

      WorkflowFactoryManager.register(ChatWorkflowFactory)

      if (config.string(JIRAKIRA_FEATURE_FLAG).toBoolean()) {
        JiraKiraWorkflowFactory.init(config, this@ApplicationState)
        Adapters[JiraKiraWorkflowFactory::class] = JiraKiraWorkflowFactory
        WorkflowFactoryManager.register(JiraKiraWorkflowFactory)
      }

      if (config.string(WHATSAPP_FEATURE_FLAG).toBoolean()) {
        Adapters[WhatsAppAdapter::class] =
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
            settings = services.admin.settings,
            tokenUsageRepositoryW = TokenUsageRepositoryWrite(database),
          )
      }
    }
  }
}

/**
 * Keeps the state of optional modules. Modules are configured via the `ktor.features` flags.
 * Whenever a flag is enabled, the corresponding adapter is enabled for use.
 */
object Adapters {
  val adapters: MutableMap<KClass<*>, Any> = mutableMapOf<KClass<*>, Any>()

  /**
   * Execute the given block if the given feature is enabled.
   *
   * @param block Block to run if the feature is enabled. Gets access to whichever adapter is mapped
   *   to the feature.
   * @param T The type of adapter to run the block with. Callers need to make sure they are passing
   *   the correct type or the cast will fail.
   */
  inline fun <reified T> runIfEnabled(block: (T) -> Unit) {
    adapters[T::class]?.let { adapter -> block(adapter as T) }
  }

  operator fun set(key: KClass<*>, value: Any) = adapters.set(key, value)

  inline operator fun <reified T> get(key: KClass<*>): T? = adapters[key]?.let { it as T }
}
