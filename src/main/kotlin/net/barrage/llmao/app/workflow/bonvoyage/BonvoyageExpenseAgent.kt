package net.barrage.llmao.app.workflow.bonvoyage

import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.WorkflowAgent

class BonvoyageExpenseAgent(
  user: User,
  tokenTracker: TokenUsageTracker,
  model: String,
  inferenceProvider: InferenceProvider,
  private val trip: BonvoyageTrip,
) :
  WorkflowAgent(
    id = BONVOYAGE_EXPENSE_AGENT_ID,
    user = user,
    model = model,
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = model, temperature = 0.1),
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = null,
  ) {
  override fun context(): String {
    val username = user.username
    return """You are talking to $username.
          | $username is on a business trip from ${trip.startLocation} to ${trip.endLocation}.
          | The trip start time is ${trip.startDateTime} and the end time is ${trip.endDateTime}.
          | The description of the trip states the following:
          |
          | "${trip.description}"
          |
          | You keep track of expenses for this trip for the purpose of creating a trip report.
          | The user will send you pictures they took of receipts of expenses made on this trip.
          | They will also optionally provide you a description of the expense.
          |
          | You will extract the following information from the receipt image:
          | - The amount of money spent on the expense.
          | - The currency of the expense.
          | - The description of the expense. If the user provides a description, use it. Otherwise, attempt to describe it based on the image of the receipt.
          | - The date-time the expense was created at.
          |
          | You will output the extracted information in JSON format, using the schema ${EXPENSE_FORMAT.name}.
      """
      .trimMargin()
  }
}
