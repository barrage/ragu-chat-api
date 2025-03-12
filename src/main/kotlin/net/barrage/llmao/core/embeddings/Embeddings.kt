package net.barrage.llmao.core.embeddings

import net.barrage.llmao.core.tokens.TokenUsageAmount

data class Embeddings(val embeddings: List<Double>, val usage: TokenUsageAmount? = null)
