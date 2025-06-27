package net.barrage.llmao.chat

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.chat.api.AdminAgentService
import net.barrage.llmao.chat.api.AdminApi
import net.barrage.llmao.chat.api.AdminChatService
import net.barrage.llmao.chat.api.Api
import net.barrage.llmao.chat.api.PublicAgentService
import net.barrage.llmao.chat.api.PublicApi
import net.barrage.llmao.chat.api.PublicChatService
import net.barrage.llmao.chat.model.AgentDeactivated
import net.barrage.llmao.chat.model.CreateAgent
import net.barrage.llmao.chat.model.UpdateAgent
import net.barrage.llmao.chat.model.UpdateChatTitleDTO
import net.barrage.llmao.chat.model.UpdateCollections
import net.barrage.llmao.chat.repository.AgentRepository
import net.barrage.llmao.chat.repository.ChatRepositoryRead
import net.barrage.llmao.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.chat.routes.adminAgentsRoutes
import net.barrage.llmao.chat.routes.adminChatsRoutes
import net.barrage.llmao.chat.routes.agentsRoutes
import net.barrage.llmao.chat.routes.avatarRoutes
import net.barrage.llmao.chat.routes.chatsRoutes
import net.barrage.llmao.chat.whatsapp.WhatsAppAdapter
import net.barrage.llmao.chat.whatsapp.WhatsAppRepository
import net.barrage.llmao.chat.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.chat.whatsapp.adminWhatsAppRoutes
import net.barrage.llmao.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.chat.whatsapp.whatsAppHookRoutes
import net.barrage.llmao.chat.whatsapp.whatsAppRoutes
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.PluginConfiguration
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.string
import net.barrage.llmao.core.workflow.Event
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.core.workflow.WorkflowOutput

internal const val CHAT_WORKFLOW_ID = "CHAT"
internal const val WHATSAPP_CHAT_TYPE = "WHATSAPP"
internal const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"

class ChatPlugin : Plugin {
  lateinit var api: Api

  private var whatsapp: WhatsAppAdapter? = null

  override fun id(): String = CHAT_WORKFLOW_ID

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    val chatRead = ChatRepositoryRead(state.database, CHAT_WORKFLOW_ID)
    val agentRepository = AgentRepository(state.database)

    api =
      Api(
        admin =
          AdminApi(
            chat = AdminChatService(chatRead, agentRepository),
            agent =
              AdminAgentService(state.providers, agentRepository, chatRead, state.providers.image),
          ),
        user =
          PublicApi(
            chat = PublicChatService(chatRead, agentRepository),
            agent = PublicAgentService(state.providers, agentRepository),
          ),
      )

    if (config.string(WHATSAPP_FEATURE_FLAG).toBoolean()) {
      val wappChatRead = ChatRepositoryRead(state.database, WHATSAPP_CHAT_TYPE)
      val wappChatWrite = ChatRepositoryWrite(state.database, WHATSAPP_CHAT_TYPE)
      whatsapp =
        WhatsAppAdapter(
          apiKey = config.string("infobip.apiKey"),
          endpoint = config.string("infobip.endpoint"),
          config =
            WhatsAppSenderConfig(
              config.string("infobip.sender"),
              config.string("infobip.template"),
              config.string("infobip.appName"),
            ),
          agentRepository = agentRepository,
          chatRepositoryRead = wappChatRead,
          chatRepositoryWrite = wappChatWrite,
          whatsAppRepository = WhatsAppRepository(state.database),
          settings = state.settings,
        )
    }

    ChatToolExecutor.init(api)
    ChatWorkflowFactory.init(state.providers, api, state)
    WorkflowFactoryManager.register(ChatWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    avatarRoutes(state.providers.image)

    // Admin API routes
    authenticate("admin") {
      adminAgentsRoutes(api.admin.agent, state.settings)
      adminChatsRoutes(api.admin.chat)
    }

    // User API routes
    authenticate("user") {
      agentsRoutes(api.user.agent)
      chatsRoutes(api.user.chat, state.providers.image)
    }

    whatsapp?.let {
      whatsAppHookRoutes(it)
      authenticate("admin") { adminWhatsAppRoutes(it) }
      authenticate("user") { whatsAppRoutes(it) }
    }
  }

  override fun RequestValidationConfig.configureRequestValidation() {
    validate<CreateAgent>(CreateAgent::validate)
    validate<UpdateAgent>(UpdateAgent::validate)
    validate<UpdateCollections>(UpdateCollections::validate)

    // Chat DTOs validations
    validate<UpdateChatTitleDTO>(UpdateChatTitleDTO::validate)

    // WhatsApp DTOs validations
    validate<UpdateNumber>(UpdateNumber::validate)
  }

  override fun describe(settings: ApplicationSettings): PluginConfiguration =
    PluginConfiguration(
      id = id(),
      description = "Chat with custom agents.",
      settings =
        mapOf(
          MaxHistoryTokens.KEY to
            (settings.getOptional(MaxHistoryTokens.KEY) ?: MaxHistoryTokens.DEFAULT.toString()),
          AgentPresencePenalty.KEY to settings.getOptional(AgentPresencePenalty.KEY),
          AgentTitleMaxCompletionTokens.KEY to
            (settings.getOptional(AgentTitleMaxCompletionTokens.KEY)
              ?: AgentTitleMaxCompletionTokens.DEFAULT.toString()),
        ),
    )

  override fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    subclass(ChatTitleUpdated::class, ChatTitleUpdated.serializer())
  }

  override fun PolymorphicModuleBuilder<Event>.configureEventSerialization() {
    subclass(AgentDeactivated::class, AgentDeactivated.serializer())
  }
}

/**
 * Maximum number of tokens to keep in chat histories. Used to prevent context windows from growing
 * too large. Always applies to all chats.
 */
internal data object MaxHistoryTokens {
  const val KEY = "CHAT_MAX_HISTORY_TOKENS"
  const val DEFAULT = 100_000
}

/**
 * Used to penalize tokens that are already present in the context window. The global value applies
 * to all agents, unless overridden by their configuration.
 */
internal data object AgentPresencePenalty {
  const val KEY = "CHAT_AGENT_PRESENCE_PENALTY"
}

/** The maximum amount of tokens for title generation. Applied to all agents. */
internal data object AgentTitleMaxCompletionTokens {
  const val KEY = "CHAT_AGENT_TITLE_MAX_COMPLETION_TOKENS"
  const val DEFAULT = 100
}

/** The ID of the agent used for WhatsApp. */
internal data object WhatsappAgentId {
  const val KEY = "WHATSAPP.AGENT_ID"
}
