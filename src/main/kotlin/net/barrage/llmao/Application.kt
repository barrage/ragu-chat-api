package net.barrage.llmao

import io.ktor.server.application.*
import net.barrage.llmao.app.LlmProviderFactory
import net.barrage.llmao.app.VectorDatabaseProviderFactory
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.core.chat.ChatFactory
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.plugins.Database
import net.barrage.llmao.plugins.configureCors
import net.barrage.llmao.plugins.configureErrorHandling
import net.barrage.llmao.plugins.configureOpenApi
import net.barrage.llmao.plugins.configureRequestValidation
import net.barrage.llmao.plugins.configureRouting
import net.barrage.llmao.plugins.configureSerialization
import net.barrage.llmao.plugins.configureSession
import net.barrage.llmao.plugins.extendSession
import net.barrage.llmao.services.AgentService
import net.barrage.llmao.services.UserService
import net.barrage.llmao.websocket.Server
import net.barrage.llmao.websocket.websocketServer

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  Database.init(environment.config)

  val state = ApplicationState(environment)
  val services = ServiceState(state)

  val chatFactory = ChatFactory(state.providers, services.agent, services.chat)
  val websocketServer = Server(chatFactory)

  configureSerialization()
  configureSession(services.auth)
  extendSession(services.auth)
  configureOpenApi()
  websocketServer(websocketServer)
  configureRouting(services)
  configureRequestValidation()
  configureErrorHandling()
  configureCors()
}

fun env(env: ApplicationEnvironment, key: String): String {
  return env.config.property(key).getString()
}

class RepositoryState(
  val user: UserRepository = UserRepository(),
  val session: SessionRepository = SessionRepository(),
  val agent: AgentRepository = AgentRepository(),
  val chat: ChatRepository = ChatRepository(),
)

class ProviderState(env: ApplicationEnvironment) {
  val auth: AuthenticationProviderFactory = AuthenticationProviderFactory(env)
  val llm: LlmProviderFactory = LlmProviderFactory(env)
  val vector: VectorDatabaseProviderFactory = VectorDatabaseProviderFactory(env)
}

class ApplicationState(env: ApplicationEnvironment) {
  val repository = RepositoryState()
  val providers = ProviderState(env)
}

class ServiceState(state: ApplicationState) {
  val chat = ChatService(state.providers, state.repository.chat, state.repository.agent)
  val agent = AgentService(state.repository.agent)
  val user = UserService(state.repository.user)
  val auth =
    AuthenticationService(state.providers.auth, state.repository.session, state.repository.user)
}
