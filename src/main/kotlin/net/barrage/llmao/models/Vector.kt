package net.barrage.llmao.models

data class VectorQueryOptions(
    val collection: String,
    val fields: String,
    val nResults: Int? = null,
    val where: Any? = null,
    val distanceFilter: Double? = null
)

val DOCUMENTATION = VectorQueryOptions(
    collection = "EdTechDocumentation",
    fields = "content parentDocumentId fileName fileType title section page",
    nResults = 5,
    where = null,
    distanceFilter = null
)