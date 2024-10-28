package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
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
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query

fun Route.adminChatsRoutes(service: ChatService) {
  route("/admin/chats") {
    get(adminGetAllChats()) {
      val pagination = call.query(PaginationSort::class)
      val chats = service.listChats(pagination)
      call.respond(HttpStatusCode.OK, chats)
    }

    route("/{chatId}") {
      get(adminGetChatWithUserAndAgent()) {
        val chatId = call.pathUuid("chatId")
        val chat = service.getChatWithUserAndAgent(chatId)
        call.respond(HttpStatusCode.OK, chat)
      }

      put(adminUpdateTitle()) {
        val chatId = call.pathUuid("chatId")
        val input: UpdateChatTitleDTO = call.receive()
        service.updateTitle(chatId, input.title)
        call.respond(HttpStatusCode.OK, "Chat title successfully updated")
      }

      route("/messages") {
        get(adminGetMessages()) {
          val chatId = call.pathUuid("chatId")
          val messages: List<Message> = service.getMessages(chatId)
          call.respond(HttpStatusCode.OK, messages)
        }

        patch("/{messageId}", adminEvaluate()) {
          val input: EvaluateMessageDTO = call.receive()
          val chatId = call.pathUuid("chatId")
          val messageId = call.pathUuid("messageId")
          val message = service.evaluateMessage(chatId, messageId, input.evaluation)
          call.respond(HttpStatusCode.OK, message)
        }
      }
    }
  }
}

// OpenAPI documentation
private fun adminGetAllChats(): OpenApiRoute.() -> Unit = {
  tags("admin/chats")
  description = "Retrieve list of all chats"
  request { queryPagination() }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Chat objects representing all the chats"
        body<CountedList<Chat>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chats"
        body<List<AppError>> {}
      }
  }
}

// OpenAPI documentation
private fun adminGetChatWithUserAndAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/chats")
  description = "Get single chat"
  request {
    pathParameter<KUUID>("chatId") {
      description = "Chat ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Single chat"
        body<ChatWithUserAndAgent> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Chat not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chats"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetMessages(): OpenApiRoute.() -> Unit = {
  tags("admin/chats")
  description = "Retrieve chat messages"
  request {
    pathParameter<KUUID>("chatId") {
      description = "The ID of the chat to retrieve messages from"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Message objects representing all the messages from a chat"
        body<List<Message>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving messages"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateTitle(): OpenApiRoute.() -> Unit = {
  tags("admin/chats")
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

private fun adminEvaluate(): OpenApiRoute.() -> Unit = {
  tags("admin/chats")
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
        description = "Internal server error occurred while retrieving message"
        body<List<AppError>> {}
      }
  }
}
