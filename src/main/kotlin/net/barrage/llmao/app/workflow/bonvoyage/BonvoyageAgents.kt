package net.barrage.llmao.app.workflow.bonvoyage

import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.WorkflowAgent

class BonvoyageExpenseAgent(
  tokenTracker: TokenUsageTracker,
  model: String,
  inferenceProvider: InferenceProvider,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = model, temperature = 0.1),
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = null,
  )

class BonvoyageChatAgent(
  model: String,
  inferenceProvider: InferenceProvider,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = model),
    tokenTracker = tokenTracker,
    history = history,
    contextEnrichment = null,
  )
