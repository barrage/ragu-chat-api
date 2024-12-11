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
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgent
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.api.http.queryPagination
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query

fun Route.adminWhatsAppRoutes(whatsAppAdapter: WhatsAppAdapter) {
  route("/admin/whatsapp/agents") {
    get(adminGetAllWhatsAppAgents()) {
      val pagination = call.query(PaginationSort::class)
      val agents = whatsAppAdapter.getAllAgents(pagination)
      call.respond(agents)
    }

    post(adminCreateWhatsAppAgent()) {
      val newAgent = call.receive<CreateAgent>()
      val createdAgent = whatsAppAdapter.createAgent(newAgent)
      call.respond(createdAgent)
    }

    route("/{agentId}") {
      get(adminGetWhatsAppAgent()) {
        val agentId = call.pathUuid("agentId")
        val agent = whatsAppAdapter.getAgent(agentId)
        call.respond(agent)
      }

      put(adminUpdateWhatsAppAgent()) {
        val agentId = call.pathUuid("agentId")
        val updatedAgent = call.receive<UpdateAgent>()
        val agent = whatsAppAdapter.updateAgent(agentId, updatedAgent)
        call.respond(agent)
      }

      put("/collections", adminUpdateWhatsAppAgentCollections()) {
        val agentId = call.pathUuid("agentId")
        val update = call.receive<UpdateCollections>()
        whatsAppAdapter.updateCollections(agentId, update)
        call.respond(HttpStatusCode.OK)
      }

      delete(adminDeleteWhatsAppAgent()) {
        val agentId = call.pathUuid("agentId")
        whatsAppAdapter.deleteAgent(agentId)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }

  route("/admin/whatsapp/numbers") {
    get("/{userId}", adminGetWhatsAppNumbersForUser()) {
      val userId = call.pathUuid("userId")
      val userNumbers = whatsAppAdapter.getNumbers(userId)
      call.respond(userNumbers)
    }

    post("/{userId}", adminAddWhatsAppNumberForUser()) {
      val userId = call.pathUuid("userId")
      val number = call.receive<PhoneNumber>()
      val user = whatsAppAdapter.addNumber(userId, number)
      call.respond(user)
    }

    put("/{userId}/{numberId}", adminUpdateWhatsAppNumberForUser()) {
      val userId = call.pathUuid("userId")
      val numberId = call.pathUuid("numberId")
      val updatedNumber = call.receive<PhoneNumber>()
      val user = whatsAppAdapter.updateNumber(userId, numberId, updatedNumber)
      call.respond(user)
    }

    delete("/{userId}/{numberId}", adminDeleteWhatsAppNumberForUser()) {
      val userId = call.pathUuid("userId")
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
      val chat = whatsAppAdapter.getChatWithMessages(chatId)
      call.respond(chat)
    }
  }
}

// OpenAPI documentation
private fun adminGetAllWhatsAppAgents(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Retrieve list of all WhatsApp agents"
  request { queryPagination() }
  response {
    HttpStatusCode.OK to
      {
        description = "List of WhatsAppAgents"
        body<CountedList<WhatsAppAgent>>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agents"
        body<List<AppError>> {}
      }
  }
}

private fun adminCreateWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Create a new WhatsApp agent"
  request { body<CreateAgent> { description = "New WhatsApp agent object" } }
  response {
    HttpStatusCode.Created to
      {
        description = "WhatsAppAgent created successfully"
        body<WhatsAppAgent>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Retrieve WhatsApp agent by ID"
  request {
    pathParameter<KUUID>("agentId") {
      description = "Agent ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsAppAgent retrieved successfully"
        body<WhatsAppAgentFull>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Update WhatsApp agent"
  request {
    pathParameter<KUUID>("agentId") {
      description = "Agent ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateAgent> { description = "Updated WhatsApp agent object" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "WhatsAppAgent updated successfully"
        body<WhatsAppAgent>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminDeleteWhatsAppAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Delete WhatsApp agent"
  request {
    pathParameter<KUUID>("agentId") {
      description = "Agent ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "WhatsAppAgent deleted successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Cannot delete active agent"
        body<List<AppError>> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "WhatsApp agent not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting agent"
        body<List<AppError>> {}
      }
  }
}

private fun adminUpdateWhatsAppAgentCollections(): OpenApiRoute.() -> Unit = {
  tags("admin/whatsapp/agents")
  description = "Update WhatsApp agent collections"
  request {
    pathParameter<KUUID>("agentId") {
      description = "Agent ID"
      example("default") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateCollections> {
      description = "The updated collections for the agent"
      required = true
    }
  }
  response {
    HttpStatusCode.OK to { description = "Collections updated successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid input or agent ID"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating collections"
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
    body<PhoneNumber> { description = "New WhatsApp number" }
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
    body<PhoneNumber> { description = "Updated WhatsApp number" }
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
  request { queryPagination() }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of WhatsApp chats"
        body<CountedList<WhatsAppChatWithUserName>>()
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
        body<WhatsAppChatWithUserAndMessages>()
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving chat"
        body<List<AppError>> {}
      }
  }
}
