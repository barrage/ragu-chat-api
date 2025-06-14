package embeddings

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.embedding.Embeddings
import net.barrage.llmao.core.http.httpClient
import net.barrage.llmao.core.string

class FastEmbedder(private val endpoint: String) : Embedder {
  private val client: HttpClient = httpClient()

  companion object {
    fun initialize(config: ApplicationConfig): FastEmbedder {
      return FastEmbedder(config.string("embeddings.fembed.endpoint"))
    }
  }

  override fun id(): String = "fembed"

  private suspend fun getModels(): Map<String, Int> {
    val response = client.get("$endpoint/list")
    return response.body()
  }

  override suspend fun supportsModel(model: String): Boolean {
    val models = getModels()
    return models.keys.contains(model)
  }

  override suspend fun embed(input: String, model: String): Embeddings {
    val request = EmbeddingRequest(listOf(input), model)
    val response =
      client.post("$endpoint/embed") {
        contentType(ContentType.Application.Json)
        setBody(request)
      }
    return Embeddings(response.body<EmbeddingResponse>().embeddings[0])
  }

  override suspend fun vectorSize(model: String): Int {
    val models = getModels()
    return models[model] ?: throw IllegalArgumentException("Model $model not found")
  }
}

@Serializable private data class EmbeddingRequest(val input: List<String>, val model: String)

@Serializable private data class EmbeddingResponse(val embeddings: List<List<Double>>)
