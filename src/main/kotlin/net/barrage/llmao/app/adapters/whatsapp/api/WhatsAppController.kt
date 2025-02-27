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
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResponseDTO
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError

fun Route.whatsAppHookRoutes(whatsAppAdapter: WhatsAppAdapter) {
  post("/whatsapp/webhook", infobipResponse()) {
    val input = call.receive<InfobipResponseDTO>()
    whatsAppAdapter.handleIncomingMessage(input)
    call.respond(HttpStatusCode.OK)
  }
}

fun Route.whatsAppRoutes(whatsAppAdapter: WhatsAppAdapter) {
  route("/whatsapp/numbers") {
    get(getWhatsAppNumbersForUser()) {
      val loggedInUser = call.user()
      val user = whatsAppAdapter.getNumbers(loggedInUser.id)
      call.respond(user)
    }

    post(createWhatsAppNumberForUser()) {
      val loggedInUser = call.user()
      val number = call.receive<PhoneNumber>()
      val user = whatsAppAdapter.addNumber(loggedInUser.id, number)
      call.respond(user)
    }

    put("/{numberId}", updateWhatsAppNumberForUser()) {
      val loggedInUser = call.user()
      val numberId = call.pathUuid("numberId")
      val updatedNumber = call.receive<PhoneNumber>()
      val updatedUser = whatsAppAdapter.updateNumber(loggedInUser.id, numberId, updatedNumber)
      call.respond(updatedUser)
    }

    delete("/{numberId}", deleteWhatsAppNumberForUser()) {
      val loggedInUser = call.user()
      val numberId = call.pathUuid("numberId")
      whatsAppAdapter.deleteNumber(loggedInUser.id, numberId)
      call.respond(HttpStatusCode.NoContent)
    }
  }

  route("/whatsapp/chats") {
    get(getWhatsAppChatForUser()) {
      val loggedInUser = call.user()
      val chats = whatsAppAdapter.getChatByUserId(loggedInUser.id)
      call.respond(chats)
    }
  }
}

// OpenAPI documentation
private fun infobipResponse(): OpenApiRoute.() -> Unit = {
  tags("3rd party hooks")
  description = "Endpoint to handle WhatsApp messages from Infobip"
  request { body<InfobipResponseDTO> { description = "The Infobip response payload" } }
  response {
    HttpStatusCode.OK to { description = "Successfully processed the message" }
    HttpStatusCode.BadRequest to { description = "Invalid request payload" }
    HttpStatusCode.InternalServerError to { description = "Internal server error" }
  }
}

private fun getWhatsAppNumbersForUser(): OpenApiRoute.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Retrieve WhatsApp numbers for user"
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp numbers retrieved successfully"
        body<List<WhatsAppNumber>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving numbers"
        body<List<AppError>> {}
      }
  }
}

private fun createWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Create WhatsApp number for user"
  request { body<PhoneNumber> { description = "New WhatsApp number" } }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp number created successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating number"
        body<List<AppError>> {}
      }
  }
}

private fun updateWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Update WhatsApp number for user"
  request {
    pathParameter<KUUID>("numberId") {
      description = "Number ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<PhoneNumber> { description = "Updated WhatsApp number" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp number updated successfully"
        body<WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating number"
        body<List<AppError>> {}
      }
  }
}

private fun deleteWhatsAppNumberForUser(): OpenApiRoute.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Delete WhatsApp number for user"
  request {
    pathParameter<KUUID>("numberId") {
      description = "Number ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "WhatsApp number deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting number"
        body<List<AppError>> {}
      }
  }
}

private fun getWhatsAppChatForUser(): OpenApiRoute.() -> Unit = {
  tags("whatsapp/chats")
  description = "Retrieve WhatsApp chat for user"
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
        body<WhatsAppChatWithUserAndMessages>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}
