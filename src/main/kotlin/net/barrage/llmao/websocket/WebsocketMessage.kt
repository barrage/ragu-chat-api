package net.barrage.llmao.websocket

import com.aallam.openai.api.core.FinishReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.barrage.llmao.serializers.KUUID

@Serializable
sealed class ClientMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: SystemMessage) : ClientMessage()

  @Serializable @SerialName("chat") data class Chat(val text: String) : ClientMessage()
}

@Serializable
sealed class SystemMessage {
  @Serializable
  @SerialName("chat_open_new")
  data class OpenNewChat(val agentId: KUUID) : SystemMessage()

  @Serializable
  @SerialName("chat_open_existing")
  data class OpenExistingChat(val chatId: KUUID) : SystemMessage()

  @Serializable @SerialName("chat_close") data class CloseChat(val chatId: KUUID) : SystemMessage()

  @Serializable @SerialName("chat_stop_stream") data object StopStream : SystemMessage()
}

@Serializable
sealed class ServerMessage {
  @Serializable data class ChatOpen(val chatId: KUUID) : ServerMessage()

  @Serializable data class ChatTitle(val chatId: KUUID, val title: String) : ServerMessage()

  @Serializable data class ChatClosed(val chatId: KUUID) : ServerMessage()
}

@Serializable
class FinishEvent(
  /** The streaming chat ID */
  val chatId: KUUID,

  /** What caused the stream to finish. */
  val reason: FinishReason,

  /**
   * Optional message ID, present only when the finish reason is STOP. Failed message IDs are not
   * sent.
   */
  val messageId: KUUID? = null,

  /** Response content sent only when the chat is not streaming. */
  var content: String? = null,
)

val ClientMessageSerializer = Json {
  // Register all the polymorphic subclasses
  classDiscriminator = "type" // Use "type" field for the discriminator
  serializersModule = SerializersModule {
    polymorphic(ClientMessage::class) {
      subclass(ClientMessage.System::class, ClientMessage.System.serializer())
      subclass(ClientMessage.Chat::class, ClientMessage.Chat.serializer())
    }
    polymorphic(SystemMessage::class) {
      subclass(SystemMessage.OpenNewChat::class, SystemMessage.OpenNewChat.serializer())
      subclass(SystemMessage.OpenExistingChat::class, SystemMessage.OpenExistingChat.serializer())
      subclass(SystemMessage.CloseChat::class, SystemMessage.CloseChat.serializer())
      subclass(SystemMessage.StopStream::class, SystemMessage.StopStream.serializer())
    }
  }
}
