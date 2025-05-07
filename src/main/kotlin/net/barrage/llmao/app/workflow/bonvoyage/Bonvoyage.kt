package net.barrage.llmao.app.workflow.bonvoyage

import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.WorkflowAgent

class Bonvoyage(
  user: User,
  tokenTracker: TokenUsageTracker,
  model: String,
  inferenceProvider: InferenceProvider,
) :
  WorkflowAgent(
    user = user,
    model = model,
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = model, temperature = 0.1),
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = null,
  ) {
  private lateinit var currentContext: String

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override fun context(): String = currentContext

  fun setContext(context: String) {
    currentContext = context
  }
}
