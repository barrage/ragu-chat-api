package net.barrage.llmao.app.api.http.controllers.specialists

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import net.barrage.llmao.app.api.http.queryParam
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.app.specialist.jirakira.JiraKiraKeyStore
import net.barrage.llmao.app.specialist.jirakira.JiraKiraRepository
import net.barrage.llmao.app.specialist.jirakira.WorklogAttribute
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason

fun Route.jiraKiraUserRoutes(jiraKiraRepository: JiraKiraKeyStore) {
  post("/jirakira/key", setJiraKiraKey()) {
    val userId = call.user().id
    val key = call.receive<String>()
    jiraKiraRepository.setUserApiKey(userId, key)
    call.respond(HttpStatusCode.NoContent)
  }

  delete("/jirakira/key", removeJiraKiraKey()) {
    val userId = call.user().id
    jiraKiraRepository.removeUserApiKey(userId)
    call.respond(HttpStatusCode.NoContent)
  }
}

fun Route.jiraKiraAdminRoutes(jiraKiraRepository: JiraKiraRepository) {
  @Serializable
  data class AddWorklogAttribute(val key: String, val description: String, val required: Boolean)

  get("/jirakira/worklog-attributes", listJiraKiraWorklogAttributes()) {
    val attributes = jiraKiraRepository.listAllWorklogAttributes()
    call.respond(HttpStatusCode.OK, attributes)
  }

  post("/jirakira/worklog-attributes", upsertJiraKiraWorklogAttribute()) {
    val body = call.receive<AddWorklogAttribute>()
    jiraKiraRepository.upsertWorklogAttribute(body.key, body.description, body.required)
    call.respond(HttpStatusCode.OK)
  }

  delete("/jirakira/worklog-attributes", removeJiraKiraWorklogAttribute()) {
    val key =
      call.queryParam("key")
        ?: throw AppError.api(ErrorReason.InvalidParameter, "Missing attribute key")
    jiraKiraRepository.removeWorklogAttribute(key)
    call.respond(HttpStatusCode.OK)
  }
}

private fun setJiraKiraKey(): RouteConfig.() -> Unit = {
  tags("jirakira")
  description = "Set Jira API key"
  request { body<String> { description = "Jira API key" } }
  response {
    HttpStatusCode.NoContent to { description = "Jira API key set successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while setting Jira API key"
        body<List<AppError>> {}
      }
  }
}

private fun removeJiraKiraKey(): RouteConfig.() -> Unit = {
  tags("jirakira")
  description = "Remove Jira API key"
  response {
    HttpStatusCode.NoContent to { description = "Jira API key removed successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while removing Jira API key"
        body<List<AppError>> {}
      }
  }
}

private fun listJiraKiraWorklogAttributes(): RouteConfig.() -> Unit = {
  tags("jirakira")
  description = "List Jira worklog attributes"
  response {
    HttpStatusCode.OK to
      {
        description = "Jira worklog attributes listed successfully"
        body<List<WorklogAttribute>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while listing Jira worklog attributes"
        body<List<AppError>> {}
      }
  }
}

private fun upsertJiraKiraWorklogAttribute(): RouteConfig.() -> Unit = {
  tags("jirakira")
  description = "Upsert Jira worklog attribute"
  request { body<WorklogAttribute> { description = "Jira worklog attribute" } }
  response {
    HttpStatusCode.OK to { description = "Jira worklog attribute upserted successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while upserting Jira worklog attribute"
        body<List<AppError>> {}
      }
  }
}

private fun removeJiraKiraWorklogAttribute(): RouteConfig.() -> Unit = {
  tags("jirakira")
  description = "Remove Jira worklog attribute"
  request {
    queryParameter<String>("key") {
      description = "Jira worklog attribute key"
      example("example") { value = "123" }
    }
  }
  response {
    HttpStatusCode.OK to { description = "Jira worklog attribute removed successfully" }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while removing Jira worklog attribute"
        body<List<AppError>> {}
      }
  }
}
