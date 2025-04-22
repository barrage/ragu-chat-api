package net.barrage.llmao.app.workflow.chat

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.WHATSAPP_CHAT_TYPE
import net.barrage.llmao.app.WHATSAPP_FEATURE_FLAG
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppRepository
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppSenderConfig
import net.barrage.llmao.app.adapters.whatsapp.adminWhatsAppRoutes
import net.barrage.llmao.app.adapters.whatsapp.whatsAppHookRoutes
import net.barrage.llmao.app.adapters.whatsapp.whatsAppRoutes
import net.barrage.llmao.app.workflow.chat.controllers.adminAgentsRoutes
import net.barrage.llmao.app.workflow.chat.controllers.adminChatsRoutes
import net.barrage.llmao.app.workflow.chat.controllers.administrationRouter
import net.barrage.llmao.app.workflow.chat.controllers.agentsRoutes
import net.barrage.llmao.app.workflow.chat.controllers.chatsRoutes
import net.barrage.llmao.app.workflow.chat.controllers.specialistWorkflowRoutes
import net.barrage.llmao.core.AdminApi
import net.barrage.llmao.core.Api
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.PublicApi
import net.barrage.llmao.core.api.admin.AdminAgentService
import net.barrage.llmao.core.api.admin.AdminChatService
import net.barrage.llmao.core.api.admin.AdminStatService
import net.barrage.llmao.core.api.pub.PublicAgentService
import net.barrage.llmao.core.api.pub.PublicChatService
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.string

const val CHAT_WORKFLOW_ID = "CHAT"

object ChatPlugin : Plugin {
  lateinit var api: Api

  private var whatsapp: WhatsAppAdapter? = null

  override fun id(): String = "CHAT"

  override suspend fun configure(config: ApplicationConfig, state: ApplicationState) {
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
                state.listener,
                state.providers.image,
              ),
            admin =
              AdminStatService(state.providers, agentRepository, chatRead, state.tokenUsageRead),
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
          tokenUsageRepositoryW = state.tokenUsageWrite,
        )
    }

    ChatWorkflowFactory.init(state.providers, api, state)
    WorkflowFactoryManager.register(ChatWorkflowFactory)
  }

  override fun Route.routes(state: ApplicationState) {
    // Admin API routes
    authenticate("admin") {
      adminAgentsRoutes(api.admin.agent, state.settings)
      adminChatsRoutes(api.admin.chat)
      administrationRouter(api.admin.admin)
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
}
