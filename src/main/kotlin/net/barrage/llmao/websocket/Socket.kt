package net.barrage.llmao.websocket

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError
import net.barrage.llmao.llm.factories.chatFactory
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.UserService
import java.util.*

fun Application.configureWebsockets() {
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
        )

    }

    val chatFactory = chatFactory(environment.config)
    val messageHandler = MessageHandler(chatFactory)

    routing {
        webSocket {
            val userService = UserService()
            var userId: KUUID? = null
            try {
                val queryUser: String? = call.parameters["userId"]

                if (queryUser.isNullOrBlank()) {
                    throw IllegalArgumentException("Invalid user ID")
                }

                try {
                    userId = UUID.fromString(queryUser)
                } catch (e: Throwable) {
                    throw IllegalArgumentException("Invalid user ID")
                }

                userService.get(userId)

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
                                send(apiError("Invalid userId", "User ids don't match").toString())
                                continue
                            }

                            messageHandler.handleMessage(Emitter(this), message)
                        }

                        else -> {
                            send(apiError("Invalid message format", "Only text messages are allowed").toString())
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                send(apiError("Invalid message format", "Only text messages are allowed").toString())
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid message format"))
            } catch (e: IllegalArgumentException) {
                send(apiError("Invalid userId", "Invalid userId").toString())
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid userId"))
            } catch (e: NotFoundException) {
                send(apiError("User not found", e.message ?: "User not found").toString())
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "User not found"))
            } catch (e: Throwable) {
                e.printStackTrace()
                send(internalError().toString())
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Internal server error"))
            } finally {
                userId?.let { messageHandler.removeUserChat(it) }
            }
        }
    }
}
