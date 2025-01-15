package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.EvaluateMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query
import net.barrage.llmao.plugins.user

fun Route.chatsRoutes(service: ChatService) {
  route("/chats") {
    get(getAllChats()) {
      val user = call.user()
      val pagination = call.query(PaginationSort::class)
      val chats = service.listChats(pagination, user.id)
      call.respond(HttpStatusCode.OK, chats)
    }

    route("/{chatId}") {
      get(getChatWithAgent()) {
        val user = call.user()
        val chatId = call.pathUuid("chatId")
        val chat = service.getChatWithAgent(chatId, user.id)
        call.respond(HttpStatusCode.OK, chat)
      }

      put(updateTitle()) {
        val user = call.user()
        val chatId = call.pathUuid("chatId")
        val input: UpdateChatTitleDTO = call.receive()
        val chat = service.updateTitle(chatId, user.id, input.title)
        call.respond(HttpStatusCode.OK, chat)
      }

      delete(deleteChat()) {
        val user = call.user()
        val chatId = call.pathUuid("chatId")
        service.deleteChat(chatId, user.id)
        call.respond(HttpStatusCode.NoContent)
      }

      route("/messages") {
        get(getMessages()) {
          val user = call.user()
          val chatId = call.pathUuid("chatId")
          val pagination = call.query(Pagination::class)
          val messages: List<Message> = service.getMessages(chatId, user.id, pagination)
          call.respond(HttpStatusCode.OK, messages)
        }

        patch("/{messageId}", evaluate()) {
          val user = call.user()
          val input: EvaluateMessage = call.receive()
          val chatId = call.pathUuid("chatId")
          val messageId = call.pathUuid("messageId")
          val message = service.evaluateMessage(chatId, messageId, user.id, input)
          call.respond(HttpStatusCode.OK, message)
        }
      }
    }
  }
}

// OpenAPI documentation
private fun getAllChats(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Retrieve list of all chats"
  request { queryPaginationSort() }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Chat objects representing all the chats"
        body<List<Chat>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chats"
        body<List<AppError>> {}
      }
  }
}

private fun getChatWithAgent(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Retrieve chat by ID"
  request {
    pathParameter<KUUID>("chatId") {
      description = "Chat ID"
      required = true
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Chat retrieved successfully"
        body<ChatWithAgent>()
      }
    HttpStatusCode.NotFound to
      {
        description = "Chat not found"
        body<List<AppError>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}

private fun getMessages(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Retrieve chat messages"
  request {
    pathParameter<KUUID>("chatId") {
      description = "The ID of the chat to retrieve messages from"
      required = true
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    queryPagination()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "A counted list of Message objects representing all the messages from a chat"
        body<List<Message>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving messages"
        body<List<AppError>> {}
      }
  }
}

private fun updateTitle(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Update chat title"
  request {
    pathParameter<KUUID>("chatId") {
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
        body<List<AppError>> {}
      }
  }
}

private fun evaluate(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Evaluate chat message"
  request {
    pathParameter<KUUID>("chatId") {
      description = "The ID of the chat"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("messageId") {
      description = "The ID of the message"
      example("default") { value = "eb771f1a-cd4a-4288-9eb4-bd2b33c58d48" }
    }
    body<EvaluateMessage>()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Updated message retrieved successfully"
        body<EvaluateMessage> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}

private fun deleteChat(): OpenApiRoute.() -> Unit = {
  tags("chats")
  description = "Delete chat"
  request {
    pathParameter<KUUID>("chatId") {
      description = "The ID of the chat"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Chat deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting chat"
        body<List<AppError>> {}
      }
  }
}
