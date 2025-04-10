package net.barrage.llmao.core

/** Used to identify providers. */
interface Identity {
  fun id(): String
}

/**
 * Base for dynamic dispatch of various provider implementations.
 * - *T* - The type of provider returned by this factory.
 */
abstract class ProviderFactory<T : Identity> {
  protected val providers: MutableMap<String, T> = mutableMapOf()

  /** Get a provider and throw an error if it doesn't exist. */
  fun getProvider(providerId: String): T =
    providers[providerId]
      ?: throw AppError.api(ErrorReason.InvalidProvider, "Unsupported provider: '$providerId'")

  /** List the implementor's available provider IDs. */
  fun listProviders(): List<String> = providers.keys.toList()
}
