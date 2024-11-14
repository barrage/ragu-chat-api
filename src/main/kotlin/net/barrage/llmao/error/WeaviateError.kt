package net.barrage.llmao.error

import io.weaviate.client.base.WeaviateError
import io.weaviate.client.v1.graphql.model.GraphQLError
import net.barrage.llmao.app.vector.LOG

class WeaviateError {
  companion object {
    fun requestError(weaviateError: WeaviateError): AppError {
      LOG.error(
        "Weaviate request error: ${weaviateError.messages.first().message}, with status: ${weaviateError.statusCode}"
      )
      return AppError.internal()
    }

    fun mappingError(weaviateError: String): AppError {
      LOG.error("Weaviate mapping error: $weaviateError")
      return AppError.internal()
    }

    fun queryError(weaviateError: String): AppError {
      LOG.error("Weaviate query error: $weaviateError")
      return AppError.internal()
    }

    fun queryError(weaviateError: Array<GraphQLError>): AppError {
      for (error in weaviateError) {
        if (error.message.contains("GetObjectsObj")) {
          error.message.split("\"")[1].let { collectionName ->
            LOG.error("Weaviate query error: Collection $collectionName not found")
          }
        } else if (error.message.contains("vector lengths don't match")) {
          LOG.error("Weaviate query error: vector lengths don't match")
        } else if (error.message.contains("content")) {
          LOG.error("Weaviate query error: collection has no content")
        } else {
          LOG.error("Weaviate query error: ${error.message}")
        }
      }
      return AppError.internal()
    }

    fun requestErrorValidateCollection(weaviateError: WeaviateError) {
      LOG.error(
        "Weaviate request error: ${weaviateError.messages.first().message}, with status: ${weaviateError.statusCode}"
      )
    }

    fun schemaError(weaviateError: String) {
      LOG.error("Weaviate schema error: $weaviateError")
    }

    fun vectorSizeError(weaviateError: String) {
      LOG.error("Weaviate vector size error: $weaviateError")
    }
  }
}
