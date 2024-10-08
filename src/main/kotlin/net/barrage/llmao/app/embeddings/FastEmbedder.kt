package net.barrage.llmao.app.embeddings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.core.httpClient

class FastEmbedder(private val endpoint: String) : Embedder {
  private val client: HttpClient = httpClient()

  override fun id(): String {
    return "fembed"
  }

  override suspend fun supportsModel(model: String): Boolean {
    val response = client.get("$endpoint/list")
    val body: Map<String, Int> = response.body()
    return body.keys.contains(model)
  }

  override suspend fun embed(input: String, model: String): List<Double> {
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
