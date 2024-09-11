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
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.UserService
import java.time.Duration
import java.util.*

private val userService = UserService()

fun Application.configureWebsockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
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



    routing {
        webSocket {
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

                println("User ID: $userId")

                val user = userService.get(userId)

                send("Hello, ${user.username}!")

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val message: WSMessage

                            try {
                                message = Json.decodeFromString(frame.readText())
                            } catch (e: Throwable) {
                                send(apiError("Invalid message format", "Message format malformed").toString())
                                continue
                            }

                            if (userId != message.userId) {
                                send(apiError("Invalid userId", "User ids don't match").toString())
                                continue
                            }

                            MessageHandler(message)
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
            }

            if (userId != null) chats.remove(userId)
        }
    }
}
