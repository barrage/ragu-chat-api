package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.delete
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
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.dtos.chats.ChatResponse
import net.barrage.llmao.dtos.chats.PaginatedChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.chats.toPaginatedChatDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.dtos.messages.MessageDTO
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.UserContext
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.ChatService

@Resource("chats")
class ChatController(
  val page: Int? = 1,
  val size: Int? = 10,
  val sortBy: String? = "createdAt",
  val sortOrder: String? = "asc",
) {
  @Resource("{id}")
  class Chat(val parent: ChatController, val id: KUUID) {
    @Resource("messages")
    class Messages(val parent: Chat) {
      @Resource("{messageId}") class Message(val parent: Messages, val messageId: KUUID)
    }

    @Resource("title") class Title(val parent: Chat)
  }
}

fun Route.chatsRoutes() {
  val chatService = ChatService()

  authenticate("auth-session") {
    get<ChatController>(getAllChats()) {
      val page = it.page ?: 1
      val size = it.size ?: 10
      val sortBy = it.sortBy ?: "createdAt"
      val sortOrder = it.sortOrder ?: "asc"

      val user = UserContext.currentUser
      val chatResponse: ChatResponse = chatService.getAll(page, size, sortBy, sortOrder, user?.id)
      val response =
        toPaginatedChatDTO(
          chatResponse.chats,
          PaginationInfo(chatResponse.count, page, size, sortBy, sortOrder),
        )
      call.respond(HttpStatusCode.OK, response)
      return@get
    }

    get<ChatController.Chat.Messages>(getMessages()) {
      val user = UserContext.currentUser
      val messages: List<Message> = chatService.getMessages(it.parent.id, user?.id)
      call.respond(HttpStatusCode.OK, messages)
      return@get
    }

    put<ChatController.Chat.Title>(updateTitle()) {
      val user = UserContext.currentUser
      val input: UpdateChatTitleDTO = call.receive()
      val chat: Chat = chatService.updateTitle(it.parent.id, input, user?.id)
      call.respond(HttpStatusCode.OK, chat)
      return@put
    }

    patch<ChatController.Chat.Messages.Message>(evaluate()) {
      val user = UserContext.currentUser
      val input: EvaluateMessageDTO = call.receive()
      val chatId = it.parent.parent.id
      val messageId = it.messageId
      val message = chatService.evaluateMessage(chatId, messageId, input, user?.id)
      call.respond(HttpStatusCode.OK, message)
      return@patch
    }

    delete<ChatController.Chat>(deleteChat()) {
      val user = UserContext.currentUser
      chatService.deleteChatUser(it.id, user!!.id)
      call.respond(HttpStatusCode.NoContent)
      return@delete
    }
  }
}

// OpenAPI documentation
fun getAllChats(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Retrieve list of all chats"
  request {
    queryParameter<Int>("page") {
      description = "Page number for pagination"
      required = false
      example("default") { value = 1 }
    }
    queryParameter<Int>("size") {
      description = "Number of items per page"
      required = false
      example("default") { value = 10 }
    }
    queryParameter<String>("sortBy") {
      description = "Field to sort by"
      required = false
      example("default") { value = "createdAt" }
    }
    queryParameter<String>("sortOrder") {
      description = "Sort order (asc or desc)"
      required = false
      example("default") { value = "asc" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<PaginatedChatDTO> { description = "A list of Chat objects representing all the chats" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chats"
        body<List<Error>> {}
      }
  }
}

fun getMessages(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Retrieve chat messages"
  request {
    pathParameter<String>("id") {
      description = "The ID of the chat to retrieve messages from"
      required = true
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<List<MessageDTO>> {
          description = "A list of Message objects representing all the messages from a chat"
        }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving messages"
        body<List<Error>> {}
      }
  }
}

fun updateTitle(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Update chat title"
  request {
    pathParameter<String>("id") {
      description = "The ID of the chat"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateChatTitleDTO>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated chat retrieved successfully"
        body<Chat> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<Error>> {}
      }
  }
}

fun evaluate(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Evaluate chat message"
  request {
    pathParameter<String>("id") {
      description = "The ID of the chat"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<String>("messageId") {
      description = "The ID of the message"
      example("default") { value = "eb771f1a-cd4a-4288-9eb4-bd2b33c58d48" }
    }
    body<EvaluateMessageDTO>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated message retrieved successfully"
        body<MessageDTO> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<Error>> {}
      }
  }
}

fun deleteChat(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Delete chat"
  request {
    pathParameter<String>("id") {
      description = "The ID of the chat"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Chat deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting chat"
        body<List<Error>> {}
      }
  }
}
