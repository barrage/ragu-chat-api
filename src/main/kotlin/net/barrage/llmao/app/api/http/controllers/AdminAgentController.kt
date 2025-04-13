package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import net.barrage.llmao.app.api.http.dto.SearchFiltersAdminAgentsQuery
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryListAgentsFilters
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.app.api.http.queryParam
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.api.admin.AdminAgentService
import net.barrage.llmao.core.api.admin.AdminSettingsService
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentCollection
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.AgentGroupUpdate
import net.barrage.llmao.core.model.AgentUpdateTools
import net.barrage.llmao.core.model.AgentWithConfiguration
import net.barrage.llmao.core.model.CreateAgent
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.ImageType
import net.barrage.llmao.core.model.Message
import net.barrage.llmao.core.model.SettingKey
import net.barrage.llmao.core.model.UpdateAgent
import net.barrage.llmao.core.model.UpdateCollections
import net.barrage.llmao.core.model.UpdateCollectionsResult
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.string
import net.barrage.llmao.tryUuid
import net.barrage.llmao.types.KUUID

fun Route.adminAgentsRoutes(agentService: AdminAgentService, settings: AdminSettingsService) {
  val maxImageUploadSize = this.environment.config.string("blob.image.maxFileSize").toLong()

  route("/admin/agents") {
    get(adminGetAllAgents()) {
      val pagination = call.query(PaginationSort::class)
      val filters = call.query(SearchFiltersAdminAgentsQuery::class).toSearchFiltersAdminAgents()
      val activeWappAgentId = settings.get(SettingKey.WHATSAPP_AGENT_ID)?.let { tryUuid(it) }
      val agents =
        agentService.listAgents(pagination, filters).map { it.toAgentDisplay(activeWappAgentId) }
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
        val activeWappAgentId = settings.get(SettingKey.WHATSAPP_AGENT_ID)?.let { tryUuid(it) }
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

      route("/groups") {
        put(updateAgentGroups()) {
          val agentId = call.pathUuid("id")
          val groups = call.receive<AgentGroupUpdate>()
          agentService.updateGroups(agentId, groups)
          call.respond(HttpStatusCode.NoContent)
        }
      }

      route("/tools") {
        get(getAgentTools()) {
          val agentId = call.pathUuid("id")
          val tools = agentService.listAgentTools(agentId)
          call.respond(HttpStatusCode.OK, tools)
        }

        put(updateAgentTools()) {
          val agentId = call.pathUuid("id")
          val updateTools = call.receive<AgentUpdateTools>()
          agentService.updateAgentTools(agentId, updateTools)
          call.respond(HttpStatusCode.OK)
        }
      }

      route("/avatars") {
        post(uploadAgentAvatar()) {
          val agentId = call.pathUuid("id")

          val contentLength =
            call.request.contentLength()
              ?: throw AppError.api(
                ErrorReason.InvalidParameter,
                "Expected content in request body",
              )

          if (contentLength > maxImageUploadSize) {
            throw AppError.api(
              ErrorReason.PayloadTooLarge,
              "Image size exceeds the maximum allowed size of ${maxImageUploadSize / 1024 / 1024}MB",
            )
          }

          val data = call.request.receiveChannel().toByteArray()

          val imageType = ImageType.fromContentType(call.request.contentType().toString())

          val input = Image(data, imageType)

          val path = agentService.uploadAgentAvatar(agentId, input)

          call.respond(HttpStatusCode.Created, path)
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

      put("/collections", updateAgentCollections()) {
        val agentId = call.pathUuid("id")
        val update: UpdateCollections = call.receive()
        val updateResult = agentService.updateCollections(agentId, update)
        call.respond(HttpStatusCode.OK, updateResult)
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

// DTO

@Serializable
data class AgentDisplay(
  val agent: Agent,
  val configuration: AgentConfiguration? = null,
  val collections: List<AgentCollection>? = null,
  val groups: List<String>? = null,
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
    groups = groups,
    whatsapp = agent.id == activeWappAgentId,
  )

// OpenAPI documentation

private fun updateAgentGroups(): RouteConfig.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent's groups"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<AgentGroupUpdate> {
      description =
        "Groups to add/remove to/from the agent. Note sending a group in both lists will cause it to be added then removed, effectively doing nothing."
      required = true
    }
  }
  response {
    HttpStatusCode.NoContent to { description = "Groups updated successfully" }
    HttpStatusCode.BadRequest to
      {
        description = "Invalid input or agent ID"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating groups"
        body<List<AppError>> {}
      }
  }
}

private fun getAgentTools(): RouteConfig.() -> Unit = {
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

private fun updateAgentTools(): RouteConfig.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent's tools"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
    body<AgentUpdateTools> {
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

private fun adminGetAllAgents(): RouteConfig.() -> Unit = {
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

private fun adminGetAgent(): RouteConfig.() -> Unit = {
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

private fun createAgent(): RouteConfig.() -> Unit = {
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

private fun updateAgent(): RouteConfig.() -> Unit = {
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

private fun deleteAgent(): RouteConfig.() -> Unit = {
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

private fun updateAgentCollections(): RouteConfig.() -> Unit = {
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

private fun removeCollectionFromAllAgents(): RouteConfig.() -> Unit = {
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

private fun getAgentVersions(): RouteConfig.() -> Unit = {
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

private fun getAgentConfigurationWithEvaluationCounts(): RouteConfig.() -> Unit = {
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

private fun getAgentConfigurationEvaluatedMessages(): RouteConfig.() -> Unit = {
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

private fun rollbackAgentVersion(): RouteConfig.() -> Unit = {
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

private fun uploadAgentAvatar(): RouteConfig.() -> Unit = {
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

private fun deleteAgentAvatar(): RouteConfig.() -> Unit = {
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
