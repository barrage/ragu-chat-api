package net.barrage.llmao.core

import net.barrage.llmao.core.llm.ConversationLlm

typealias AuthenticationFactory = ProviderFactory<AuthenticationProvider>

typealias LlmFactory = ProviderFactory<ConversationLlm>

/**
 * Generic interface for dynamic dispatch of various provider implementations.
 * - *T* - The type of provider returned by this factory.
 */
abstract class ProviderFactory<T> {
  /** Get a provider and throw an error if it doesn't exist. */
  abstract fun getProvider(providerId: String): T
}
