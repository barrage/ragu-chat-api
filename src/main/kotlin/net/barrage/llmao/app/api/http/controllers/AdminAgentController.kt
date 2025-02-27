package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import net.barrage.llmao.app.api.http.dto.SearchFiltersAdminAgentsQuery
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryListAgentsFilters
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.app.api.http.queryParam
import net.barrage.llmao.app.api.http.runWithImage
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateCollectionsResult
import net.barrage.llmao.core.models.UpdateTools
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingsService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.tryUuid

/** Agent DTO for display purposes. */
@Serializable
data class AgentDisplay(
  val agent: Agent,
  val configuration: AgentConfiguration? = null,
  val collections: List<AgentCollection>? = null,
  val whatsapp: Boolean = false,
)

fun AgentWithConfiguration.toAgentDisplay(activeWappAgentId: KUUID?) =
  AgentDisplay(
    agent = agent,
    configuration = configuration,
    whatsapp = agent.id == activeWappAgentId,
  )

fun AgentFull.toAgentDisplay(activeWappAgentId: KUUID?) =
  AgentDisplay(
    agent = agent,
    configuration = configuration,
    collections = collections,
    whatsapp = agent.id == activeWappAgentId,
  )

fun Route.adminAgentsRoutes(agentService: AgentService, settingsService: SettingsService) {
  route("/admin/agents") {
    get(adminGetAllAgents()) {
      val pagination = call.query(PaginationSort::class)
      val filters = call.query(SearchFiltersAdminAgentsQuery::class).toSearchFiltersAdminAgents()
      val activeWappAgentId = settingsService.get(SettingKey.WHATSAPP_AGENT_ID)?.let { tryUuid(it) }
      val agents =
        agentService.listAgentsAdmin(pagination, filters).map {
          it.toAgentDisplay(activeWappAgentId)
        }
      call.respond(HttpStatusCode.OK, agents)
    }

    post(createAgent()) {
      val newAgent: CreateAgent = call.receive()
      val agent = agentService.create(newAgent)
      call.respond(HttpStatusCode.Created, agent)
    }

    route("/{id}") {
      get(adminGetAgent()) {
        val id = call.pathUuid("id")
        val activeWappAgentId =
          settingsService.get(SettingKey.WHATSAPP_AGENT_ID)?.let { tryUuid(it) }
        val agent = agentService.getFull(id)
        call.respond(HttpStatusCode.OK, agent.toAgentDisplay(activeWappAgentId))
      }

      put(updateAgent()) {
        val agentId = call.pathUuid("id")
        val updatedAgent: UpdateAgent = call.receive()
        val agent = agentService.update(agentId, updatedAgent)
        call.respond(HttpStatusCode.OK, agent)
      }

      delete(deleteAgent()) {
        val agentId = call.pathUuid("id")
        agentService.delete(agentId)
        call.respond(HttpStatusCode.NoContent)
      }

      route("/tools") {
        get(getAgentTools()) {
          val agentId = call.pathUuid("id")
          val tools = agentService.listAgentTools(agentId)
          call.respond(HttpStatusCode.OK, tools)
        }

        put(updateAgentTools()) {
          val agentId = call.pathUuid("id")
          val updateTools = call.receive<UpdateTools>()
          agentService.updateAgentTools(agentId, updateTools)
          call.respond(HttpStatusCode.OK)
        }
      }

      put("/collections", updateAgentCollections()) {
        val agentId = call.pathUuid("id")
        val update: UpdateCollections = call.receive()
        val updateResult = agentService.updateCollections(agentId, update)
        call.respond(HttpStatusCode.OK, updateResult)
      }

      route("/avatars") {
        post(uploadAgentAvatar()) {
          val agentId = call.pathUuid("id")
          call.runWithImage(agentId) { image ->
            agentService.uploadAgentAvatar(agentId, image)
            call.respond(HttpStatusCode.Created, image.name)
          }
        }

        delete(deleteAgentAvatar()) {
          val agentId = call.pathUuid("id")
          agentService.deleteAgentAvatar(agentId)
          call.respond(HttpStatusCode.NoContent)
        }
      }

      route("/versions") {
        get(getAgentVersions()) {
          val agentId = call.pathUuid("id")
          val pagination = call.query(PaginationSort::class)
          val versions = agentService.getAgentConfigurationVersions(agentId, pagination)
          call.respond(HttpStatusCode.OK, versions)
        }

        route("/{versionId}") {
          get(getAgentConfigurationWithEvaluationCounts()) {
            val agentId = call.pathUuid("id")
            val versionId = call.pathUuid("versionId")
            val version = agentService.getAgentConfigurationWithEvaluationCounts(agentId, versionId)
            call.respond(HttpStatusCode.OK, version)
          }

          get("/messages", getAgentConfigurationEvaluatedMessages()) {
            val agentId = call.pathUuid("id")
            val versionId = call.pathUuid("versionId")
            val evaluation: Boolean? = call.queryParam("evaluation")?.toBoolean()
            val pagination = call.query(PaginationSort::class)
            val version =
              agentService.getAgentConfigurationEvaluatedMessages(
                agentId,
                versionId,
                evaluation,
                pagination,
              )
            call.respond(HttpStatusCode.OK, version)
          }

          put("/rollback", rollbackAgentVersion()) {
            val agentId = call.pathUuid("id")
            val versionId = call.pathUuid("versionId")
            val version = agentService.rollbackVersion(agentId, versionId)
            call.respond(HttpStatusCode.OK, version)
          }
        }
      }
    }

    delete("/collections", removeCollectionFromAllAgents()) {
      val collection = call.queryParam("collection")!!
      val provider = call.queryParam("provider")!!
      agentService.removeCollectionFromAllAgents(collection, provider)
      call.respond(HttpStatusCode.NoContent)
    }

    get("/tools", getAgentTools()) {
      val tools = agentService.listAvailableAgentTools()
      call.respond(HttpStatusCode.OK, tools)
    }
  }
}

// OpenAPI documentation

private fun getAgentTools(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Retrieve list of all available agent tools"
  response {
    HttpStatusCode.OK to
      {
        description = "A list of available agent tools"
        body<List<String>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent tools"
        body<List<AppError>> {}
      }
  }
}

private fun updateAgentTools(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent's tools"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateTools> {
      description = "The updated tools for the agent"
      required = true
    }
  }
  response {
    HttpStatusCode.OK to { description = "Tools updated successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid input or agent ID"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating tools"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetAllAgents(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Retrieve list of all agents"
  request {
    queryPaginationSort()
    queryListAgentsFilters()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Agent objects representing all the agents"
        body<CountedList<AgentWithConfiguration>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agents"
        body<List<AppError>> {}
      }
  }
}

private fun adminGetAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Retrieve an agent by ID"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An Agent object with its collections"
        body<AgentFull> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Agent not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<AppError>> {}
      }
  }
}

private fun createAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Create a new agent"
  request { body<CreateAgent> { description = "New agent object" } }
  response {
    HttpStatusCode.Created to
      {
        description = "An Agent object representing the new agent"
        body<AgentWithConfiguration> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating agent"
        body<List<AppError>> {}
      }
  }
}

private fun updateAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateAgent> { description = "Updated agent object" }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An Agent object representing the updated agent"
        body<AgentWithConfiguration> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating agent"
        body<List<AppError>> {}
      }
  }
}

private fun deleteAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Delete an agent"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Agent deleted successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Cannot delete active agent"
        body<List<AppError>> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Agent not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting agent"
        body<List<AppError>> {}
      }
  }
}

private fun updateAgentCollections(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent's collections"

  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<UpdateCollections> {
      description = "The updated collections for the agent"
      required = true
    }
  }

  response {
    HttpStatusCode.OK to
      {
        description = "Collection"
        body<UpdateCollectionsResult> { description = "Result of the update" }
      }
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

private fun removeCollectionFromAllAgents(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Remove a collection from all agents"
  request {
    queryParameter<String>("collection") {
      description = "Collection name"
      example("example") { value = "Kusturica_small" }
    }
    queryParameter<String>("provider") {
      description = "Collection provider"
      example("example") { value = "weaviate" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Collection removed successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid input"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while removing collection"
        body<List<AppError>> {}
      }
  }
}

private fun getAgentVersions(): OpenApiRoute.() -> Unit = {
  summary = "Get agent versions"
  description = "Gets counted list of agent configuration versions."
  tags("admin/agents/versions")
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of AgentConfiguration objects representing all the agent versions"
        body<CountedList<AgentConfiguration>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent versions"
        body<List<AppError>> {}
      }
  }
}

private fun getAgentConfigurationWithEvaluationCounts(): OpenApiRoute.() -> Unit = {
  summary = "Get agent version with evaluation counts"
  description = "Gets agent current version of agent configuration with evaluation counts."
  tags("admin/agents/versions")
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("versionId") {
      description = "Agent version ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An AgentConfiguration object representing the agent version"
        body<AgentConfigurationWithEvaluationCounts> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Requested agent version not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent version"
        body<List<AppError>> {}
      }
  }
}

private fun getAgentConfigurationEvaluatedMessages(): OpenApiRoute.() -> Unit = {
  summary = "Get evaluated messages"
  description = "Gets evaluated messages for a given agent version of agent configuration."
  tags("admin/agents/versions")
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("versionId") {
      description = "Agent version ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    queryParameter<Boolean>("evaluation") {
      description = "Filter by evaluation"
      required = false
      example("default") { value = true }
    }
    queryPaginationSort()
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An AgentConfiguration object representing the agent version"
        body<CountedList<Message>> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Requested agent version not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent version"
        body<List<AppError>> {}
      }
  }
}

private fun rollbackAgentVersion(): OpenApiRoute.() -> Unit = {
  summary = "Rollback agent version"
  description = "Rollbacks agent to a given version of agent configuration."
  tags("admin/agents/versions")
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    pathParameter<KUUID>("versionId") {
      description = "Agent version ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An AgentConfiguration object representing the agent version"
        body<AgentConfiguration> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Requested agent version not found"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent version"
        body<List<AppError>> {}
      }
  }
}

private fun uploadAgentAvatar(): OpenApiRoute.() -> Unit = {
  tags("admin/agents/avatars")
  description = "Upload agent avatar"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<ByteArray> {
      description = "Avatar image, .jpeg or .png format"
      mediaTypes = setOf(ContentType.Image.JPEG, ContentType.Image.PNG)
      required = true
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Agent with avatar"
        body<Agent> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while uploading avatar"
        body<List<AppError>> {}
      }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid input"
        body<List<AppError>> {}
      }
  }
}

private fun deleteAgentAvatar(): OpenApiRoute.() -> Unit = {
  tags("admin/agents/avatars")
  description = "Delete agent avatar"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Agent avatar deleted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deleting avatar"
        body<List<AppError>> {}
      }
  }
}
