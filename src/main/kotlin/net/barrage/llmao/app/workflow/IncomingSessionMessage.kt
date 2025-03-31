package net.barrage.llmao.app.workflow

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.workflow.IncomingSystemMessage

/**
 * Enumeration of all incoming messages in the application layer. Having a sealed class like this
 * allows us to deserialize messages and setup their handlers more easily.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class IncomingSessionMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: IncomingSystemMessage) : IncomingSessionMessage()

  @Serializable
  @SerialName("chat")
  data class Chat(val text: String, val attachments: List<IncomingMessageAttachment>? = null) :
    IncomingSessionMessage()
}
