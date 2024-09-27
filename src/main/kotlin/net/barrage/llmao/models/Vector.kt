package net.barrage.llmao.models

data class VectorQueryOptions(val collections: List<String>, val amountResults: Int? = null)
