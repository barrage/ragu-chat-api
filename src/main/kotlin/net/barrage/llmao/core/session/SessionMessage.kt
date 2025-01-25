package net.barrage.llmao.core.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.barrage.llmao.core.session.chat.ChatSessionMessage
import net.barrage.llmao.core.types.KUUID

/** Incoming messages. */
@Serializable
sealed class IncomingMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: IncomingSystemMessage) : IncomingMessage()

  @Serializable @SerialName("chat") data class Chat(val text: String) : IncomingMessage()
}

/** System messages used to control sessions. */
@Serializable
sealed class IncomingSystemMessage {
  @Serializable
  @SerialName("chat_open_new")
  data class CreateNewSession(val agentId: KUUID) : IncomingSystemMessage()

  @Serializable
  @SerialName("chat_open_existing")
  data class LoadExistingSession(val chatId: KUUID, val initialHistorySize: Int = 10) :
    IncomingSystemMessage()

  @Serializable @SerialName("chat_close") data object CloseSession : IncomingSystemMessage()

  @Serializable @SerialName("chat_stop_stream") data object StopStream : IncomingSystemMessage()
}

/** Outgoing session messages. */
@Serializable
sealed class OutgoingSystemMessage {
  /** Sent when a chat is opened. */
  @SerialName("chat_open")
  @Serializable
  data class SessionOpen(val chatId: KUUID) : OutgoingSystemMessage()

  /** Sent when a chat is closed manually. */
  @SerialName("chat_closed")
  @Serializable
  data class SessionClosed(val chatId: KUUID) : OutgoingSystemMessage()

  /**
   * Sent when an administrator deactivates an agent via the service and is used to indicate the
   * chat is no longer available.
   */
  @SerialName("agent_deactivated")
  @Serializable
  data class AgentDeactivated(val agentId: KUUID) : OutgoingSystemMessage()
}

val IncomingMessageSerializer = Json {
  // Register all the polymorphic subclasses
  classDiscriminator = "type" // Use "type" field for the discriminator
  serializersModule = SerializersModule {
    polymorphic(IncomingMessage::class) {
      subclass(IncomingMessage.System::class, IncomingMessage.System.serializer())
      subclass(IncomingMessage.Chat::class, IncomingMessage.Chat.serializer())
    }
    polymorphic(IncomingSystemMessage::class) {
      subclass(
        IncomingSystemMessage.CreateNewSession::class,
        IncomingSystemMessage.CreateNewSession.serializer(),
      )
      subclass(
        IncomingSystemMessage.LoadExistingSession::class,
        IncomingSystemMessage.LoadExistingSession.serializer(),
      )
      subclass(
        IncomingSystemMessage.CloseSession::class,
        IncomingSystemMessage.CloseSession.serializer(),
      )
      subclass(
        IncomingSystemMessage.StopStream::class,
        IncomingSystemMessage.StopStream.serializer(),
      )
    }
    polymorphic(OutgoingSystemMessage::class) {
      subclass(
        OutgoingSystemMessage.SessionOpen::class,
        OutgoingSystemMessage.SessionOpen.serializer(),
      )
      subclass(
        OutgoingSystemMessage.SessionClosed::class,
        OutgoingSystemMessage.SessionClosed.serializer(),
      )
      subclass(
        OutgoingSystemMessage.AgentDeactivated::class,
        OutgoingSystemMessage.AgentDeactivated.serializer(),
      )
    }
    polymorphic(ChatSessionMessage::class) {
      subclass(ChatSessionMessage.StreamChunk::class, ChatSessionMessage.StreamChunk.serializer())
      subclass(
        ChatSessionMessage.StreamComplete::class,
        ChatSessionMessage.StreamComplete.serializer(),
      )
      subclass(
        ChatSessionMessage.ChatTitleUpdated::class,
        ChatSessionMessage.ChatTitleUpdated.serializer(),
      )
    }
  }
}
