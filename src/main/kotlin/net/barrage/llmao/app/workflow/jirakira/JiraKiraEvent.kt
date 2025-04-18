package net.barrage.llmao.app.workflow.jirakira

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class JiraKiraEvent {
  @Serializable
  @SerialName("jirakira.worklog_created")
  data class WorklogCreated(val worklog: TempoWorklogEntry) : JiraKiraEvent()

  @Serializable
  @SerialName("jirakira.worklog_updated")
  data class WorklogUpdated(val worklog: TempoWorklogEntry) : JiraKiraEvent()
}
