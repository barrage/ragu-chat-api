package net.barrage.llmao.weaviate

import io.weaviate.client.v1.schema.model.WeaviateClass
import io.weaviate.client.v1.schema.model.Property

enum class WeaviateClassName(val className: String) {
    DOCUMENTATION("Documentation")
}

fun createDocumentation(): WeaviateClass {
    val contentProperty = Property.builder().name("text").dataType(listOf("content")).description("Chunk content").build()
    val documentIdProperty = Property.builder().name("text").dataType(listOf("documentId")).description("UUID of the document stored in the DB").build()
    val fileNameProperty = Property.builder().name("text").dataType(listOf("fileName")).description("Original file name").build()
    val fileTypeProperty = Property.builder().name("text").dataType(listOf("fileType")).description("Original file type").build()

    return WeaviateClass.builder()
        .className(WeaviateClassName.DOCUMENTATION.className)
        .description("Documentation for RAG")
        .properties(
            listOf(
                contentProperty,
                documentIdProperty,
                fileNameProperty,
                fileTypeProperty
            )
        )
        .build()
}

val Documentation = createDocumentation()
