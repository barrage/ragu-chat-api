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
  /** Amount of ms between checks. */
  private val checkInterval: Long = 60_000,
) {
  private val q: ArrayDeque<ScheduledNotification> = ArrayDeque()
  private var job: Job? = null
  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val log = KtorSimpleLogger("n.b.l.a.w.b.BonvoyageNotificationScheduler")

  fun scheduleEmail(email: String, origin: String, destination: String, time: KOffsetDateTime) {
    log.debug("Scheduling email for {} at {}", email, time)
    q.add(ScheduledNotification.Email(email, time, origin, destination))
  }

  fun start() {
    if (job != null) {
      throw IllegalStateException("Scheduler already started")
    }

    log.info("Starting Bonvoyage notification scheduler")

    job =
      scope.launch {
        while (true) {
          try {
            val now = KOffsetDateTime.now()
            log.debug("Checking for notifications, pending: {}", q.size)
            q.sortBy { it.scheduledTime }
            while (q.isNotEmpty() && q.first().scheduledTime <= now.plusMinutes(1)) {
              // TODO: Check if notification was already sent
              val notification = q.removeFirst()
              when (notification) {
                is ScheduledNotification.Email -> {
                  log.debug(
                    "Sending email to {} for trip to {} at {}",
                    notification.email,
                    notification.destination,
                    notification.scheduledTime,
                  )
                  email.sendEmail(
                    "bonvoyage@barrage.net",
                    notification.email,
                    "Your trip to ${notification.destination} is about to start",
                    """Your travel from ${notification.origin} to ${notification.destination} is scheduled for ${notification.scheduledTime}."""
                      .trimMargin(),
                  )
                }
              }
            }
            log.debug("Finished notification cycle, pending: {}", q.size)
          } catch (e: Exception) {
            log.error("Error while sending reminders", e)
          }
          delay(checkInterval)
        }
      }
  }

  fun stop() {
    job?.cancel()
  }
}

private sealed class ScheduledNotification(
  val scheduledTime: KOffsetDateTime,
  val origin: String,
  val destination: String,
) {
  class Email(
    val email: String,
    scheduledTime: KOffsetDateTime,
    origin: String,
    destination: String,
  ) : ScheduledNotification(scheduledTime, origin, destination)
}
