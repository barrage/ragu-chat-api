package net.barrage.llmao.core.embedding

import net.barrage.llmao.core.token.TokenUsageAmount

data class Embeddings(val embeddings: List<Double>, val usage: TokenUsageAmount? = null)
