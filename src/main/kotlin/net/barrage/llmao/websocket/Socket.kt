package net.barrage.llmao.websocket

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.apiError
import net.barrage.llmao.llm.factories.chatFactory
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.SessionService
import net.barrage.llmao.weaviate.WeaviteLoader

fun Application.configureWebsockets() {
  install(WebSockets) {
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter =
      KotlinxWebsocketSerializationConverter(
        Json {
          ignoreUnknownKeys = true
          isLenient = true
          encodeDefaults = true
        }
      )
  }

  val chatFactory = chatFactory(environment.config, WeaviteLoader.weaver)
  val messageHandler = MessageHandler(chatFactory)
  val sessionService = SessionService()

  routing {
    webSocket {
      val sessionId: KUUID
      try {
        sessionId = KUUID.fromString(call.parameters["sessionId"].toString())
      } catch (e: IllegalArgumentException) {
        send(apiError("Invalid session id", "Session id is not a valid UUID").toString())
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session id"))
        return@webSocket
      }

      val messageResponseFlow = MutableSharedFlow<String>()
      val sharedFlow = messageResponseFlow.asSharedFlow()

      val job = launch { sharedFlow.collect { send(it) } }

      val userId: KUUID
      val session = sessionService.get(sessionId)
      if (session == null) {
        send(apiError("Invalid session id", "Session not found").toString())
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session id"))
        return@webSocket
      } else if (!session.isValid()) {
        send(apiError("Invalid session id", "Session expired").toString())
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Session expired"))
        return@webSocket
      } else {
        sessionService.extend(sessionId)
        userId = session.userId
      }

      for (frame in incoming) {
        when (frame) {
          is Frame.Text -> {
            val message: C2SMessage

            try {
              message = Json.decodeFromString(frame.readText())
            } catch (e: Throwable) {
              e.printStackTrace()
              send(apiError("Unprocessable Entity", "Message format malformed").toString())
              continue
            }

            if (userId != message.userId) {
              send(apiError("Invalid userId", "Invalid userId").toString())
              continue
            }

            sessionService.extend(sessionId)
            messageHandler.handleMessage(Emitter(messageResponseFlow), message)
          }

          is Frame.Close -> {
            job.cancel()
            messageHandler.removeUserChat(userId)
            close(CloseReason(CloseReason.Codes.NORMAL, "Session closed"))
            return@webSocket
          }

          else -> {
            send(apiError("Invalid message format", "Only text messages are allowed").toString())
          }
        }
      }
    }
  }
}
