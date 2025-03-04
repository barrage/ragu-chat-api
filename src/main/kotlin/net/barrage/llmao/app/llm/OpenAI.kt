package net.barrage.llmao.app.llm

import com.aallam.openai.api.chat.ChatChoice as OpenAiChatChoice
import com.aallam.openai.api.chat.ChatCompletion as OpenAiChatCompletion
import com.aallam.openai.api.chat.ChatCompletionChunk as OpenAiChatChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage
import com.aallam.openai.api.chat.FunctionCall as OpenAiFunctionCall
import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.chat.ToolType
import com.aallam.openai.api.core.FinishReason as OpenAiFinishReason
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.ChatChoice
import net.barrage.llmao.core.llm.ChatCompletion
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.FunctionCall
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.ToolCallChunk
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val SUPPORTED_MODELS = listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o")

class OpenAI(endpoint: String, apiKey: String) : LlmProvider {
  private val client: OpenAI = OpenAI(token = apiKey, host = OpenAIHost(endpoint))

  override fun id(): String {
    return "openai"
  }

  override suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatMessage {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        maxTokens = config.maxTokens,
        tools = config.tools?.map { it.toOpenAiTool() },
      )

    return client.chatCompletion(chatRequest).toNativeChatCompletion().choices.first().message
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<ChatMessageChunk> {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        streamOptions = StreamOptions(true),
        tools = config.tools?.map { it.toOpenAiTool() },
      )

    return this.client.chatCompletions(chatRequest).map { chunk -> chunk.toNativeMessageChunk() }
  }

  override suspend fun supportsModel(model: String): Boolean {
    return SUPPORTED_MODELS.contains(model)
  }

  override suspend fun listModels(): List<String> {
    return SUPPORTED_MODELS
  }
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
      OpenAiChatMessage.User(
        content ?: throw AppError.api(ErrorReason.InvalidParameter, "User message content is null")
      )
    "assistant" ->
      OpenAiChatMessage.Assistant(
        content,
        toolCalls =
          toolCalls?.map {
            OpenAiToolCall.Function(
              ToolId(it.id!!),
              OpenAiFunctionCall(nameOrNull = it.name, argumentsOrNull = it.arguments),
            )
          },
      )
    "system" -> OpenAiChatMessage.System(content)
    "tool" -> {
      val callId =
        toolCallId
          ?: throw AppError.api(ErrorReason.InvalidParameter, "Tool message must have tool call id")
      OpenAiChatMessage.Tool(content = content, toolCallId = ToolId(callId))
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
  )
}

fun OpenAiChatMessage.toNativeChatMessage(): ChatMessage {
  return ChatMessage(
    role = role.role,
    content = content ?: throw AppError.api(ErrorReason.InvalidParameter, "Message content is null"),
  )
}

fun OpenAiChatCompletion.toNativeChatCompletion(): ChatCompletion {
  return ChatCompletion(
    id = id,
    created = created,
    choices = choices.map { it.toNativeChatChoice() },
    model = model.id,
  )
}

fun OpenAiChatChoice.toNativeChatChoice(): ChatChoice {
  return ChatChoice(
    index = index,
    message = message.toNativeChatMessage(),
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
