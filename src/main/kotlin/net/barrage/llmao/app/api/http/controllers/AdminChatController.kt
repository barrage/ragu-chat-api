package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.dto.SearchFiltersAdminChatQuery
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryListChatsFilters
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.api.ChatService
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.ChatWithAgent
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.types.KUUID

fun Route.adminChatsRoutes(service: ChatService) {
  route("/admin/chats") {
    get(adminGetAllChats()) {
      val pagination = call.query(PaginationSort::class)
      val filters = call.query(SearchFiltersAdminChatQuery::class).toSearchFiltersAdminChats()
      val chats = service.listChatsAdmin(pagination, filters)
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
        val chat = service.updateTitle(chatId, input.title)
        call.respond(HttpStatusCode.OK, chat)
      }

      delete(adminDeleteChat()) {
        val chatId = call.pathUuid("chatId")
        service.deleteChat(chatId)
        call.respond(HttpStatusCode.NoContent)
      }

      route("/messages") {
        get(adminGetMessages()) {
          val chatId = call.pathUuid("chatId")
          val pagination = call.query(Pagination::class)
          val messages = service.getMessages(chatId, pagination = pagination)
          call.respond(HttpStatusCode.OK, messages)
        }

        patch("/{messageGroupId}", adminEvaluate()) {
          val input: EvaluateMessage = call.receive()
          val chatId = call.pathUuid("chatId")
          val messageGroupId = call.pathUuid("messageGroupId")
          service.evaluateMessage(chatId = chatId, messageGroupId = messageGroupId, input)
          call.respond(HttpStatusCode.NoContent)
        }
      }
    }
  }
}

// OpenAPI documentation
private fun adminGetAllChats(): RouteConfig.() -> Unit = {
  tags("admin/chats")
  description = "Retrieve list of all chats"
  request {
    queryPaginationSort()
    queryListChatsFilters()
  }
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

private fun adminGetChatWithUserAndAgent(): RouteConfig.() -> Unit = {
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
        body<ChatWithAgent> {}
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

private fun adminGetMessages(): RouteConfig.() -> Unit = {
  tags("admin/chats")
  description = "Retrieve chat messages"
  request {
    pathParameter<KUUID>("chatId") {
      description = "The ID of the chat to retrieve messages from"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    queryPagination()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Message objects representing all the messages from a chat"
        body<CountedList<MessageGroupAggregate>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving messages"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateTitle(): RouteConfig.() -> Unit = {
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

private fun adminEvaluate(): RouteConfig.() -> Unit = {
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
        description = "Internal server error occurred while retrieving message"
        body<List<AppError>> {}
      }
  }
}

private fun adminDeleteChat(): RouteConfig.() -> Unit = {
  tags("admin/chats")
  description = "Delete chat"
  request {
    pathParameter<KUUID>("id") {
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
