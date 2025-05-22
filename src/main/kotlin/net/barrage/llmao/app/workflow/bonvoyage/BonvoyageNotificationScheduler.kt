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

class BonvoyageNotificationScheduler(
  private val email: Email,
  private val repository: BonvoyageRepository,
  /** Amount of ms between checks. */
  private val checkInterval: Long = 60_000,
) {
  private var job: Job? = null
  private val jobScope = CoroutineScope(Dispatchers.Default)
  private val emailScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.BonvoyageNotificationScheduler")

  fun start() {
    if (job != null) {
      throw IllegalStateException("Scheduler already started")
    }

    log.info("Starting Bonvoyage notification scheduler")

    job = jobScope.launch { loop() }
  }

  private suspend fun loop() {
    while (true) try {
      val pendingNotifications = repository.listPendingStartNotifications().toMutableList()
      pendingNotifications.addAll(repository.listPendingEndNotifications())

      val now = KOffsetDateTime.now()

      var sent = 0

      for (notification in pendingNotifications) {
        if (notification.scheduledTime > now) {
          continue
        }

        log.debug("Sending {} email to {} ", notification.notificationType, notification.userEmail)

        val message =
          when (notification.notificationType) {
            BonvoyageTripNotificationType.START_OF_TRIP ->
              "Your trip ${notification.startLocation}-${notification.endLocation} is starting."
            BonvoyageTripNotificationType.END_OF_TRIP ->
              "Your trip ${notification.startLocation}-${notification.endLocation} is ending."
          }

        emailScope.launch {
          email.sendEmail(
            BonvoyageConfig.emailSender,
            notification.userEmail,
            "Trip reminder ${notification.startLocation}-${notification.endLocation}",
            message,
          )
          repository.markTripNotificationAsSent(notification.tripId, notification.notificationType)
        }

        sent += 1
      }
      if (sent > 0) {
        log.debug("Scheduler cycle complete; {} notifications sent", sent)
      }
    } catch (e: Exception) {
      log.error("Error while sending reminders", e)
    } finally {
      delay(checkInterval)
    }
  }

  fun stop() {
    job?.cancel()
  }
}
