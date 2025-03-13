package net.barrage.llmao.app.adapters.whatsapp.api

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.adapters.whatsapp.WhatsAppAdapter
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppAgentUpdate
import net.barrage.llmao.app.adapters.whatsapp.models.UpdateNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError

fun Route.adminWhatsAppRoutes(whatsAppAdapter: WhatsAppAdapter) {
  route("/admin/whatsapp/agent") {
    get(adminGetWhatsAppAgent()) {
      val agent = whatsAppAdapter.getAgent()
      call.respond(HttpStatusCode.OK, agent)
    }

    put(adminSetWhatsAppAgent()) {
      val update = call.receive<WhatsAppAgentUpdate>()
      val agent = whatsAppAdapter.setAgent(update.agentId)
      call.respond(agent)
    }

    delete(adminUnsetWhatsAppAgent()) {
      whatsAppAdapter.unsetAgent()
      call.respond(HttpStatusCode.NoContent)
    }
  }

  route("/admin/whatsapp/numbers") {
    get("/{userId}", adminGetWhatsAppNumbersForUser()) {
      val userId = call.parameters["userId"]!!
      val userNumbers = whatsAppAdapter.getNumbers(userId)
      call.respond(userNumbers)
    }

    post("/{userId}", adminAddWhatsAppNumberForUser()) {
      val userId = call.parameters["userId"]!!
      val number = call.receive<UpdateNumber>()
      val user = whatsAppAdapter.addNumber(userId, null, number)
      call.respond(user)
    }

    put("/{userId}/{numberId}", adminUpdateWhatsAppNumberForUser()) {
      val userId = call.parameters["userId"]!!
      val numberId = call.pathUuid("numberId")
      val updatedNumber = call.receive<UpdateNumber>()
      val user = whatsAppAdapter.updateNumber(userId, numberId, updatedNumber)
      call.respond(user)
    }

    delete("/{userId}/{numberId}", adminDeleteWhatsAppNumberForUser()) {
      val userId = call.parameters["userId"]!!
      val numberId = call.pathUuid("numberId")
      whatsAppAdapter.deleteNumber(userId, numberId)
      call.respond(HttpStatusCode.NoContent)
    }
  }

  route("/admin/whatsapp/chats") {
    get(adminGetAllWhatsAppChats()) {
      val pagination = call.query(PaginationSort::class)
      val chats = whatsAppAdapter.getAllChats(pagination)
      call.respond(chats)
    }

    get("/{chatId}", adminGetWhatsAppChat()) {
      val chatId = call.pathUuid("chatId")
      val chat = whatsAppAdapter.getChatById(chatId)
      call.respond(chat)
    }
  }
}

// OpenAPI documentation
private fun adminGetWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agent")
  description = "Get the currently assigned WhatsApp agent"
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp agent retrieved successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminSetWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agent")
  description = "Set the currently assigned WhatsApp agent"
  request { body<WhatsAppAgentUpdate> { description = "New WhatsApp agent" } }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp agent set successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while setting agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminUnsetWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agent")
  description = "Unset the currently assigned WhatsApp agent"
  response {
    HttpStatusCode.NoContent to { description = "WhatsApp agent unset successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while unsetting agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetWhatsAppNumbersForUser(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/numbers")
  description = "Retrieve WhatsApp numbers for user"
  request {
    pathParameter<KUUID>("userId") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsAppNumbers retrieved successfully"
        body<List<WhatsAppNumber>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving numbers"
        body<List<AppError>> {}
      }
  }
}

private fun adminAddWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/numbers")
  description = "Add WhatsApp number for user"
  request {
    pathParameter<KUUID>("userId") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateNumber> { description = "New WhatsApp number" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsAppNumber created successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating number"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/numbers")
  description = "Update WhatsApp number for user"
  request {
    pathParameter<KUUID>("userId") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("numberId") {
      description = "Number ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateNumber> { description = "Updated WhatsApp number" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsAppNumber updated successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating number"
        body<List<AppError>> {}
      }
  }
}

private fun adminDeleteWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/numbers")
  description = "Delete WhatsApp number for user"
  request {
    pathParameter<KUUID>("userId") {
      description = "User ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("numberId") {
      description = "Number ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "WhatsAppNumber deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting number"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetAllWhatsAppChats(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/chats")
  description = "Retrieve list of all WhatsApp chats"
  request { queryPaginationSort() }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of WhatsApp chats"
        body<CountedList<Chat>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chats"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetWhatsAppChat(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/chats")
  description = "Retrieve WhatsApp chat by ID"
  request {
    pathParameter<KUUID>("chatId") {
      description = "Chat ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp chat retrieved successfully"
        body<ChatWithMessages>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}
