package net.barrage.llmao.models

data class VectorQueryOptions(
    val collection: String,
    val fields: String,
    val nResults: Int? = null,
    val where: Any? = null,
    val distanceFilter: Double? = null
)