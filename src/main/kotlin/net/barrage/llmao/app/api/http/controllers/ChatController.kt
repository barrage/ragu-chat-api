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
import net.barrage.llmao.app.api.http.dto.EvaluateMessageDTO
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.ChatService
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
      put(updateTitle()) {
        val user = call.user()
        val chatId = call.pathUuid("chatId")
        val input: UpdateChatTitleDTO = call.receive()
        service.updateTitle(chatId, user.id, input.title)
        call.respond(HttpStatusCode.OK)
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
          val messages: List<Message> = service.getMessages(chatId, user.id)
          call.respond(HttpStatusCode.OK, messages)
        }

        patch("/{messageId}", evaluate()) {
          val user = call.user()
          val input: EvaluateMessageDTO = call.receive()
          val chatId = call.pathUuid("chatId")
          val messageId = call.pathUuid("messageId")
          val message = service.evaluateMessage(chatId, messageId, user.id, input.evaluation)
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
  request { queryPagination() }
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
        body<List<AppError>> {}
      }
  }
}

private fun getMessages(): OpenApiRoute.() -> Unit = {
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
        body<List<AppError>> {}
      }
  }
}

private fun updateTitle(): OpenApiRoute.() -> Unit = {
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
        body<List<AppError>> {}
      }
  }
}

private fun evaluate(): OpenApiRoute.() -> Unit = {
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
        body<List<AppError>> {}
      }
  }
}

private fun deleteChat(): OpenApiRoute.() -> Unit = {
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
        body<List<AppError>> {}
      }
  }
}
