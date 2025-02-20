package net.barrage.llmao.app.workflow.jirakira

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Workflow

class JiraKiraWorkflow(val id: KUUID, val userId: KUUID, private val jirakira: JiraKira) :
  Workflow {
  private val scope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    // TODO: Figure out
    return id
  }

  override fun execute(message: String) {
    scope.launch { jirakira.completion(message) }
  }

  override fun cancelStream() {}
}
