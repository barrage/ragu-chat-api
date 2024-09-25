package net.barrage.llmao.core

/**
 * Generic interface for dynamic dispatch of various provider implementations.
 * - *I* - The type of identifier for the provider. In most cases, this can be an enum.
 * - *T* - The type of provider returned by this factory.
 */
abstract class ProviderFactory<I, T> {
  /** Get a provider and throw an error if it doesn't exist. */
  abstract fun getProvider(providerId: I): T
}
