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

class BonvoyageWelcomeAgent(
  tokenTracker: TokenUsageTracker,
  model: String,
  inferenceProvider: InferenceProvider,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = model, temperature = 0.2),
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = null,
  ) {
  suspend fun welcomeMessage(trip: BonvoyageTrip, default: String): String =
    try {
      completion(
          "You are a travel chat assistant who is helping ${trip.userFullName} on his business trip from ${trip.startLocation} to ${trip.endLocation}.",
          """Create a short and friendly welcome message that summarizes the following trip:
          |Trip start location: ${trip.startLocation}
          |Trip end location: ${trip.endLocation}
          |Trip stops: ${trip.stops.joinToString(" -\n")}
          |Trip anticipated start time: ${trip.startDateTime}
          |Trip anticipated end time: ${trip.endDateTime}
          |Trip description: ${trip.description}
        """
            .trimMargin(),
        )
        .last()
        .content!!
        .text()
    } catch (e: Exception) {
      log.error("Failed to generate welcome message", e)
      default
    }
}
