package net.barrage.llmao.app.api.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.barrage.llmao.core.models.FinishReason
import net.barrage.llmao.core.types.KUUID

/** Incoming WS messages. */
@Serializable
sealed class ClientMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: SystemMessage) : ClientMessage()

  @Serializable @SerialName("chat") data class Chat(val text: String) : ClientMessage()
}

/** WS command messages. */
@Serializable
sealed class SystemMessage {
  @Serializable
  @SerialName("chat_open_new")
  data class OpenNewChat(val agentId: KUUID) : SystemMessage()

  @Serializable
  @SerialName("chat_open_existing")
  data class OpenExistingChat(val chatId: KUUID, val initialHistorySize: Int = 10) :
    SystemMessage()

  @Serializable @SerialName("chat_close") data object CloseChat : SystemMessage()

  @Serializable @SerialName("chat_stop_stream") data object StopStream : SystemMessage()
}

/** Outgoing WS messages. */
@Serializable
sealed class ServerMessage {
  @SerialName("chat_open") @Serializable data class ChatOpen(val chatId: KUUID) : ServerMessage()

  @SerialName("chat_title")
  @Serializable
  data class ChatTitle(val chatId: KUUID, val title: String) : ServerMessage()

  @SerialName("chat_closed")
  @Serializable
  data class ChatClosed(val chatId: KUUID) : ServerMessage()

  /** Event sent when a chats gets a response fro m an LLM. */
  @SerialName("finish_event")
  @Serializable
  data class FinishEvent(
    /** The streaming chat ID */
    val chatId: KUUID,

    /** What caused the stream to finish. */
    val reason: FinishReason,

    /**
     * Optional message ID, present only when the finish reason is STOP. Failed message IDs are not
     * sent.
     */
    val messageId: KUUID? = null,
  ) : ServerMessage()
}

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
