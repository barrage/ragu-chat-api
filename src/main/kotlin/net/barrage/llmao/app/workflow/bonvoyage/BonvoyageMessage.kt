package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.workflow.DefaultWorkflowInput
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.types.KUUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class BonvoyageInput {
  /**
   * Sent by travelers during trips to chat with the agent.
   *
   * Input to [BonvoyageChatAgent].
   */
  @Serializable
  @SerialName("bonvoyage.chat")
  data class ChatInput(val input: DefaultWorkflowInput) : BonvoyageInput()

  /**
   * Sent to upload a business trip expense. Usually a picture of a receipt.
   *
   * Input to [BonvoyageExpenseAgent].
   */
  @Serializable
  @SerialName("bonvoyage.expense.upload")
  data class ExpenseUpload(
    /** Base64 encoded image data. */
    val data: IncomingImageData,

    /** Description of the expense. */
    val description: String? = null,
  ) : BonvoyageInput()

  /**
   * Sent to update an expense entry.
   *
   * System message; does not use agent.
   */
  @Serializable
  @SerialName("bonvoyage.expense.update")
  data class ExpenseUpdate(
    val expenseId: KUUID,
    val properties: BonvoyageTravelExpenseUpdateProperties,
  ) : BonvoyageInput()
}

/**
 * Sent upon successfully registering an expense for the trip.
 *
 * Response to: [BonvoyageInput.ExpenseUpload]
 */
@Serializable
@SerialName("bonvoyage.expense.upload")
data class ExpenseUpload(val data: BonvoyageTravelExpense) : WorkflowOutput()

/**
 * Sent upon successfully updating an expense entry.
 *
 * Response to: [BonvoyageInput.ExpenseUpdate]
 */
@Serializable
@SerialName("bonvoyage.expense.update")
data class ExpenseUpdate(val expense: BonvoyageTravelExpense) : WorkflowOutput()
