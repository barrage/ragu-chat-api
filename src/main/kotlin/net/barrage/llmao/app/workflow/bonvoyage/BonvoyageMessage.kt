package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.types.KUUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class BonvoyageInput {
  /** Sent to upload a business trip expense. Usually a picture of a receipt. */
  @Serializable
  @SerialName("expense.upload")
  data class ExpenseUpload(
    /** Base64 encoded image data. */
    val data: IncomingImageData,

    /** Description of the expense. */
    val description: String,
  ) : BonvoyageInput()

  /** Sent to update an expense entry. */
  @Serializable
  @SerialName("expense.update")
  data class ExpenseUpdate(
    val expenseId: KUUID,
    val properties: BonvoyageTravelExpenseUpdateProperties,
  ) : BonvoyageInput()

  /** Sent to request an aggregate of a trip and all its expenses. */
  @Serializable @SerialName("trip.summary") data object TripSummary : BonvoyageInput()

  /** Sent to generate a trip report. */
  @Serializable @SerialName("trip.report") data object TripGenerateReport : BonvoyageInput()

  /** Sent to finalize the trip and make it read only. */
  @Serializable @SerialName("trip.finalize") data object TripFinalize : BonvoyageInput()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class BonvoyageOutput {
  /**
   * Sent upon successfully registering an expense for the trip.
   *
   * Response to: [BonvoyageInput.ExpenseUpload]
   */
  @Serializable
  @SerialName("expense.upload")
  data class ExpenseUpload(val data: BonvoyageTravelExpense) : BonvoyageOutput()

  /** Response to: [BonvoyageInput.TripSummary] */
  @Serializable
  @SerialName("trip.summary")
  data class TripSummary(val data: BonvoyageTripAggregate) : BonvoyageOutput()

  /**
   * Sent upon successfully updating an expense entry.
   *
   * Response to: [BonvoyageInput.ExpenseUpdate]
   */
  @Serializable
  @SerialName("expense.update")
  data class ExpenseUpdate(val expense: BonvoyageTravelExpense) : BonvoyageOutput()

  /**
   * Sent upon successful report generation.
   *
   * Response to: [BonvoyageInput.TripGenerateReport]
   */
  @Serializable @SerialName("trip.report") data object TripReport : BonvoyageOutput()

  /**
   * Sent upon receiving a request to finalize the trip when there are unverified expenses.
   *
   * Response to: [BonvoyageInput.TripFinalize]
   */
  @Serializable
  @SerialName("trip.finalize.expenses_unverified")
  data class FinalizeTripExpensesUnverified(val expenses: List<BonvoyageTravelExpense>) :
    BonvoyageOutput()
}
