package net.barrage.llmao.app.workflow.chat.whatsapp

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.barrage.llmao.app.workflow.chat.model.ChatWithMessages
import net.barrage.llmao.app.workflow.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.http.pathUuid
import net.barrage.llmao.core.http.user
import net.barrage.llmao.core.types.KUUID

fun Route.whatsAppHookRoutes(whatsAppAdapter: WhatsAppAdapter) {
  post("/whatsapp/webhook", infobipResponse()) {
    val input = call.receive<net.barrage.llmao.app.workflow.chat.whatsapp.model.InfobipResponse>()
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
      val user = call.user()
      val number = call.receive<UpdateNumber>()
      val created = whatsAppAdapter.addNumber(user.id, user.username, number.phoneNumber)
      call.respond(created)
    }

    put("/{numberId}", updateWhatsAppNumberForUser()) {
      val loggedInUser = call.user()
      val numberId = call.pathUuid("numberId")
      val updatedNumber = call.receive<UpdateNumber>()
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
      val chats = whatsAppAdapter.getChatByUserId(call.user().id)
      call.respond(chats)
    }
  }
}

// OpenAPI documentation
private fun infobipResponse(): RouteConfig.() -> Unit = {
  tags("3rd party hooks")
  description = "Endpoint to handle WhatsApp messages from Infobip"
  request {
    body<net.barrage.llmao.app.workflow.chat.whatsapp.model.InfobipResponse> {
      description = "The Infobip response payload"
    }
  }
  response {
    HttpStatusCode.OK to { description = "Successfully processed the message" }
    HttpStatusCode.BadRequest to { description = "Invalid request payload" }
    HttpStatusCode.InternalServerError to { description = "Internal server error" }
  }
}

private fun getWhatsAppNumbersForUser(): RouteConfig.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Retrieve WhatsApp numbers for user"
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp numbers retrieved successfully"
        body<List<net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving numbers"
        body<List<AppError>> {}
      }
  }
}

private fun createWhatsAppNumberForUser(): RouteConfig.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Create WhatsApp number for user"
  request { body<UpdateNumber> { description = "New WhatsApp number" } }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp number created successfully"
        body<net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating number"
        body<List<AppError>> {}
      }
  }
}

private fun updateWhatsAppNumberForUser(): RouteConfig.() -> Unit = {
  tags("whatsapp/numbers")
  description = "Update WhatsApp number for user"
  request {
    pathParameter<KUUID>("numberId") {
      description = "Number ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateNumber> { description = "Updated WhatsApp number" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsApp number updated successfully"
        body<net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating number"
        body<List<AppError>> {}
      }
  }
}

private fun deleteWhatsAppNumberForUser(): RouteConfig.() -> Unit = {
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

private fun getWhatsAppChatForUser(): RouteConfig.() -> Unit = {
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
        body<ChatWithMessages>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}
