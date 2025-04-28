package net.barrage.llmao.app.workflow.tripotron

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.set
import net.barrage.llmao.tables.references.TRIPOTRON_TRAVEL_EXPENSES
import net.barrage.llmao.tables.references.TRIPOTRON_WORKFLOWS
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext

class TripotronRepository(private val dslContext: DSLContext) {
  suspend fun insertTrip(insert: TripotronInsertTrip) {
    dslContext
      .insertInto(TRIPOTRON_WORKFLOWS)
      .set(TRIPOTRON_WORKFLOWS.ID, insert.id)
      .set(TRIPOTRON_WORKFLOWS.USER_ID, insert.trip.user.id)
      .set(TRIPOTRON_WORKFLOWS.USER_FULL_NAME, insert.trip.user.username)
      .set(TRIPOTRON_WORKFLOWS.TRAVEL_ORDER_ID, insert.trip.travelOrderId)
      .set(TRIPOTRON_WORKFLOWS.START_LOCATION, insert.trip.startLocation)
      .set(TRIPOTRON_WORKFLOWS.END_LOCATION, insert.trip.endLocation)
      .set(TRIPOTRON_WORKFLOWS.START_DATE_TIME, insert.trip.startDateTime)
      .set(TRIPOTRON_WORKFLOWS.END_DATE_TIME, insert.trip.endDateTime)
      .set(TRIPOTRON_WORKFLOWS.TRANSPORT_TYPE, insert.trip.transportType.name)
      .set(TRIPOTRON_WORKFLOWS.DESCRIPTION, insert.trip.description)
      .set(TRIPOTRON_WORKFLOWS.VEHICLE_TYPE, insert.trip.vehicleType)
      .set(TRIPOTRON_WORKFLOWS.VEHICLE_REGISTRATION, insert.trip.vehicleRegistration)
      .set(TRIPOTRON_WORKFLOWS.START_MILEAGE, insert.trip.startMileage)
      .awaitSingle()
  }

  suspend fun updateTrip(update: TripotronUpdateTrip) {
    dslContext
      .update(TRIPOTRON_WORKFLOWS)
      .set(update.trip.userFullName, TRIPOTRON_WORKFLOWS.USER_FULL_NAME)
      .set(update.trip.startLocation, TRIPOTRON_WORKFLOWS.START_LOCATION)
      .set(update.trip.endLocation, TRIPOTRON_WORKFLOWS.END_LOCATION)
      .set(update.trip.startDateTime, TRIPOTRON_WORKFLOWS.START_DATE_TIME)
      .set(update.trip.endDateTime, TRIPOTRON_WORKFLOWS.END_DATE_TIME)
      .set(update.trip.description, TRIPOTRON_WORKFLOWS.DESCRIPTION)
      .set(update.trip.vehicleType, TRIPOTRON_WORKFLOWS.VEHICLE_TYPE)
      .set(update.trip.transportType, TRIPOTRON_WORKFLOWS.TRANSPORT_TYPE) { it.name }
      .set(update.trip.vehicleRegistration, TRIPOTRON_WORKFLOWS.VEHICLE_REGISTRATION)
      .set(update.trip.startMileage, TRIPOTRON_WORKFLOWS.START_MILEAGE)
      .set(update.trip.endMileage, TRIPOTRON_WORKFLOWS.END_MILEAGE)
      .where(TRIPOTRON_WORKFLOWS.ID.eq(update.id))
      .awaitSingle()
  }

  suspend fun getTrip(id: KUUID): TripotronTrip? {
    return dslContext
      .select(
        TRIPOTRON_WORKFLOWS.ID,
        TRIPOTRON_WORKFLOWS.USER_ID,
        TRIPOTRON_WORKFLOWS.USER_FULL_NAME,
        TRIPOTRON_WORKFLOWS.TRAVEL_ORDER_ID,
        TRIPOTRON_WORKFLOWS.START_LOCATION,
        TRIPOTRON_WORKFLOWS.END_LOCATION,
        TRIPOTRON_WORKFLOWS.START_DATE_TIME,
        TRIPOTRON_WORKFLOWS.END_DATE_TIME,
        TRIPOTRON_WORKFLOWS.TRANSPORT_TYPE,
        TRIPOTRON_WORKFLOWS.DESCRIPTION,
        TRIPOTRON_WORKFLOWS.CREATED_AT,
        TRIPOTRON_WORKFLOWS.UPDATED_AT,
        TRIPOTRON_WORKFLOWS.VEHICLE_TYPE,
        TRIPOTRON_WORKFLOWS.VEHICLE_REGISTRATION,
        TRIPOTRON_WORKFLOWS.START_MILEAGE,
        TRIPOTRON_WORKFLOWS.END_MILEAGE,
      )
      .where(TRIPOTRON_WORKFLOWS.ID.eq(id))
      .awaitSingle()
      ?.into(TRIPOTRON_WORKFLOWS)
      ?.toTrip()
  }

  suspend fun listTrips(): List<TripotronTrip> {
    return dslContext
      .select(
        TRIPOTRON_WORKFLOWS.ID,
        TRIPOTRON_WORKFLOWS.USER_ID,
        TRIPOTRON_WORKFLOWS.USER_FULL_NAME,
        TRIPOTRON_WORKFLOWS.TRAVEL_ORDER_ID,
        TRIPOTRON_WORKFLOWS.START_LOCATION,
        TRIPOTRON_WORKFLOWS.END_LOCATION,
        TRIPOTRON_WORKFLOWS.START_DATE_TIME,
        TRIPOTRON_WORKFLOWS.END_DATE_TIME,
        TRIPOTRON_WORKFLOWS.TRANSPORT_TYPE,
        TRIPOTRON_WORKFLOWS.DESCRIPTION,
        TRIPOTRON_WORKFLOWS.CREATED_AT,
        TRIPOTRON_WORKFLOWS.UPDATED_AT,
        TRIPOTRON_WORKFLOWS.VEHICLE_TYPE,
        TRIPOTRON_WORKFLOWS.VEHICLE_REGISTRATION,
        TRIPOTRON_WORKFLOWS.START_MILEAGE,
        TRIPOTRON_WORKFLOWS.END_MILEAGE,
      )
      .from(TRIPOTRON_WORKFLOWS)
      .asFlow()
      .map { it.into(TRIPOTRON_WORKFLOWS).toTrip() }
      .toList()
  }

  suspend fun insertTripExpense(tripId: KUUID, expense: TravelExpense) {
    dslContext
      .insertInto(TRIPOTRON_TRAVEL_EXPENSES)
      .set(TRIPOTRON_TRAVEL_EXPENSES.ID, expense.id)
      .set(TRIPOTRON_TRAVEL_EXPENSES.WORKFLOW_ID, tripId)
      .set(TRIPOTRON_TRAVEL_EXPENSES.AMOUNT, expense.amount)
      .set(TRIPOTRON_TRAVEL_EXPENSES.CURRENCY, expense.currency)
      .set(TRIPOTRON_TRAVEL_EXPENSES.IMAGE_PATH, expense.imagePath)
      .set(TRIPOTRON_TRAVEL_EXPENSES.IMAGE_PROVIDER, expense.imageProvider)
      .set(TRIPOTRON_TRAVEL_EXPENSES.DESCRIPTION, expense.description)
      .awaitSingle()
  }

  suspend fun listTripExpenses(tripId: KUUID): List<TripotronTravelExpense> {
    return dslContext
      .select(
        TRIPOTRON_TRAVEL_EXPENSES.ID,
        TRIPOTRON_TRAVEL_EXPENSES.WORKFLOW_ID,
        TRIPOTRON_TRAVEL_EXPENSES.AMOUNT,
        TRIPOTRON_TRAVEL_EXPENSES.CURRENCY,
        TRIPOTRON_TRAVEL_EXPENSES.IMAGE_PATH,
        TRIPOTRON_TRAVEL_EXPENSES.IMAGE_PROVIDER,
        TRIPOTRON_TRAVEL_EXPENSES.DESCRIPTION,
        TRIPOTRON_TRAVEL_EXPENSES.VERIFIED,
        TRIPOTRON_TRAVEL_EXPENSES.CREATED_AT,
        TRIPOTRON_TRAVEL_EXPENSES.UPDATED_AT,
      )
      .from(TRIPOTRON_TRAVEL_EXPENSES)
      .where(TRIPOTRON_TRAVEL_EXPENSES.WORKFLOW_ID.eq(tripId))
      .asFlow()
      .map { it.into(TRIPOTRON_TRAVEL_EXPENSES).toTravelExpense() }
      .toList()
  }
}
