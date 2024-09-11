package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.dtos.messages.MessageDTO
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.ChatService
import net.barrage.llmao.services.SessionService

@Resource("chats")
class ChatController {
    @Resource("{id}")
    class Chat(val parent: ChatController, val id: KUUID) {
        @Resource("messages")
        class Messages(val parent: Chat) {
            @Resource("{messageId}")
            class Message(val parent: Messages, val messageId: KUUID)
        }

        @Resource("title")
        class Title(val parent: Chat)
    }
}

fun Route.chatsRoutes() {
    val chatService = ChatService()

    authenticate("auth-session") {
        get<ChatController>({
            tags("chats")
            description = "Retrieve list of all chats"
            request { }
            response {
                HttpStatusCode.OK to {
                    description = "List of all chats retrieved successfully"
                    body<List<ChatDTO>> {
                        description = "A list of ChatDTO objects representing all the chats"
                    }
                }
                HttpStatusCode.InternalServerError to {
                    description = "Internal server error occurred while retrieving chats"
                    body<List<Error>> {}
                }
            }
        }) {
            val userSession = call.sessions.get<UserSession>()
            val serverSession = SessionService().get(userSession!!.id)
            val chats: List<ChatDTO> = chatService.getAll(serverSession!!.userId)
            call.respond(HttpStatusCode.OK, chats)
            return@get
        }

        get<ChatController.Chat.Messages>({
            tags("chats")
            description = "Retrieve chat messages"
            request {
                pathParameter<String>("id") {
                    description = "The ID of the chat to retrieve messages from"
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "List of all chat messages retrieved successfully"
                    body<List<MessageDTO>> {
                        description = "A list of MessageDTO objects representing all the chats"
                    }
                }
                HttpStatusCode.InternalServerError to {
                    description = "Internal server error occurred while retrieving chats"
                    body<List<Error>> {}
                }
            }
        }) {
            val userSession = call.sessions.get<UserSession>()
            val serverSession = SessionService().get(userSession!!.id)
            val messages: List<Message> = chatService.getMessages(it.parent.id, serverSession!!.userId)
            call.respond(HttpStatusCode.OK, messages)
            return@get
        }

        put<ChatController.Chat.Title>({
            tags("chats")
            description = "Update chat title"
            request {
                pathParameter<String>("id") {
                    description = "The ID of the chat"
                }
                body<UpdateChatTitleDTO>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Updated chat retrieved successfully"
                    body<Chat> {}
                }
                HttpStatusCode.InternalServerError to {
                    description = "Internal server error occurred while retrieving chats"
                    body<List<Error>> {}
                }
            }
        }) {
            val userSession = call.sessions.get<UserSession>()
            val serverSession = SessionService().get(userSession!!.id)
            val input: UpdateChatTitleDTO = call.receive()
            val chat: Chat = chatService.updateTitle(it.parent.id, input, serverSession!!.userId)
            call.respond(HttpStatusCode.OK, chat)
            return@put
        }

        patch<ChatController.Chat.Messages.Message>({
            tags("chats")
            description = "Evaluate chat message"
            request {
                pathParameter<String>("id") {
                    description = "The ID of the chat"
                }
                pathParameter<String>("messageId") {
                    description = "The ID of the message"
                }
                body<EvaluateMessageDTO>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Updated message retrieved successfully"
                    body<MessageDTO> {}
                }
                HttpStatusCode.InternalServerError to {
                    description = "Internal server error occurred while retrieving chats"
                    body<List<Error>> {}
                }
            }
        }) {
            val userSession = call.sessions.get<UserSession>()
            val serverSession = SessionService().get(userSession!!.id)
            val input: EvaluateMessageDTO = call.receive()
            val chatId = it.parent.parent.id
            val messageId = it.messageId
            val message = chatService.evaluateMessage(chatId, messageId, input, serverSession!!.userId)
            call.respond(HttpStatusCode.OK, message)
            return@patch
        }
    }
}