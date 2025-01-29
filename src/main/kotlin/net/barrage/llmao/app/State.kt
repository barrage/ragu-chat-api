package net.barrage.llmao.app

import io.ktor.server.config.*
import kotlin.reflect.KClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationRepository
import net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository
import net.barrage.llmao.app.api.http.CookieFactory
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.LlmProviderFactory
import net.barrage.llmao.app.storage.MinioImageStorage
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.services.AdministrationService
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.AuthenticationService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.services.UserService
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.workflow.chat.ChatWorkflowRepository
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.initDatabase
import net.barrage.llmao.string
import org.jooq.DSLContext

const val CHONKIT_AUTH_FEATURE_FLAG = "ktor.features.chonkitAuthServer"
const val WHATSAPP_FEATURE_FLAG = "ktor.features.whatsApp"

class ApplicationState(
  config: ApplicationConfig,
  applicationStopping: Job,
  listener: EventListener<StateChangeEvent>,
) {
  val providers = ProviderState(config)
  val repository: RepositoryState
  val adapters: AdapterState
  val services: ServiceState

  init {
    CookieFactory.init(config)
    val database = initDatabase(config, applicationStopping)
    repository = RepositoryState(database)
    services = ServiceState(providers, repository, listener)
    adapters = AdapterState(config, database, providers)
  }
}

class RepositoryState(database: DSLContext) {
  val user: UserRepository = UserRepository(database)
  val session: SessionRepository = SessionRepository(database)
  val agent: AgentRepository = AgentRepository(database)
  val chat: ChatRepository = ChatRepository(database)
  val chatSession: ChatWorkflowRepository = ChatWorkflowRepository(database)
}

/**
 * Keeps the state of optional modules. Modules are configured via the `ktor.features` flags.
 * Whenever a flag is enabled, the corresponding adapter is enabled for use.
 */
class AdapterState(config: ApplicationConfig, database: DSLContext, providers: ProviderState) {
  val adapters = mutableMapOf<KClass<*>, Any>()

  init {
    if (config.string(CHONKIT_AUTH_FEATURE_FLAG).toBoolean()) {
      val chonkitAuthRepo = ChonkitAuthenticationRepository(database)
      adapters[ChonkitAuthenticationService::class] = runBlocking {
        ChonkitAuthenticationService.init(chonkitAuthRepo, config)
      }
    }
    if (config.string(WHATSAPP_FEATURE_FLAG).toBoolean()) {
      val whatsAppRepo = WhatsAppRepository(database)
      adapters[WhatsAppAdapter::class] =
        WhatsAppAdapter(config, providers, whatsAppRepo, providers.imageStorage)
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

class ProviderState(config: ApplicationConfig) {
  val auth: AuthenticationProviderFactory = AuthenticationProviderFactory(config)
  val llm: LlmProviderFactory = LlmProviderFactory(config)
  val vector: VectorDatabaseProviderFactory = VectorDatabaseProviderFactory(config)
  val embedding: EmbeddingProviderFactory = EmbeddingProviderFactory(config)
  val imageStorage: ImageStorage = MinioImageStorage(config)

  fun list(): ProvidersResponse {
    val authProviders = auth.listProviders()
    val llmProviders = llm.listProviders()
    val vectorProviders = vector.listProviders()
    val embeddingProviders = embedding.listProviders()

    return ProvidersResponse(authProviders, llmProviders, vectorProviders, embeddingProviders)
  }

  /**
   * Checks whether the providers and their respective models are supported. Data passed to this
   * function should come from already validated DTOs.
   */
  suspend fun validateSupportedConfigurationParams(
    llmProvider: String? = null,
    model: String? = null,
    vectorProvider: String? = null,
    embeddingProvider: String? = null,
    embeddingModel: String? = null,
  ) {
    if (llmProvider != null && model != null) {
      // Throws if invalid provider
      val llm = llm.getProvider(llmProvider)
      if (!llm.supportsModel(model)) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${llm.id()}' does not support model '${model}'",
        )
      }
    }

    if (vectorProvider != null) {
      // Throws if invalid provider
      vector.getProvider(vectorProvider)
    }

    if (embeddingProvider != null && embeddingModel != null) {
      val embedder = embedding.getProvider(embeddingProvider)
      if (!embedder.supportsModel(embeddingModel)) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${embedder.id()}' does not support model '${embeddingModel}'",
        )
      }
    }
  }
}

class ServiceState(
  providers: ProviderState,
  repository: RepositoryState,
  listener: EventListener<StateChangeEvent>,
) {
  val chat = ChatService(repository.chat, repository.agent, repository.user, providers.imageStorage)
  val agent =
    AgentService(providers, repository.agent, repository.chat, listener, providers.imageStorage)
  val user = UserService(repository.user, repository.session, providers.imageStorage)
  val auth = AuthenticationService(providers.auth, repository.session, repository.user)
  val admin = AdministrationService(providers, repository.agent, repository.chat, repository.user)
}

@Serializable
data class ProvidersResponse(
  val auth: List<String>,
  val llm: List<String>,
  val vector: List<String>,
  val embedding: List<String>,
)
