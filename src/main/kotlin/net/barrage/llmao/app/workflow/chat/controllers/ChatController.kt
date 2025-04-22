package net.barrage.llmao.app.workflow.chat.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.api.pub.PublicChatService
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.ChatWithAgent
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.storage.ATTACHMENTS_PATH
import net.barrage.llmao.core.storage.BlobStorage
import net.barrage.llmao.types.KUUID

fun Route.chatsRoutes(service: PublicChatService, imageStorage: BlobStorage<Image>) {
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
        val chat = service.userUpdateTitle(chatId, user.id, input.title)
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
          val messages = service.getMessages(chatId, user.id, pagination)
          call.respond(HttpStatusCode.OK, messages)
        }

        patch("/{messageGroupId}", evaluate()) {
          val user = call.user()
          val input: EvaluateMessage = call.receive()
          val chatId = call.pathUuid("chatId")
          val messageGroupId = call.pathUuid("messageGroupId")
          service.evaluateMessage(chatId, messageGroupId, user.id, input)
          call.respond(HttpStatusCode.NoContent)
        }
      }

      get("/attachments/images/{path}") {
        val user = call.user()

        val chatId = call.pathUuid("chatId")

        // Throws if not found
        service.getChat(chatId, user.id)

        val imagePath = call.parameters["path"]!!

        val image = imageStorage.retrieve("${ATTACHMENTS_PATH}/$imagePath")

        if (image == null) {
          call.respond(HttpStatusCode.NotFound)
          return@get
        }

        call.response.header(
          HttpHeaders.ContentDisposition,
          ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, imagePath)
            .toString(),
        )

        call.respondBytes(
          image.data,
          ContentType.parse(image.type.contentType()),
          HttpStatusCode.OK,
        )
      }
    }
  }
}

// OpenAPI documentation
private fun getAllChats(): RouteConfig.() -> Unit = {
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

private fun getChatWithAgent(): RouteConfig.() -> Unit = {
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

private fun getMessages(): RouteConfig.() -> Unit = {
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
        body<CountedList<MessageGroupAggregate>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving messages"
        body<List<AppError>> {}
      }
  }
}

private fun updateTitle(): RouteConfig.() -> Unit = {
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

private fun evaluate(): RouteConfig.() -> Unit = {
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

private fun deleteChat(): RouteConfig.() -> Unit = {
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
