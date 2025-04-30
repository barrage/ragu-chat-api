package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.database.insertMessages
import net.barrage.llmao.core.database.set
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.tables.references.BONVOYAGE_TRAVEL_EXPENSES
import net.barrage.llmao.tables.references.BONVOYAGE_WORKFLOWS
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class TripotronRepository(private val dslContext: DSLContext) {
  suspend fun insertTrip(insert: TripotronInsertTrip) {
    dslContext
      .insertInto(BONVOYAGE_WORKFLOWS)
      .set(BONVOYAGE_WORKFLOWS.ID, insert.id)
      .set(BONVOYAGE_WORKFLOWS.USER_ID, insert.trip.user.id)
      .set(BONVOYAGE_WORKFLOWS.USER_FULL_NAME, insert.trip.user.username)
      .set(BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID, insert.trip.travelOrderId)
      .set(BONVOYAGE_WORKFLOWS.START_LOCATION, insert.trip.startLocation)
      .set(BONVOYAGE_WORKFLOWS.END_LOCATION, insert.trip.endLocation)
      .set(BONVOYAGE_WORKFLOWS.START_DATE_TIME, insert.trip.startDateTime)
      .set(BONVOYAGE_WORKFLOWS.END_DATE_TIME, insert.trip.endDateTime)
      .set(BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE, insert.trip.transportType.name)
      .set(BONVOYAGE_WORKFLOWS.DESCRIPTION, insert.trip.description)
      .set(BONVOYAGE_WORKFLOWS.VEHICLE_TYPE, insert.trip.vehicleType)
      .set(BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION, insert.trip.vehicleRegistration)
      .set(BONVOYAGE_WORKFLOWS.START_MILEAGE, insert.trip.startMileage)
      .awaitSingle()
  }

  suspend fun updateTrip(update: TripotronUpdateTrip) {
    dslContext
      .update(BONVOYAGE_WORKFLOWS)
      .set(update.trip.userFullName, BONVOYAGE_WORKFLOWS.USER_FULL_NAME)
      .set(update.trip.startLocation, BONVOYAGE_WORKFLOWS.START_LOCATION)
      .set(update.trip.endLocation, BONVOYAGE_WORKFLOWS.END_LOCATION)
      .set(update.trip.startDateTime, BONVOYAGE_WORKFLOWS.START_DATE_TIME)
      .set(update.trip.endDateTime, BONVOYAGE_WORKFLOWS.END_DATE_TIME)
      .set(update.trip.description, BONVOYAGE_WORKFLOWS.DESCRIPTION)
      .set(update.trip.vehicleType, BONVOYAGE_WORKFLOWS.VEHICLE_TYPE)
      .set(update.trip.transportType, BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE) { it.name }
      .set(update.trip.vehicleRegistration, BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION)
      .set(update.trip.startMileage, BONVOYAGE_WORKFLOWS.START_MILEAGE)
      .set(update.trip.endMileage, BONVOYAGE_WORKFLOWS.END_MILEAGE)
      .where(BONVOYAGE_WORKFLOWS.ID.eq(update.id))
      .awaitSingle()
  }

  suspend fun getTrip(id: KUUID): TripotronTrip? {
    return dslContext
      .select(
        BONVOYAGE_WORKFLOWS.ID,
        BONVOYAGE_WORKFLOWS.USER_ID,
        BONVOYAGE_WORKFLOWS.USER_FULL_NAME,
        BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID,
        BONVOYAGE_WORKFLOWS.START_LOCATION,
        BONVOYAGE_WORKFLOWS.END_LOCATION,
        BONVOYAGE_WORKFLOWS.START_DATE_TIME,
        BONVOYAGE_WORKFLOWS.END_DATE_TIME,
        BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE,
        BONVOYAGE_WORKFLOWS.DESCRIPTION,
        BONVOYAGE_WORKFLOWS.CREATED_AT,
        BONVOYAGE_WORKFLOWS.UPDATED_AT,
        BONVOYAGE_WORKFLOWS.VEHICLE_TYPE,
        BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION,
        BONVOYAGE_WORKFLOWS.START_MILEAGE,
        BONVOYAGE_WORKFLOWS.END_MILEAGE,
      )
      .where(BONVOYAGE_WORKFLOWS.ID.eq(id))
      .awaitSingle()
      ?.into(BONVOYAGE_WORKFLOWS)
      ?.toTrip()
  }

  suspend fun listTrips(): List<TripotronTrip> {
    return dslContext
      .select(
        BONVOYAGE_WORKFLOWS.ID,
        BONVOYAGE_WORKFLOWS.USER_ID,
        BONVOYAGE_WORKFLOWS.USER_FULL_NAME,
        BONVOYAGE_WORKFLOWS.TRAVEL_ORDER_ID,
        BONVOYAGE_WORKFLOWS.START_LOCATION,
        BONVOYAGE_WORKFLOWS.END_LOCATION,
        BONVOYAGE_WORKFLOWS.START_DATE_TIME,
        BONVOYAGE_WORKFLOWS.END_DATE_TIME,
        BONVOYAGE_WORKFLOWS.TRANSPORT_TYPE,
        BONVOYAGE_WORKFLOWS.DESCRIPTION,
        BONVOYAGE_WORKFLOWS.CREATED_AT,
        BONVOYAGE_WORKFLOWS.UPDATED_AT,
        BONVOYAGE_WORKFLOWS.VEHICLE_TYPE,
        BONVOYAGE_WORKFLOWS.VEHICLE_REGISTRATION,
        BONVOYAGE_WORKFLOWS.START_MILEAGE,
        BONVOYAGE_WORKFLOWS.END_MILEAGE,
      )
      .from(BONVOYAGE_WORKFLOWS)
      .asFlow()
      .map { it.into(BONVOYAGE_WORKFLOWS).toTrip() }
      .toList()
  }

  suspend fun insertTripExpense(tripId: KUUID, expense: TravelExpense) {
    dslContext
      .insertInto(BONVOYAGE_TRAVEL_EXPENSES)
      .set(BONVOYAGE_TRAVEL_EXPENSES.ID, expense.id)
      .set(BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID, tripId)
      .set(BONVOYAGE_TRAVEL_EXPENSES.AMOUNT, expense.amount)
      .set(BONVOYAGE_TRAVEL_EXPENSES.CURRENCY, expense.currency)
      .set(BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH, expense.imagePath)
      .set(BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER, expense.imageProvider)
      .set(BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION, expense.description)
      .awaitSingle()
  }

  suspend fun listTripExpenses(tripId: KUUID): List<TripotronTravelExpense> {
    return dslContext
      .select(
        BONVOYAGE_TRAVEL_EXPENSES.ID,
        BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID,
        BONVOYAGE_TRAVEL_EXPENSES.AMOUNT,
        BONVOYAGE_TRAVEL_EXPENSES.CURRENCY,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PATH,
        BONVOYAGE_TRAVEL_EXPENSES.IMAGE_PROVIDER,
        BONVOYAGE_TRAVEL_EXPENSES.DESCRIPTION,
        BONVOYAGE_TRAVEL_EXPENSES.VERIFIED,
        BONVOYAGE_TRAVEL_EXPENSES.CREATED_AT,
        BONVOYAGE_TRAVEL_EXPENSES.UPDATED_AT,
      )
      .from(BONVOYAGE_TRAVEL_EXPENSES)
      .where(BONVOYAGE_TRAVEL_EXPENSES.WORKFLOW_ID.eq(tripId))
      .asFlow()
      .map { it.into(BONVOYAGE_TRAVEL_EXPENSES).toTravelExpense() }
      .toList()
  }

  suspend fun insertMessages(workflowId: KUUID, messages: List<MessageInsert>): KUUID =
    dslContext.transactionCoroutine { ctx -> ctx.dsl().insertMessages(workflowId, messages) }
}
