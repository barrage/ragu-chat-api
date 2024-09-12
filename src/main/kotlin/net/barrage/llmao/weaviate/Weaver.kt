package net.barrage.llmao.weaviate

import io.ktor.server.plugins.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.batch.model.BatchDeleteResponse
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import io.weaviate.client.v1.schema.model.WeaviateClass
import net.barrage.llmao.models.*
import java.util.*

class Weaver(config: WeaviateConfig) {

    private val client: WeaviateClient = WeaviateClient(Config(config.scheme, config.host))

    fun deleteVectors(documentId: String, collection: String): BatchDeleteResponse {
        val whereFilter = WhereFilter.builder()
            .path(listOf("documentId").toString())
            .operator(Operator.Equal)
            .valueText(documentId)
            .build()

        val result = client.batch().objectsBatchDeleter()
            .withClassName(collection)
            .withWhere(whereFilter)
            .run()

        if (result.hasErrors()) {
            val errorMessage = result.error.messages.joinToString(", ")
            throw BadRequestException("Failed to count items: $errorMessage")
        }

        return result.result ?: throw RuntimeException("Failed to delete vectors")
    }

    fun countItems(collection: String): Double? {
        val countField = Field.builder()
            .name("meta")
            .fields(
                Field.builder().name("count").build()
            ).build()

        val result = client.graphQL().aggregate()
            .withClassName(collection)
            .withFields(countField)
            .run()

        if (result.hasErrors()) {
            val errorMessage = result.error.messages.joinToString(", ")
            throw BadRequestException("Failed to count items: $errorMessage")
        }

        val graphQLResponse = result.result

        val aggregate = (graphQLResponse.data as? Map<*, *>)?.get("Aggregate") as? Map<*, *>

        val collectionList = (aggregate?.get(collection) as? List<*>)

        val firstItem = collectionList?.getOrNull(0) as? Map<*, *>

        return (firstItem?.get("meta") as? Map<*, *>)?.get("count") as? Double
    }

    fun createCollection(clazz: WeaviateClass): WeaviateClass {
        val weaviateClass: WeaviateClass = WeaviateClass.builder()
            .className(clazz.className)
            .build()

        val result = client.schema().classCreator()
            .withClass(weaviateClass)
            .run()

        if (result.hasErrors()) {
            val errorMessage = result.error.messages.joinToString(", ")
            throw BadRequestException("Failed to create collection: $errorMessage")
        }
        return weaviateClass
    }


    fun deleteCollection(collection: String) {
        val result = client.schema().classDeleter().withClassName(collection).run()

        if (result.hasErrors()) {
            val errorMessage = result.error.messages.joinToString(", ")
            throw BadRequestException("Failed to delete collection: $errorMessage")
        }
    }

    fun collectionInfo(collection: String): WeaviateClass {
        val result = client.schema().classGetter().withClassName(collection).run()

        if (result.hasErrors()) {
            val errorMessage = result.error.messages.joinToString(", ")
            throw BadRequestException("Failed to get collections info: $errorMessage")
        }

        return result.result
    }

    fun query(embeddings: List<Int>, options: VectorQueryOptions): List<DocumentChunk> {
        val fields = options.fields.split(",").map { fieldName ->
            Field.builder().name(fieldName.trim()).build()
        }

        var query = client.graphQL().get()
            .withFields(*fields.toTypedArray())
            .withClassName(options.collection)
            .withNearVector(NearVectorArgument.builder().vector(embeddings.map { it.toFloat() }.toTypedArray()).distance(options.distanceFilter?.toFloat()).build())
            .withLimit(options.nResults)


        if (options.where != null) {
            val where = WhereFilter.builder().path().valueText(options.where.toString()).build()
            query = query.withWhere(where)
        }

        val queryResult = query.run()

        if (queryResult.hasErrors()) {
            val errorMessage = queryResult.error.messages.joinToString(", ")
            throw BadRequestException("Failed to query: $errorMessage")
        }

        val get = (queryResult.result.data as? Map<*, *>)?.get("Get") as? Map<*, *>

        val documentChunks = when (val collection = get?.get(options.collection) as? List<*>) {
            is List<*> -> collection.mapNotNull { item ->
                if (item is Map<*, *>) {
                    val content = item["content"] as? String
                    val fileName = item["fileName"] as? String
                    val fileTypeStr = item["fileType"] as? String
                    val fileType = fileTypeStr?.let { FileType.valueOf(it) }

                    if (content != null && fileName != null && fileType != null) {
                        val metadata = DocumentChunkMeta(fileName, fileType)
                        DocumentChunk(content, metadata)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            else -> emptyList()
        }

        return documentChunks
    }

    fun feedWithMetadata(
        documentId: String,
        collection: String,
        chunks: List<DocumentChunk>,
        embeddings: List<List<Int>>
    ): List<String> {
        require(chunks.size == embeddings.size) { "Mismatch in chunks <=> embeddings" }

        val batch = client.batch().objectsBatcher()
        val ids = mutableListOf<String>()

        for (i in embeddings.indices) {
            val chunk = chunks[i]
            val embedding = embeddings[i]
            val id = UUID.randomUUID().toString()

            val metadataMap = chunk.metadata.toMap()
            val vector = embedding.map { it.toFloat() }.toTypedArray()


            val obj = WeaviateObject.builder().id(id).vector(vector).className(collection).properties(
                mapOf(
                    "content" to chunk.content,
                    "documentId" to documentId
                ) + metadataMap
            ).build()

            ids.add(id)
            batch.withObject(obj)
        }

        batch.run()

        return ids
    }
}

