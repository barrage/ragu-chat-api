package net.barrage.llmao.app

import io.ktor.server.application.*
import kotlinx.serialization.Serializable
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
import net.barrage.llmao.plugins.initDatabase
import org.jooq.DSLContext

class ApplicationState(env: ApplicationEnvironment) {
  val repository = RepositoryState(initDatabase(env))
  val providers = ProviderState(env)
}

class RepositoryState(
  client: DSLContext,
  val user: UserRepository = UserRepository(client),
  val session: SessionRepository = SessionRepository(client),
  val agent: AgentRepository = AgentRepository(client),
  val chat: ChatRepository = ChatRepository(client),
)

class ProviderState(env: ApplicationEnvironment) {
  val auth: AuthenticationProviderFactory = AuthenticationProviderFactory(env)
  val llm: LlmProviderFactory = LlmProviderFactory(env)
  val vector: VectorDatabaseProviderFactory = VectorDatabaseProviderFactory(env)
  val embedding: EmbeddingProviderFactory = EmbeddingProviderFactory(env)

  fun list(): ProvidersResponse {
    val authProviders = auth.listProviders()
    val llmProviders = llm.listProviders()
    val vectorProviders = vector.listProviders()
    val embeddingProviders = embedding.listProviders()

    return ProvidersResponse(authProviders, llmProviders, vectorProviders, embeddingProviders)
  }
}

class ServiceState(state: ApplicationState) {
  val chat =
    ChatService(
      state.providers,
      state.repository.chat,
      state.repository.agent,
      state.repository.user,
    )
  val agent = AgentService(state.providers, state.repository.agent)
  val user = UserService(state.repository.user, state.repository.session)
  val auth =
    AuthenticationService(state.providers.auth, state.repository.session, state.repository.user)
  val admin =
    AdministrationService(
      state.providers,
      state.repository.agent,
      state.repository.chat,
      state.repository.user,
    )
}

@Serializable
data class ProvidersResponse(
  val auth: List<String>,
  val llm: List<String>,
  val vector: List<String>,
  val embedding: List<String>,
)
