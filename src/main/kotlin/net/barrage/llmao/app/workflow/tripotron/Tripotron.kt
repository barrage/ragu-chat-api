package net.barrage.llmao.app.workflow.tripotron

import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.WorkflowAgent

class Tripotron(
  user: User,
  tokenTracker: TokenUsageTracker,
  model: String,
  inferenceProvider: InferenceProvider,
  tools: List<ToolDefinition>?,
  /** The trip around which the workflow is centered. */
  private val trip: TripDetails,
) :
  WorkflowAgent(
    user = user,
    model = model,
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionParameters(model = model, temperature = 0.1),
    tokenTracker = tokenTracker,
    tools = tools,
    contextEnrichment = null,
    history = null,
  ) {
  private lateinit var currentContext: String

  override fun id(): String = TRIPOTRON_WORKFLOW_ID

  override fun context(): String {
    return currentContext
  }
}
