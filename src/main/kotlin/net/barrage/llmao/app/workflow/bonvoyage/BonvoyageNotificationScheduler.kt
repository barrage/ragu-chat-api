package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.barrage.llmao.core.Email
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

class BonvoyageNotificationScheduler(
  private val email: Email,
  private val repository: BonvoyageRepository,
  /** Amount of ms between checks. */
  private val checkInterval: Long = 60_000,
) {
  private val q: ArrayDeque<ScheduledNotification> = ArrayDeque()
  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.BonvoyageNotificationScheduler")

  fun start() {
    if (job != null) {
      throw IllegalStateException("Scheduler already started")
    }

    log.info("Starting Bonvoyage notification scheduler")

    job = scope.launch { loop() }
  }

  private suspend fun loop() {
    while (true) {
      try {
        updateQueue()

        val now = KOffsetDateTime.now()

        while (q.isNotEmpty() && q.first().scheduledTime <= now) {
          val notification = q.removeFirst()
          when (notification) {
            is ScheduledNotification.Email -> {
              log.debug("Sending {} email to {} ", notification.type, notification.email)

              val message =
                when (notification.type) {
                  BonvoyageTripNotificationType.START_OF_TRIP ->
                    "Your trip ${notification.origin}-${notification.destination} is starting."
                  BonvoyageTripNotificationType.END_OF_TRIP ->
                    "Your trip ${notification.origin}-${notification.destination} is ending."
                }

              repository.transaction(::BonvoyageRepository) {
                it.markTripNotificationAsSent(notification.id)
                email.sendEmail(
                  "bonvoyage@barrage.net",
                  notification.email,
                  "Trip reminder ${notification.origin}-${notification.destination}",
                  message,
                )
              }
            }
          }
        }
        log.info("Scheduler cycle complete; queue size: {}", q.size)
      } catch (e: Exception) {
        log.error("Error while sending reminders", e)
      }
      delay(checkInterval)
    }
  }

  private suspend fun updateQueue() {
    log.debug("Updating notification queue; current size: {}", q.size)

    val trips = repository.listTrips(completed = false).associateBy { it.id }
    val pendingNotifications = repository.listPendingTripNotifications(q.map { it.id })

    for (notification in pendingNotifications) {
      val trip = trips[notification.tripId]

      if (trip == null) {
        log.debug(
          "Trip ({}) is either deleted or completed, skipping notification.",
          notification.tripId,
        )
        continue
      }

      when (notification.notificationType) {
        BonvoyageTripNotificationType.START_OF_TRIP -> {
          if (trip.active) {
            log.debug("Trip ({}) has already started, skipping start notification.", trip.id)
            continue
          }
          // TODO: Use push instead of email.
          q.add(
            ScheduledNotification.Email(
              trip.userEmail,
              notification.id,
              trip.startDateTime,
              trip.startLocation,
              trip.endLocation,
              notification.notificationType,
            )
          )
        }

        BonvoyageTripNotificationType.END_OF_TRIP -> {
          // TODO: Use push instead of email.
          q.add(
            ScheduledNotification.Email(
              trip.userEmail,
              notification.id,
              trip.endDateTime,
              trip.startLocation,
              trip.endLocation,
              notification.notificationType,
            )
          )
        }
      }
    }

    q.sortBy { it.scheduledTime }

    log.debug("Notification queue updated, new size: {}", q.size)
  }

  fun stop() {
    job?.cancel()
  }
}

private sealed class ScheduledNotification(
  val id: KUUID,
  val scheduledTime: KOffsetDateTime,
  val origin: String,
  val destination: String,
  val type: BonvoyageTripNotificationType,
) {
  class Email(
    val email: String,
    id: KUUID,
    scheduledTime: KOffsetDateTime,
    origin: String,
    destination: String,
    type: BonvoyageTripNotificationType,
  ) : ScheduledNotification(id, scheduledTime, origin, destination, type)
}
