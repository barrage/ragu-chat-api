package net.barrage.llmao.adapters.llm.openai

import com.aallam.openai.api.chat.ChatChoice as OpenAiChatChoice
import com.aallam.openai.api.chat.ChatCompletion as OpenAiChatCompletion
import com.aallam.openai.api.chat.ChatCompletionChunk as OpenAiChatChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.FunctionCall as OpenAiFunctionCall
import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.JsonSchema
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.chat.ToolType
import com.aallam.openai.api.core.FinishReason as OpenAiFinishReason
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI as OpenAIClient
import com.aallam.openai.client.OpenAIHost
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.ChatChoice
import net.barrage.llmao.core.llm.ChatCompletion
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ChatMessageContentPart
import net.barrage.llmao.core.llm.ContentMulti
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.FunctionCall
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.ToolCallChunk
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.token.TokenUsageAmount

private val log = KtorSimpleLogger("adapters.llm.OpenAI")

class OpenAI(endpoint: String, apiKey: String, private val models: List<String>) :
  InferenceProvider {
  private val client: OpenAIClient =
    OpenAIClient(
      token = apiKey,
      host = OpenAIHost(endpoint),
      logging = LoggingConfig(logLevel = LogLevel.Info),
    )

  companion object {
    fun initialize(endpoint: String, apiKey: String, models: List<String>): OpenAI {
      if (models.isEmpty()) {
        throw AppError.internal(
          """Invalid openai configuration; Check your `llm.openai.models` config.
           | At least one model must be specified.
           | If you do not intend to use OpenAI, set the `ktor.features.llm.openai` flag to `false`.
           |"""
            .trimMargin()
        )
      }

      log.info("Initializing OpenAI with models: {}", models.joinToString(", "))

      return OpenAI(endpoint, apiKey, models)
    }
  }

  override fun id(): String = "openai"

  override suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatCompletion {
    val chatRequest =
      ChatCompletionRequest(
        messages = messages.map { it.toOpenAiChatMessage() },
        model = ModelId(config.base.model),
        temperature = config.base.temperature,
        presencePenalty = config.base.presencePenalty,
        maxTokens = config.base.maxTokens,
        tools = config.agent?.tools?.listToolSchemas()?.map { it.toOpenAiTool() },
        responseFormat =
          config.agent?.responseFormat?.let {
            ChatResponseFormat.jsonSchema(
              JsonSchema(name = it.name, schema = it.schema, strict = it.strict)
            )
          },
      )

    return client.chatCompletion(chatRequest).toNativeChatCompletion()
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<ChatMessageChunk> {
    val chatRequest =
      ChatCompletionRequest(
        messages = messages.map { it.toOpenAiChatMessage() },
        streamOptions = StreamOptions(true),
        model = ModelId(config.base.model),
        temperature = config.base.temperature,
        presencePenalty = config.base.presencePenalty,
        maxTokens = config.base.maxTokens,
        tools = config.agent?.tools?.listToolSchemas()?.map { it.toOpenAiTool() },
        responseFormat =
          config.agent?.responseFormat?.let {
            ChatResponseFormat.jsonSchema(
              JsonSchema(name = it.name, schema = it.schema, strict = it.strict)
            )
          },
      )

    return this.client.chatCompletions(chatRequest).map { chunk -> chunk.toNativeMessageChunk() }
  }

  override suspend fun supportsModel(model: String): Boolean = models.contains(model)

  override suspend fun listModels(): List<String> = models
}

fun ToolDefinition.toOpenAiTool(): Tool {
  return Tool(
    type = ToolType.Function,
    function =
      FunctionTool(
        name = function.name,
        description = function.description,
        parameters = Parameters.fromJsonString(Json.encodeToString(function.parameters)),
      ),
  )
}

fun ChatMessage.toOpenAiChatMessage(): OpenAiChatMessage {
  return when (role) {
    "user" ->
      when (content) {
        is ContentSingle -> OpenAiChatMessage.User((content as ContentSingle).content)
        is ContentMulti ->
          OpenAiChatMessage.User(
            (content as ContentMulti).content.map {
              when (it) {
                is ChatMessageContentPart.Text -> TextPart(it.text)
                is ChatMessageContentPart.Image -> ImagePart(it.imageUrl.url, it.imageUrl.detail)
              }
            }
          )

        null -> throw AppError.api(ErrorReason.InvalidParameter, "User message must have content")
      }

    "assistant" -> {

      OpenAiChatMessage.Assistant(
        content?.let {
          assert(it is ContentSingle) { "Assistant message must be ContentSingle" }
          it.text()
        },
        toolCalls =
          toolCalls?.map {
            OpenAiToolCall.Function(
              ToolId(it.id!!),
              OpenAiFunctionCall(nameOrNull = it.name, argumentsOrNull = it.arguments),
            )
          },
      )
    }

    "system" -> {
      assert(content is ContentSingle) { "System message must be ContentSingle" }
      OpenAiChatMessage.System(content!!.text())
    }

    "tool" -> {
      assert(content is ContentSingle) { "Tool message content must be ContentSingle" }
      val callId =
        toolCallId
          ?: throw AppError.api(ErrorReason.InvalidParameter, "Tool message must have tool call id")
      OpenAiChatMessage.Tool(content = content!!.text(), toolCallId = ToolId(callId))
    }

    else -> throw AppError.api(ErrorReason.InvalidParameter, "Invalid message role '$role'")
  }
}

fun OpenAiChatChunk.toNativeMessageChunk(): ChatMessageChunk {
  val choice = choices.firstOrNull()
  return ChatMessageChunk(
    id = id,
    created = created.toLong(),
    content = choice?.delta?.content,
    stopReason = choice?.finishReason?.toNativeFinishReason(),
    toolCalls =
      choice?.delta?.toolCalls?.map {
        ToolCallChunk(
          index = it.index,
          id = it.id?.id,
          function = FunctionCall(it.function?.nameOrNull, it.function?.arguments),
        )
      },
    tokenUsage =
      usage?.let { TokenUsageAmount(it.promptTokens, it.completionTokens, it.totalTokens) },
  )
}

fun OpenAiChatMessage.toNativeChatMessage(finishReason: FinishReason? = null): ChatMessage {
  return ChatMessage(
    role = role.role,
    content = content?.let(::ContentSingle),
    finishReason = finishReason,
    toolCalls =
      toolCalls?.map { toolCall ->
        when (toolCall) {
          is OpenAiToolCall.Function ->
            ToolCallData(
              id = toolCall.id.id,
              name = toolCall.function.name,
              arguments = toolCall.function.arguments,
            )
        }
      },
  )
}

fun OpenAiChatCompletion.toNativeChatCompletion(): ChatCompletion {
  return ChatCompletion(
    id = id,
    created = created,
    choices = choices.map { it.toNativeChatChoice() },
    model = model.id,
    tokenUsage =
      usage?.let { TokenUsageAmount(it.promptTokens, it.completionTokens, it.totalTokens) },
  )
}

fun OpenAiChatChoice.toNativeChatChoice(): ChatChoice {
  return ChatChoice(
    index = index,
    message = message.toNativeChatMessage(finishReason?.toNativeFinishReason()),
    finishReason = finishReason?.toNativeFinishReason(),
  )
}

fun OpenAiFinishReason.toNativeFinishReason(): FinishReason {
  return when (this) {
    OpenAiFinishReason.Stop -> FinishReason.Stop
    OpenAiFinishReason.Length -> FinishReason.Length
    OpenAiFinishReason.FunctionCall -> FinishReason.FunctionCall
    OpenAiFinishReason.ToolCalls -> FinishReason.ToolCalls
    OpenAiFinishReason.ContentFilter -> FinishReason.ContentFilter
    else ->
      throw AppError.api(ErrorReason.InvalidParameter, "Unrecognized Open AI finish reason '$this'")
  }
}
