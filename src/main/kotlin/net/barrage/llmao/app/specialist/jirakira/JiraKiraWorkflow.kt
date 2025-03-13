package net.barrage.llmao.app.specialist.jirakira

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Workflow

class JiraKiraWorkflow(val id: KUUID, val user: User, private val jirakira: JiraKira) : Workflow {
  private val scope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    // TODO: Figure out
    return id
  }

  override fun execute(message: String) {
    scope.launch {
      try {
        jirakira.execute(message)
      } catch (e: AppError) {
        LOG.error("Error in JiraKira", e)
        jirakira.emitError(
          e.withDisplayMessage("Gojira malfunction. Business execution failure imminent.")
        )
      } catch (e: Exception) {
        LOG.error("Error in JiraKira", e)
        jirakira.emitError(
          AppError.internal()
            .withDisplayMessage("Gojira malfunction. Business execution failure imminent.")
        )
      }
    }
  }

  override fun cancelStream() {}
}
