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
import io.ktor.server.sessions.*
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.CountedList
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.PaginationSort
import net.barrage.llmao.models.RequestUser
import net.barrage.llmao.serializers.KUUID

@Resource("chats")
class ChatController(val pagination: PaginationSort) {
  @Resource("{id}")
  class Chat(val parent: ChatController, val id: KUUID) {
    @Resource("messages")
    class Messages(val parent: Chat) {
      @Resource("{messageId}") class Message(val parent: Messages, val messageId: KUUID)
    }

    @Resource("title") class Title(val parent: Chat)
  }
}

fun Route.chatsRoutes(service: ChatService) {

  authenticate("auth-session") {
    get<ChatController>(getAllChats()) {
      val user = call.attributes[RequestUser]
      val chats = service.listChats(it.pagination, user.id)
      call.respond(HttpStatusCode.OK, chats)
    }

    get<ChatController.Chat.Messages>(getMessages()) {
      val user = call.attributes[RequestUser]
      val messages: List<Message> = service.getMessages(it.parent.id, user.id)
      call.respond(HttpStatusCode.OK, messages)
    }

    put<ChatController.Chat.Title>(updateTitle()) {
      val user = call.attributes[RequestUser]
      val input: UpdateChatTitleDTO = call.receive()
      service.updateTitle(it.parent.id, user.id, input.title)
      call.respond(HttpStatusCode.OK)
    }

    patch<ChatController.Chat.Messages.Message>(evaluate()) {
      val user = call.attributes[RequestUser]
      val input: EvaluateMessageDTO = call.receive()
      val chatId = it.parent.parent.id
      val messageId = it.messageId
      val message = service.evaluateMessage(chatId, messageId, user.id, input.evaluation)
      call.respond(HttpStatusCode.OK, message)
    }

    delete<ChatController.Chat>(deleteChat()) {
      val user = call.attributes[RequestUser]
      service.deleteChat(it.id, user.id)
      call.respond(HttpStatusCode.NoContent)
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
        body<CountedList<Chat>> {
          description = "A list of Chat objects representing all the chats"
        }
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
        body<List<Message>> {
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
        body<Message> {}
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
