package net.barrage.llmao.core.workflow.chat

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.core.workflow.WorkflowAgent
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/** Handles LLM interactions for direct prompts. */
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
class ChatWorkflowAgent(private val providers: ProviderState, val agent: WorkflowAgent) {
========
class ChatAgent(private val providers: ProviderState, val agent: WorkflowAgent) {
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt
  /** Start a chat completion stream using the parameters from the WorkflowAgent. */
  suspend fun chatCompletionStream(
    input: List<ChatMessage>,
    useRag: Boolean = true,
    useTools: Boolean = true,
  ): Flow<ChatMessageChunk> {
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
    val messages = input.toMutableList()
========
    val llmInput = input.toMutableList()
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt

    val llm = providers.llm.getProvider(agent.llmProvider)

    if (useRag) {
      val userMessage =
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
        messages.lastOrNull { it.role == "user" }
          ?: throw AppError.internal("No user message in input")

      // Safe to !! because user messages are never created with null content
      userMessage.content = prepareChatPromptWithRag(userMessage.content!!)
    }

    val systemMessage = ChatMessage.system(agent.context)

    val llmInput = mutableListOf(systemMessage, *messages.toTypedArray())
========
        llmInput.lastOrNull { it.role == "user" }
          ?: throw AppError.internal("No user message in input")

      // Safe to !! because user messages are never created with null content
      userMessage.content = executeRetrievalAugmentation(userMessage.content!!)
    }

    llmInput.add(0, ChatMessage.system(agent.context))
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt

    return llm.completionStream(
      llmInput,
      ChatCompletionParameters(
        model = agent.model,
        temperature = agent.temperature,
        tools = if (!useTools) null else agent.tools,
      ),
    )
  }

  suspend fun chatCompletionWithRag(input: List<ChatMessage>): ChatMessage {
    val messages = input.toMutableList()
    val userMessage =
      messages.lastOrNull { it.role == "user" }
        ?: throw AppError.internal("No user message in input")

    // Safe to !! because user messages are never created with null content
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
    userMessage.content = prepareChatPromptWithRag(userMessage.content!!)
========
    userMessage.content = executeRetrievalAugmentation(userMessage.content!!)
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt

    val systemMessage = ChatMessage.system(agent.context)

    val llmInput = mutableListOf(systemMessage, *messages.toTypedArray())

    val llm = providers.llm.getProvider(agent.llmProvider)

    return llm.chatCompletion(llmInput, ChatCompletionParameters(agent.model, agent.temperature))
  }

  suspend fun summarizeConversation(history: List<ChatMessage>): String {
    val llm = providers.llm.getProvider(agent.llmProvider)

    val conversation =
      history.joinToString("\n") {
        val s =
          when (it.role) {
            "user" -> "User"
            "assistant" -> "Assistant"
            "system" -> "System"
            else -> ""
          }
        return@joinToString "$s: ${it.content}"
      }

    val summaryPrompt = agent.instructions.formatSummaryPrompt(conversation)

    val messages = listOf(ChatMessage.user(summaryPrompt))

    val completion =
      llm.chatCompletion(
        messages,
        ChatCompletionParameters(
          model = agent.model,
          temperature = agent.temperature,
          maxTokens = 2000,
        ),
      )

    // Safe to !! because we are not sending any tools in the message, which means the content
    // will never be null
    return completion.content!!
  }

  fun countHistoryTokens(history: List<ChatMessage>): Int {
    val text = history.joinToString("\n") { it.content ?: "" }
    return getEncoder(agent.model).encode(text).size()
  }

<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
  suspend fun prepareChatPromptWithRag(prompt: String): String {
========
  /**
   * Uses the agent's collection setup to retrieve related content from the vector database. Returns
   * a string in the form of:
   * ```
   * <INSTRUCTIONS>
   * <PROMPT>
   * ```
   */
  private suspend fun executeRetrievalAugmentation(prompt: String): String {
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt
    if (agent.collections.isEmpty()) {
      return prompt
    }

    val systemMessage = ChatMessage.system(agent.context)

    LOG.trace("Created system message {}", systemMessage)

    var collectionInstructions = ""

    // Maps providers to lists of CollectionQuery
    val providerQueries = mutableMapOf<String, MutableList<CollectionQuery>>()

    // Embed the input per collection provider and model
    for (collection in agent.collections) {
      val embeddings =
        providers.embedding
          .getProvider(collection.embeddingProvider)
          .embed(prompt, collection.embeddingModel)

      if (!providerQueries.containsKey(collection.vectorProvider)) {
        providerQueries[collection.vectorProvider] =
          mutableListOf(CollectionQuery(collection.name, collection.amount, embeddings))
      } else {
        providerQueries[collection.vectorProvider]!!.add(
          CollectionQuery(collection.name, collection.amount, embeddings)
        )
      }
    }

    // Holds Provider -> Collection -> VectorData
    val relatedChunks = mutableMapOf<String, Map<String, List<VectorData>>>()

    // Query each vector provider for the most similar vectors
    providerQueries.forEach { (provider, queries) ->
      val vectorDb = providers.vector.getProvider(provider)
      relatedChunks[provider] = vectorDb.query(queries)
    }

    for (collection in agent.collections) {
      val instruction = collection.instruction
      // Safe to !! because the providers must be present here if they were mapped above
      val collectionData =
        relatedChunks[collection.vectorProvider]!![collection.name]?.joinToString("\n") {
          it.content
        }

      collectionData?.let {
        collectionInstructions += "$instruction\n\"\"\n\t$collectionData\n\"\""
      }
    }

    val instructions =
      if (collectionInstructions.isEmpty()) ""
      else "Instructions: ${"\"\"\""}\n$collectionInstructions\n${"\"\"\""}"

    return "${instructions}\n$prompt"
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt
========
  }

  suspend fun createTitle(prompt: String, response: String): String {
    val llm = providers.llm.getProvider(agent.llmProvider)
    val titleInstruction = agent.instructions.titleInstruction()
    val userMessage = "PROMPT: $prompt\nRESPONSE: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      llm.chatCompletion(
        messages,
        ChatCompletionParameters(agent.model, agent.temperature, maxTokens = 100),
      )

    // Safe to !! because we are not sending tools to the LLM
    var title = completion.content!!.trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    return title
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt
  }

  private fun getEncoder(llm: String): Encoding {
    val registry = Encodings.newDefaultEncodingRegistry()
    for (type in ModelType.entries) {
      if (type.name == llm) {
        return registry.getEncodingForModel(type)
      }
    }
    throw AppError.api(ErrorReason.InvalidParameter, "Cannot find tokenizer for model '$llm'")
  }
<<<<<<<< HEAD:src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatWorkflowAgent.kt

  suspend fun createTitle(prompt: String, response: String): String {
    val llm = providers.llm.getProvider(agent.llmProvider)
    val titleInstruction = agent.instructions.titleInstruction()
    val userMessage = "PROMPT: $prompt\nRESPONSE: $response"
    val messages = listOf(ChatMessage.system(titleInstruction), ChatMessage.user(userMessage))

    val completion =
      llm.chatCompletion(
        messages,
        ChatCompletionParameters(agent.model, agent.temperature, maxTokens = 100),
      )

    // Safe to !! because we are not sending tools to the LLM
    var title = completion.content!!.trim()

    while (title.startsWith("\"") && title.endsWith("\"") && title.length > 1) {
      title = title.substring(1, title.length - 1)
    }

    LOG.trace("Title generated: {}", title)

    return title
  }
========
>>>>>>>> 78f03af (Add recursive stream calls for tools and fix title generation):src/main/kotlin/net/barrage/llmao/core/workflow/chat/ChatAgent.kt
}
