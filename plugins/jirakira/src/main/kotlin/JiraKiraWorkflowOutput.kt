import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.workflow.WorkflowOutput

@Serializable
@OptIn(ExperimentalSerializationApi::class)
sealed class JiraKiraWorkflowOutput : WorkflowOutput() {
  @Serializable
  @SerialName("jirakira.worklog_created")
  data class WorklogCreated(val worklog: TempoWorklogEntry) : JiraKiraWorkflowOutput()

  @Serializable
  @SerialName("jirakira.worklog_updated")
  data class WorklogUpdated(val worklog: TempoWorklogEntry) : JiraKiraWorkflowOutput()
}
