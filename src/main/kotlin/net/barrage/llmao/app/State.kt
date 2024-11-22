package net.barrage.llmao.app

import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationRepository
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
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

class ApplicationState(config: ApplicationConfig) {
  val repository: RepositoryState
  val adapterRepository: AdapterRepositoryState
  val providers = ProviderState(config)
  val adapters: AdapterState

  init {
    val database = initDatabase(config)
    repository = RepositoryState(database)
    adapterRepository = AdapterRepositoryState(database)
    adapters = AdapterState(config, adapterRepository)
  }
}

class AdapterState(config: ApplicationConfig, repositoryState: AdapterRepositoryState) {
  // TODO: Module flag
  val chonkitAuth: ChonkitAuthenticationService = runBlocking {
    ChonkitAuthenticationService.init(repositoryState.chonkit, config)
  }
}

class AdapterRepositoryState(client: DSLContext) {
  // TODO: Module flag
  val chonkit = ChonkitAuthenticationRepository(client)
}

class RepositoryState(client: DSLContext) {
  val user: UserRepository = UserRepository(client)
  val session: SessionRepository = SessionRepository(client)
  val agent: AgentRepository = AgentRepository(client)
  val chat: ChatRepository = ChatRepository(client)
}

class ProviderState(config: ApplicationConfig) {
  val auth: AuthenticationProviderFactory = AuthenticationProviderFactory(config)
  val llm: LlmProviderFactory = LlmProviderFactory(config)
  val vector: VectorDatabaseProviderFactory = VectorDatabaseProviderFactory(config)
  val embedding: EmbeddingProviderFactory = EmbeddingProviderFactory(config)

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
  val agent = AgentService(state.providers, state.repository.agent, state.repository.chat)
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
  val chonkitAuth = state.adapters.chonkitAuth
}

@Serializable
data class ProvidersResponse(
  val auth: List<String>,
  val llm: List<String>,
  val vector: List<String>,
  val embedding: List<String>,
)
