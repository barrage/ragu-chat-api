package net.barrage.llmao.app.llm

import com.aallam.openai.api.core.FinishReason
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.types.KOffsetDateTime

class Ollama(private val endpoint: String) : LlmProvider {
  private val client: HttpClient = httpClient()

  override fun id(): String {
    return "ollama"
  }

  override suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String {
    val request =
      ChatRequest(
        config.model,
        messages,
        CompletionRequestOptions(
          config.temperature,
          maxTokens = config.maxTokens,
          tools = config.tools,
        ),
        false,
      )
    val response =
      client.post("$endpoint/api/chat") {
        contentType(ContentType.Application.Json)
        setBody(request)
      }
    val body = response.body<ChatCompletionResponse>()
    return body.message.content
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>> {
    val request =
      ChatRequest(
        config.model,
        messages,
        CompletionRequestOptions(
          config.temperature,
          maxTokens = config.maxTokens,
          tools = config.tools,
        ),
        true,
      )
    val response =
      client.post("$endpoint/api/chat") {
        contentType(ContentType.Application.Json)
        setBody(request)
      }

    return flow {
      val channel: ByteReadChannel = response.bodyAsChannel()

      val buffer = ByteArray(4096)

      while (!channel.isClosedForRead) {
        // Read them as they come
        val bytesRead = channel.readAvailable(buffer)

        if (bytesRead == 0) {
          continue
        }

        // Decode them to a string
        val chunks = buffer.decodeToString(0, bytesRead)

        // Chunks come in newline delimited JSONs
        val chunkStream = chunks.split("\n")

        val chunkBuffer = mutableListOf<TokenChunk>()
        var offset = 0

        for (chunk in chunkStream) {
          try {
            val streamChunk: ChatStreamChunk = Json.decodeFromString(chunk)

            // Parsing is successful, add chunk length to offset
            offset += chunk.length

            // Add token chunk to the chunk buffer
            chunkBuffer.add(streamChunk.toTokenChunk())
          } catch (e: SerializationException) {
            // Parsing unsuccessful, reset buffer to end of valid JSON
            buffer.copyInto(buffer, startIndex = offset)
          }
        }

        if (chunkBuffer.isNotEmpty()) {
          emit(chunkBuffer)
        }
      }
    }
  }

  override suspend fun supportsModel(model: String): Boolean {
    val response = client.get("$endpoint/api/tags")
    val models = response.body<ModelResponse>()
    return models.models.map(ModelInfo::name).contains(model)
  }

  override suspend fun listModels(): List<String> {
    val response = client.get("$endpoint/api/tags")
    val models = response.body<ModelResponse>()
    return models.models.map(ModelInfo::name)
  }
}

@Serializable
private data class CompletionRequest(
  val model: String,
  val prompt: String,
  val options: CompletionRequestOptions,
  val stream: Boolean,
)

@Serializable
private data class ChatRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val options: CompletionRequestOptions,
  val stream: Boolean,
)

@Serializable
private data class CompletionRequestOptions(
  val temperature: Double,
  val maxTokens: Int? = null,
  val tools: List<ToolDefinition>? = null,
)

@Serializable
private data class ChatCompletionResponse(
  val model: String,
  @SerialName("created_at") val createdAt: KOffsetDateTime,
  val message: ChatMessage,
  val done: Boolean,
  @SerialName("total_duration") val totalDuration: Long,
  @SerialName("load_duration") val loadDuration: Long,
  @SerialName("prompt_eval_count") val promptEvalCount: Long,
  @SerialName("prompt_eval_duration") val promptEvalDuration: Long,
  @SerialName("eval_count") val evalCount: Long,
  @SerialName("eval_duration") val evalDuration: Long,
)

@Serializable
private data class CompletionResponse(
  val model: String,
  @SerialName("created_at") val createdAt: KOffsetDateTime,
  val response: String,
  val done: Boolean,
  @SerialName("total_duration") val totalDuration: Long,
  @SerialName("load_duration") val loadDuration: Long,
  @SerialName("prompt_eval_count") val promptEvalCount: Long,
  @SerialName("prompt_eval_duration") val promptEvalDuration: Long,
  @SerialName("eval_count") val evalCount: Long,
  @SerialName("eval_duration") val evalDuration: Long,
)

@Serializable private data class ModelResponse(val models: List<ModelInfo>)

@Serializable
private data class ModelInfo(
  val name: String,
  @SerialName("modified_at") val modifiedAt: String,
  val size: Long,
  val digest: String,
  val details: ModelDetails,
)

@Serializable
private data class ModelDetails(
  val format: String,
  val family: String,
  val families: List<String>?,
  @SerialName("parameter_size") val parameterSize: String,
  @SerialName("quantization_level") val quantizationLevel: String,
)

@Serializable
private data class ChatStreamChunk(
  val model: String,
  @SerialName("created_at") val createdAt: KOffsetDateTime,
  val message: ChatMessage,
  val done: Boolean,
) {
  fun toTokenChunk(): TokenChunk {
    return TokenChunk(
      "ID",
      createdAt.toEpochSecond(),
      message.content,
      if (done) {
        FinishReason.Stop
      } else {
        null
      },
    )
  }
}
