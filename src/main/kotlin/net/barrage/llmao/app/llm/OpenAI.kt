package net.barrage.llmao.app.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolType
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.llm.ToolDefinition

private const val TITLE_GENERATION_MODEL = "gpt-4"
private val SUPPORTED_MODELS = listOf("gpt-3.5-turbo", "gpt-4", "gpt-4o")

class OpenAI(endpoint: String, apiKey: String) : LlmProvider {
  private val client: OpenAI = OpenAI(token = apiKey, host = OpenAIHost(endpoint))

  override fun id(): String {
    return "openai"
  }

  override suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        maxTokens = config.maxTokens,
        tools = config.tools?.map { it.toOpenAiTool() },
      )

    // TODO: Remove yelling
    return this.client.chatCompletion(chatRequest).choices[0].message.content!!
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>> {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        streamOptions = StreamOptions(true),
      )

    return this.client.chatCompletions(chatRequest).map {
      listOf(
        TokenChunk(
          it.id,
          it.created.toLong(),
          it.choices.firstOrNull()?.delta?.content,
          it.choices.firstOrNull()?.finishReason,
        )
      )
    }
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
