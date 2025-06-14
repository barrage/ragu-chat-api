package net.barrage.llmao.core.llm

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.model.AgentCollection
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData

private val LOG = KtorSimpleLogger("n.b.l.c.llm.RagContextEnrichment")

/**
 * Generic interface for enriching an LLM's context.
 *
 * Context enrichment is done on the user message.
 */
interface ContextEnrichment {
  /**
   * Enrich the given input with additional information.
   *
   * Each implementation will have different ways of enriching the input, but all of them must
   * return a string with the enriched content.
   *
   * @param input The input to enrich. Can be previously enriched. If so, implementations should
   *   take care of proper formatting.
   * @return The enriched input.
   */
  suspend fun enrich(input: String): String
}

object ContextEnrichmentFactory {
  private lateinit var providers: ProviderState

  fun init(providers: ProviderState) {
    this.providers = providers
  }

  /**
   * User entitlements are used to check application permissions.
   *
   * In cases of RAG when the `groups` properties in collections is present, they will be checked to
   * see if the agent can access that collection.
   *
   * If the user is in a group from the collection's `groups` property, the LLM will have its
   * context enriched with the collection's data.
   */
  fun collectionEnrichment(
    tokenTracker: TokenUsageTracker,
    userEntitlements: List<String>,
    collections: List<AgentCollection>,
  ): RagContextEnrichment? {
    val chatCollections = mutableListOf<AgentCollection>()

    for (collection in collections) {
      val collectionInfo =
        providers.vector[collection.vectorProvider].getCollectionInfo(collection.collection)

      if (collectionInfo == null) {
        LOG.warn("Collection '{}' does not exist, skipping", collection.collection)
        continue
      }

      // Check collection permissions
      var allowed = true

      // No groups assigned means collection is visible to everyone
      collectionInfo.groups?.let {
        // Empty groups also
        if (it.isEmpty()) {
          return@let
        }

        allowed = false

        for (group in it) {
          if (userEntitlements.contains(group)) {
            allowed = true
            break
          }
        }
      }

      if (!allowed) {
        LOG.warn(
          "Collection '{}' is not available to user; required: {}, user: {}",
          collection.collection,
          collectionInfo.groups,
          userEntitlements,
        )
        continue
      }

      chatCollections.add(collection)
    }

    return if (chatCollections.isEmpty()) null
    else RagContextEnrichment(chatCollections, providers, tokenTracker)
  }
}

class RagContextEnrichment(
  private val collections: List<AgentCollection>,
  private val providers: ProviderState,
  private val tokenTracker: TokenUsageTracker,
) : ContextEnrichment {

  /**
   * Uses the agent's collection setup to retrieve related content from the vector database. Returns
   * a string in the form of:
   * ```
   * <INSTRUCTIONS>
   * <PROMPT>
   * ```
   */
  override suspend fun enrich(input: String): String {
    LOG.info("Executing RAG enrichment")

    if (collections.isEmpty()) {
      return input
    }

    var collectionInstructions = ""

    // Maps providers to lists of CollectionQuery
    val providerQueries = mutableMapOf<String, MutableList<CollectionQuery>>()

    // Embed the input per collection provider and model
    for (collection in collections) {
      val embeddings =
        providers.embedding[collection.embeddingProvider].embed(input, collection.embeddingModel)

      embeddings.usage?.let { tokenUsage ->
        tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.EMBEDDING,
          model = collection.embeddingModel,
          provider = collection.embeddingProvider,
        )
      }

      providerQueries[collection.vectorProvider]?.add(
        CollectionQuery(
          name = collection.collection,
          amount = collection.amount,
          maxDistance = collection.maxDistance,
          vector = embeddings.embeddings,
        )
      )
        ?: run {
          providerQueries[collection.vectorProvider] =
            mutableListOf(
              CollectionQuery(
                name = collection.collection,
                amount = collection.amount,
                maxDistance = collection.maxDistance,
                vector = embeddings.embeddings,
              )
            )
        }
    }

    // Holds Provider -> Collection -> VectorData
    val relatedChunks = mutableMapOf<String, Map<String, List<VectorData>>>()

    // Query each vector provider for the most similar vectors
    providerQueries.forEach { (provider, queries) ->
      val vectorDb = providers.vector[provider]
      try {
        relatedChunks[provider] = vectorDb.query(queries)
      } catch (e: Throwable) {
        LOG.error("Failed to query vector database", e)
      }
    }

    for (collection in collections) {
      val instruction = collection.instruction

      if (relatedChunks[collection.vectorProvider] == null) {
        LOG.warn("No results for collection: {}", collection.collection)
        continue
      }

      val collectionData =
        relatedChunks[collection.vectorProvider]!![collection.collection]?.joinToString("\n") {
          it.content
        }

      collectionData?.let {
        collectionInstructions += "$instruction\n\"\"\n\t$collectionData\n\"\""
      }
    }

    val instructions =
      if (collectionInstructions.isEmpty()) ""
      else
        """The information below denoted by triple quotes is relevant to the prompt, use it to respond to the prompt."
          |${"\"\"\""}
          |$collectionInstructions
          |${"\"\"\""}
          |"""
          .trimMargin()

    return if (instructions.isBlank()) input else "$instructions\n$input"
  }
}
