package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.patch
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.ChatService

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

    get<ChatController> {
        val chats: List<ChatDTO> = chatService.getAll()
        call.respond(HttpStatusCode.OK, chats)
        return@get
    }

    get<ChatController.Chat.Messages> {
        val messages: List<Message> = chatService.getMessages(it.parent.id)
        call.respond(HttpStatusCode.OK, messages)
        return@get
    }

    put<ChatController.Chat.Title> {
        val input: UpdateChatTitleDTO = call.receive()
        val chat: Chat = chatService.updateTitle(it.parent.id, input)
        call.respond(HttpStatusCode.OK, chat)
        return@put
    }

    patch<ChatController.Chat.Messages.Message> {
        val input: EvaluateMessageDTO = call.receive()
        val chatId = it.parent.parent.id
        val messageId = it.messageId
        val message = chatService.evaluateMessage(chatId, messageId, input)
        call.respond(HttpStatusCode.OK, message)
        return@patch
    }
}