package net.barrage.llmao.app.embeddings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.core.httpClient
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private enum class FembedModel(val value: String) {
  AllMiniL6("Qdrant/all-MiniLM-L6-v2-onnx"),
  AllMiniL12("Xenova/all-MiniLM-L12-v2"),
  BgeBase("Xenova/bge-base-en-v1.5"),
  BgeLarge("Xenova/bge-large-en-v1.5"),
  BgeSmall("Xenova/bge-small-en-v1.5");

  companion object {
    fun tryFromString(value: String): FembedModel? {
      return when (value) {
        AllMiniL6.value -> AllMiniL6
        AllMiniL12.value -> AllMiniL12
        BgeBase.value -> BgeBase
        BgeLarge.value -> BgeLarge
        BgeSmall.value -> BgeSmall
        else -> null
      }
    }
  }
}

// TODO: Grab models from remote instance and store them to check for validity instead of using
// enum.
class FastEmbedder(private val endpoint: String) : Embedder {
  private val client: HttpClient = httpClient()

  override fun id(): String {
    return "fembed"
  }

  override fun supportsModel(model: String): Boolean {
    return FembedModel.tryFromString(model) != null
  }

  override suspend fun embed(input: String, model: String): List<Double> {
    FembedModel.tryFromString(model)
      ?: throw AppError.api(
        ErrorReason.InvalidParameter,
        "Embedder '${id()}' does not support model '$model'",
      )
    val request = EmbeddingRequest(listOf(input), model)
    val response =
      client.post("$endpoint/embed") {
        contentType(ContentType.Application.Json)
        setBody(request)
      }
    return response.body<EmbeddingResponse>().embeddings[0]
  }
}

@Serializable private data class EmbeddingRequest(val content: List<String>, val model: String)

@Serializable private data class EmbeddingResponse(val embeddings: List<List<Double>>)
