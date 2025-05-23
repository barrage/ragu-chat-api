package net.barrage.llmao.core

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import kotlinx.serialization.Serializable
import net.barrage.llmao.app.administration.settings.SettingsRepositoryPostgres
import net.barrage.llmao.app.blob.MinioImageStorage
import net.barrage.llmao.app.embeddings.EmbeddingProviderFactory
import net.barrage.llmao.app.llm.InferenceProviderFactory
import net.barrage.llmao.app.vector.VectorDatabaseProviderFactory
import net.barrage.llmao.core.administration.Administration
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.database.initDatabase
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.repository.TokenUsageRepositoryRead
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.int
import net.barrage.llmao.string
import org.jooq.DSLContext

fun Application.state(plugins: Plugins): ApplicationState {
  return ApplicationState(environment.config, initDatabase(environment.config), plugins)
}

/** Application state available to plugins. */
class ApplicationState(config: ApplicationConfig, val database: DSLContext, plugins: Plugins) {
  val providers: ProviderState =
    ProviderState(
      llm = InferenceProviderFactory(config),
      vector = VectorDatabaseProviderFactory(config),
      embedding = EmbeddingProviderFactory(config),
      image = MinioImageStorage(config),
    )

  /** A key-value storage API for application settings. */
  val settings: Settings = Settings(SettingsRepositoryPostgres(database))

  /**
   * Handle to the event listener.
   *
   * Events can be dispatched to this handle and are forwarded to all registered plugins via the
   * session manager.
   */
  val listener: EventListener = EventListener()

  /** Handle for sending emails, configured via application properties. */
  val email =
    Email(
      host = config.string("email.host"),
      port = config.int("email.port"),
      auth =
        run {
          val username = config.tryGetString("email.username")
          val password = config.tryGetString("email.password")

          if (username == null || password == null) {
            return@run null
          }

          EmailAuthentication(username, password)
        },
    )

  init {
    ChatMessageProcessor.init(providers)
    ContextEnrichmentFactory.init(providers)
    TokenUsageTrackerFactory.init(TokenUsageRepositoryWrite(database))
    Administration.init(providers, TokenUsageRepositoryRead(database), plugins, settings)
  }
}

/** Encapsulates all available providers for downstream services. */
class ProviderState(
  /** Providers for LLM inference. */
  val llm: ProviderFactory<InferenceProvider>,

  /** Providers for vector databases. */
  val vector: ProviderFactory<VectorDatabase>,

  /** Text embedding providers. */
  val embedding: ProviderFactory<Embedder>,

  /** Image storage provider. Only one is available per instance of app. */
  val image: BlobStorage<Image>,
) {
  fun list(): ProvidersResponse {
    val llmProviders = llm.listProviders()
    val vectorProviders = vector.listProviders()
    val embeddingProviders = embedding.listProviders()

    return ProvidersResponse(llmProviders, vectorProviders, embeddingProviders, image.id())
  }

  suspend fun listAvailableLlms(): List<String> {
    val llms = mutableListOf<String>()
    for (provider in llm.listProviders()) {
      llms.addAll(llm[provider].listModels())
    }
    return llms
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
      val llm = llm[llmProvider]
      if (!llm.supportsModel(model)) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${llm.id()}' does not support model '${model}'",
        )
      }
    }

    if (vectorProvider != null) {
      // Throws if invalid provider
      vector[vectorProvider]
    }

    if (embeddingProvider != null && embeddingModel != null) {
      val embedder = embedding[embeddingProvider]
      if (!embedder.supportsModel(embeddingModel)) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${embedder.id()}' does not support model '${embeddingModel}'",
        )
      }
    }
  }
}

@Serializable
data class ProvidersResponse(
  val llm: List<String>,
  val vector: List<String>,
  val embedding: List<String>,
  val image: String,
)
