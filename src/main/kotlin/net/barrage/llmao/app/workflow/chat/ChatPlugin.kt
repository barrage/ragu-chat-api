package net.barrage.llmao.app.workflow.chat

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.app.workflow.chat.api.AdminAgentService
import net.barrage.llmao.app.workflow.chat.api.AdminApi
import net.barrage.llmao.app.workflow.chat.api.AdminChatService
import net.barrage.llmao.app.workflow.chat.api.Api
import net.barrage.llmao.app.workflow.chat.api.PublicAgentService
import net.barrage.llmao.app.workflow.chat.api.PublicApi
import net.barrage.llmao.app.workflow.chat.api.PublicChatService
import net.barrage.llmao.app.workflow.chat.model.AgentDeactivated
import net.barrage.llmao.app.workflow.chat.model.CreateAgent
import net.barrage.llmao.app.workflow.chat.model.UpdateAgent
import net.barrage.llmao.app.workflow.chat.model.UpdateChatTitleDTO
import net.barrage.llmao.app.workflow.chat.model.UpdateCollections
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryRead
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.app.workflow.chat.routes.adminAgentsRoutes
import net.barrage.llmao.app.workflow.chat.routes.adminChatsRoutes
import net.barrage.llmao.app.workflow.chat.routes.agentsRoutes
import net.barrage.llmao.app.workflow.chat.routes.avatarRoutes
import net.barrage.llmao.app.workflow.chat.routes.chatsRoutes
import net.barrage.llmao.app.workflow.chat.routes.specialistWorkflowRoutes
import net.barrage.llmao.app.workflow.chat.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.workflow.chat.whatsapp.WhatsAppRepository
import net.barrage.llmao.app.workflow.chat.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.app.workflow.chat.whatsapp.adminWhatsAppRoutes
import net.barrage.llmao.app.workflow.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.app.workflow.chat.whatsapp.whatsAppHookRoutes
import net.barrage.llmao.app.workflow.chat.whatsapp.whatsAppRoutes
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Event
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.string

const val CHAT_WORKFLOW_ID = "CHAT"
const val WHATSAPP_CHAT_TYPE = "WHATSAPP"
const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"

internal val LOG = KtorSimpleLogger("n.b.l.a.workflow.chat.ChatPlugin")

class ChatPlugin : Plugin {
  lateinit var api: Api

  private var whatsapp: WhatsAppAdapter? = null

  override fun id(): String = "CHAT"

  override suspend fun configureState(config: ApplicationConfig, state: ApplicationState) {
    val chatRead = ChatRepositoryRead(state.database, CHAT_WORKFLOW_ID)
    val agentRepository = AgentRepository(state.database)

    api =
      Api(
        admin =
          AdminApi(
            chat = AdminChatService(chatRead, agentRepository),
            agent =
              AdminAgentService(
                state.providers,
                agentRepository,
                chatRead,
                state.providers.image,
                state.listener,
              ),
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
          providers = state.providers,
          agentRepository = agentRepository,
          chatRepositoryRead = wappChatRead,
          chatRepositoryWrite = wappChatWrite,
          whatsAppRepository = WhatsAppRepository(state.database),
          settings = state.settings,
        )
    }

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
      specialistWorkflowRoutes()
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

  override suspend fun handleEvent(manager: SessionManager, event: Event) {
    when (event) {
      is AgentDeactivated -> {
        LOG.info("Handling agent deactivated event ({})", event.agentId)

        manager.retainWorkflows {
          val chat = it as? ChatWorkflow ?: return@retainWorkflows true
          chat.agentId() != event.agentId
        }

        manager.broadcast(OutgoingSystemMessage.AgentDeactivated(event.agentId))
      }
    }
  }

  override fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    subclass(ChatTitleUpdated::class, ChatTitleUpdated.serializer())
  }
}
