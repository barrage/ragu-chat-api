package net.barrage.llmao.core

/**
 * Base for dynamic dispatch of various provider implementations.
 * - *T* - The type of provider returned by this factory.
 */
abstract class ProviderFactory<T : Identity> {
    protected val providers: MutableMap<String, T> = mutableMapOf()

    fun getOptional(providerId: String): T? = providers[providerId]

    /** Get a provider and throw an error if it doesn't exist. */
    operator fun get(providerId: String): T =
        providers[providerId]
            ?: throw AppError.api(
                ErrorReason.InvalidParameter,
                "Unsupported provider: '$providerId'"
            )

    /** List the implementor's available provider IDs. */
    fun listProviders(): List<String> = providers.keys.toList()
}
