package net.barrage.llmao.core

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.admin.AdministrationService
import net.barrage.llmao.core.agent.AgentRepository
import net.barrage.llmao.core.agent.AgentService
import net.barrage.llmao.core.chat.ChatService
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.settings.SettingsRepository
import net.barrage.llmao.core.specialist.SpecialistRepositoryWrite
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.token.TokenUsageRepositoryRead
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.core.vector.VectorDatabase
import org.jooq.DSLContext

/** Encapsulates all available providers for downstream services. */
class ProviderState(
  val llm: ProviderFactory<LlmProvider>,
  val vector: ProviderFactory<VectorDatabase>,
  val embedding: ProviderFactory<Embedder>,
  val imageStorage: ImageStorage,
) {
  fun list(): ProvidersResponse {
    val llmProviders = llm.listProviders()
    val vectorProviders = vector.listProviders()
    val embeddingProviders = embedding.listProviders()

    return ProvidersResponse(llmProviders, vectorProviders, embeddingProviders)
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

/** Encapsulates all service instances. */
class ServiceState(
  val chat: ChatService,
  val agent: AgentService,
  val admin: AdministrationService,
  val settings: Settings,
)

/** Encapsulates all repository instances. */
class RepositoryState(private val database: DSLContext) {
  val agent: AgentRepository = AgentRepository(database)
  val settings: SettingsRepository = SettingsRepository(database)
  val tokenUsageR: TokenUsageRepositoryRead = TokenUsageRepositoryRead(database)
  val tokenUsageW: TokenUsageRepositoryWrite = TokenUsageRepositoryWrite(database)

  /** Get a chat R repository for the given type of chat. */
  fun chatRead(type: String): ChatRepositoryRead = ChatRepositoryRead(database, type)

  /** Get a chat W repository for the given type of chat. */
  fun chatWrite(type: String): ChatRepositoryWrite = ChatRepositoryWrite(database, type)

  // fun specialistRead(type: String): SpecialistRepositoryRead = SpecialistRepositoryRead(database,
  // type)

  fun specialistWrite(type: String): SpecialistRepositoryWrite =
    SpecialistRepositoryWrite(database, type)
}

@Serializable
data class ProvidersResponse(
  val llm: List<String>,
  val vector: List<String>,
  val embedding: List<String>,
)
