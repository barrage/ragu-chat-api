package net.barrage.llmao.app.workflow.jirakira

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class JiraKiraMessage {
  @Serializable
  @SerialName("jirakira.response")
  data class LlmResponse(val content: String) : JiraKiraMessage()

  @Serializable
  @SerialName("jirakira.worklog")
  data class WorklogCreated(val worklog: TempoWorklog) : JiraKiraMessage()
}
