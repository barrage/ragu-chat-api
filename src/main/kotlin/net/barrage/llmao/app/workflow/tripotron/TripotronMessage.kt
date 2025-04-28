package net.barrage.llmao.app.workflow.tripotron

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.types.KUUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class TripotronInput {
  /** Represents a business trip expense. Usually a picture of a receipt. */
  @Serializable
  @SerialName("expense.upload")
  data class ExpenseUpload(
    /** Base64 encoded image data. */
    val data: IncomingImageData,

    /** Description of the expense. */
    val description: String,
  ) : TripotronInput()

  @Serializable @SerialName("expense.list") data object ExpenseList : TripotronInput()

  @Serializable
  @SerialName("expense.verify")
  data class ExpenseVerify(val expenseId: KUUID) : TripotronInput()

  @Serializable @SerialName("trip.finalize") data object Finalize : TripotronInput()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class TripotronOutput {
  @Serializable
  @SerialName("expense.registered")
  data class ExpenseRegistered(val data: TravelExpense)
}

@Serializable
enum class TransportType {
  /** The trip is performed with public transport. */
  Public,

  /**
   * The trip is performed with a personal vehicle. This mandates that start and end mileage be
   * provided along with the vehicle's registration plates.
   */
  Personal,
}
