package net.barrage.llmao.app

import io.ktor.server.application.*
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.LlmProviderFactory
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.services.AdministrationService
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.services.UserService

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

  fun list(): Map<String, List<String>> {
    val authProviders = auth.listProviders()
    val llmProviders = llm.listProviders()
    val vectorProviders = vector.listProviders()
    val embeddingProviders = embedding.listProviders()

    return mapOf(
      "auth" to authProviders,
      "llm" to llmProviders,
      "vector" to vectorProviders,
      "embedding" to embeddingProviders,
    )
  }
}

class ServiceState(state: ApplicationState) {
  val chat = ChatService(state.providers, state.repository.chat, state.repository.agent)
  val agent = AgentService(state.providers, state.repository.agent)
  val user = UserService(state.repository.user)
  val auth =
    AuthenticationService(state.providers.auth, state.repository.session, state.repository.user)
  val admin = AdministrationService(state.providers)
}
