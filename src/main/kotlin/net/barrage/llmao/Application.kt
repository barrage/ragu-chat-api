package net.barrage.llmao

import io.ktor.server.application.*
import net.barrage.llmao.app.api.ws.ChatFactory
import net.barrage.llmao.app.api.ws.Server
import net.barrage.llmao.app.api.ws.websocketServer
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.LlmProviderFactory
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.plugins.*

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

class ApplicationState(env: ApplicationEnvironment) {
  val repository = RepositoryState()
  val providers = ProviderState(env)
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
  val embedding: EmbeddingProviderFactory = EmbeddingProviderFactory(env)
}

class ServiceState(state: ApplicationState) {
  val chat = ChatService(state.providers, state.repository.chat, state.repository.agent)
  val agent = AgentService(state.providers, state.repository.agent)
  val user = UserService(state.repository.user)
  val auth =
    AuthenticationService(state.providers.auth, state.repository.session, state.repository.user)
}
