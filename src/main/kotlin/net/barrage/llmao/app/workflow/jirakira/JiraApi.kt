package net.barrage.llmao.app.workflow.jirakira

import com.nimbusds.jose.util.StandardCharset
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.OffsetDateTimeSerializer

private val json = Json { ignoreUnknownKeys = true }

/** JIRA API whose availability is based on the api KEY. Instantiated per workflow. */
class JiraApi(
  private val endpoint: String,
  private val apiKey: String,
  private val client: HttpClient,
) {
  suspend fun getCurrentJiraUser(): JiraUser {
    LOG.debug("Fetching Jira user metadata")

    val account = runWithHandler<JiraUserSession>("$endpoint/rest/auth/1/session")
    return runWithHandler<JiraUser>("$endpoint/rest/api/2/user?username=${account.name}")
  }

  suspend fun getOpenIssuesForProject(projectKey: String): List<JiraIssueShort> {
    val fields = "summary,description,assignee,watcher,worklog,created,updated,priority"
    val jql =
      URLEncoder.encode(
        "project=\"${projectKey}\" AND assignee=currentUser() AND statusCategory!=Done",
        StandardCharset.UTF_8,
      )

    val response =
      runWithHandler<JiraIssueResponse>("$endpoint/rest/api/2/search?jql=$jql&fields=$fields")

    LOG.debug("Found ${response.issues.size} open issues for project {}", projectKey)
    LOG.debug("{}", response.issues.joinToString(", ") { it.key })

    return response.issues.map {
      JiraIssueShort(
        id = it.id,
        key = it.key,
        summary = it.fields.summary!!,
        assigneeName = it.fields.assignee?.name,
        priority = it.fields.priority!!.name,
        created = it.fields.created!!,
        updated = it.fields.updated!!,
      )
    }
  }

  suspend fun getIssueWorklog(issueKey: String, jiraUserKey: String): List<JiraWorklogEntryShort> {
    val issue = runWithHandler<JiraIssue>("$endpoint/rest/api/2/issue/$issueKey?fields=worklog")

    return issue.fields.worklog!!
      .worklogs
      .filter { it.author.key == jiraUserKey }
      .map {
        JiraWorklogEntryShort(
          worklogEntryId = it.id,
          authorKey = it.author.key,
          comment = it.comment,
          started = it.started,
          timeSpentSeconds = it.timeSpentSeconds,
        )
      }
  }

  suspend fun getIssueId(issueKey: String): String {
    val issue = runWithHandler<JiraIssue>("$endpoint/rest/api/2/issue/$issueKey")
    LOG.debug("Found issue ID {} for key {}", issue.id, issueKey)
    return issue.id
  }

  suspend fun getIssueKey(id: String): IssueKey {
    val issue = runWithHandler<JiraIssue>("$endpoint/rest/api/2/issue/$id")
    LOG.debug("Found issue key {} for ID {}", issue.key, id)
    return IssueKey(issue.key)
  }

  /**
   * Create a worklog entry for a Jira issue.
   *
   * `POST /rest/tempo-timesheets/4/worklogs/`
   */
  suspend fun createWorklogEntry(
    input: CreateWorklogInput,

    /** The Jira user ID to use for the worklog entry. `JIRAUSERXXXX` */
    jiraUserId: String,

    /**
     * Custom attribute key and value that represents the time slot account for billable work. The
     * value is obtained from the [getDefaultBillingAccountForIssue] call.
     */
    timeSlotAttribute: TimeSlotAttribute?,
  ): TempoWorklogEntry {
    val attributes =
      timeSlotAttribute?.let {
        mutableMapOf(timeSlotAttribute.key to WorklogInputAttributeJira(timeSlotAttribute.value))
      } ?: mutableMapOf()

    for ((key, value) in input.attributes) {
      attributes[key] = WorklogInputAttributeJira(value)
    }

    val body =
      CreateWorklogInputJira(
        originTaskId = input.issueId,
        comment = input.comment,
        started = input.started.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")),
        timeSpentSeconds = input.timeSpentSeconds,
        attributes = attributes,
        worker = jiraUserId,
      )

    LOG.debug("jira - creating worklog entry: {}", body)

    return runWithHandler<List<TempoWorklogEntry>>(
        "$endpoint/rest/tempo-timesheets/4/worklogs",
        HttpMethod.Post,
      ) {
        header("Content-Type", "application/json")
        setBody(body)
      }
      .first()
  }

  suspend fun updateWorklogEntry(input: UpdateWorklogInput): TempoWorklogEntry {
    val attributes =
      input.attributes.map { (key, value) -> key to WorklogInputAttributeJira(value) }.toMap()

    val body =
      UpdateWorklogInputJira(
        originId = input.worklogEntryId,
        started = input.started?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")),
        timeSpentSeconds = input.timeSpentSeconds,
        comment = input.comment,
        attributes = attributes,
      )

    LOG.debug("jira - updating worklog entry: {}", body)

    return runWithHandler<TempoWorklogEntry>(
      "$endpoint/rest/tempo-timesheets/4/worklogs/${input.worklogEntryId}",
      HttpMethod.Put,
    ) {
      header("Content-Type", "application/json")
      setBody(body)
    }
  }

  suspend fun getDefaultBillingAccountForIssue(issueKey: String): IssueTimeSlotAccount? {
    // The response is obtained as an application/x-javascript;charset=UTF-8 response.
    // The actual JSON will be wrapped like: `__fn__({...})`
    val body =
      runWithHandler<String>(
        "$endpoint/rest/tempo-rest/1.0/accounts/json/billingKeyList/$issueKey?callback=__fn__"
      ) {
        header("Accept", "application/x-javascript;charset=UTF-8")
      }

    try {
      val accounts =
        json.decodeFromString<IssueTimeSlotAccountResponse>(
          body.substringAfter("__fn__(", "").dropLast(1)
        )
      return accounts.values.find { it.selected } ?: accounts.values.firstOrNull()
    } catch (e: Exception) {
      LOG.error("Failed to parse Jira response for billing account: {}", body, e)
      return null
    }
  }

  suspend fun listWorklogAttributes(): List<TempoWorkAttribute> {
    LOG.debug("Listing worklog attributes")

    val attributes =
      runWithHandler<List<TempoWorkAttribute>>("$endpoint/rest/tempo-core/1/work-attribute")

    LOG.debug("Found ${attributes.size} worklog attributes")

    for (attribute in attributes) {
      LOG.debug(" - {}: {}", attribute.key, attribute.name)
    }

    return attributes
  }

  private suspend inline fun <reified T> runWithHandler(
    url: String,
    method: HttpMethod = HttpMethod.Get,
    crossinline builder: HttpRequestBuilder.() -> Unit = {},
  ): T {
    val response =
      client.request(url) {
        this.method = method
        header("Authorization", "Bearer $apiKey")
        builder()
      }

    handleResponseError(url, response)

    try {
      return response.body<T>()
    } catch (e: Exception) {
      LOG.error("Failed to parse Jira response ({})", url, e)
      throw AppError.internal("Failed to parse Jira response")
    }
  }

  private suspend fun handleResponseError(url: String, response: HttpResponse) {
    if (response.status.isSuccess()) {
      return
    }
    val error =
      try {
        response.body<JiraError>()
      } catch (e: Exception) {
        LOG.error("Failed to parse Jira error ({})", url, e)
        throw AppError.internal("An error occurred when calling the Jira API")
      }
    // Log the original error, now the Throwable
    LOG.error("Jira API call failed ({})", url)
    LOG.error("$error")
    throw error
  }
}

@Serializable
data class JiraError(val errorMessages: List<String>, val errors: Map<String, String>) :
  Throwable(errorMessages.joinToString(", "))

/** Customer account attribute. */
@Serializable data class TimeSlotAttribute(val key: String, val value: String)

/** Original Jira user model. Also used in JiraKira sessions. */
@Serializable
data class JiraUser(
  val self: String,
  val name: String,
  val key: String,
  @SerialName("emailAddress") val email: String,
  val displayName: String,
  val active: Boolean,
  val timeZone: String,
)

/** Summarized version of [JiraIssue]. */
@Serializable
data class JiraIssueShort(
  val id: String,
  val key: String,
  val summary: String,
  val assigneeName: String?,
  val priority: String,
  val created: KOffsetDateTime,
  val updated: KOffsetDateTime,
)

/** Summarized version of [JiraWorklogEntry]. */
@Serializable
data class JiraWorklogEntryShort(
  val worklogEntryId: String,
  val authorKey: String,
  val comment: String,
  val started: KOffsetDateTime,
  val timeSpentSeconds: Int,
)

/**
 * Original Jira response when listing issues.
 *
 * `GET /rest/api/2/search`
 */
@Serializable
private data class JiraIssueResponse(
  val startAt: Int,
  val maxResults: Int,
  val total: Int,
  val issues: List<JiraIssue>,
)

/** Original Jira issue model. */
@Serializable
private data class JiraIssue(
  val id: String,
  val self: String,
  val key: String,
  val fields: JiraIssueFields,
)

/** Configured for the JQL we are sending when searching open issues. */
@Serializable
private data class JiraIssueFields(
  val summary: String? = null,
  val created: KOffsetDateTime? = null,
  val updated: KOffsetDateTime? = null,
  val description: String? = null,
  val worklog: JiraWorklog? = null,
  val assignee: JiraUser? = null,
  val priority: JiraIssuePriority? = null,
)

/** Shortened version of the priority object from Jira. */
@Serializable private data class JiraIssuePriority(val name: String)

/** Jira worklog list object. */
@Serializable
private data class JiraWorklog(
  val startAt: Int,
  val maxResults: Int,
  val total: Int,
  val worklogs: List<JiraWorklogEntry>,
)

/** Original Jira worklog entry model. */
@Serializable
private data class JiraWorklogEntry(
  val self: String,
  val author: JiraUser,
  val updateAuthor: JiraUser,
  val comment: String,
  val created: KOffsetDateTime,
  val updated: KOffsetDateTime,
  val started: KOffsetDateTime,
  val timeSpent: String,
  val timeSpentSeconds: Int,
  val id: String,
  val issueId: String,
)

@Serializable
private data class JiraUserSession(
  val self: String,
  val name: String,
  val loginInfo: JiraLoginInfo,
)

@Serializable
private data class JiraLoginInfo(
  val failedLoginCount: Int,
  val loginCount: Int,
  val lastFailedLoginTime: KOffsetDateTime,
  val previousLoginTime: KOffsetDateTime,
)

/** Returned when calling the add worklog endpoint. */
@Serializable
data class TempoWorklogEntry(
  val billableSeconds: Int,
  val timeSpent: String,
  val comment: String,
  val location: Location,
  val attributes: Map<String, WorkAttribute>,
  val timeSpentSeconds: Int,
  val issue: Issue?,
  val tempoWorklogId: Int,
  val originId: Int,
  val worker: String,
  val updater: String,
  val started: String,
  val originTaskId: Int,
  val dateCreated: String,
  val dateUpdated: String,
)

@Serializable data class Location(val name: String, val id: Int)

@Serializable
data class WorkAttribute(
  val workAttributeId: Int,
  val value: String,
  val type: String,
  val key: String,
  val name: String,
)

@Serializable
data class Issue(
  val key: String,
  val id: Int,
  val accountKey: String,
  val estimatedRemainingSeconds: Int? = null,
  val epicIssue: EpicIssue?,
  val projectId: Int,
  val projectKey: String,
  val issueType: String,
  val versions: List<Int>,
  val summary: String,
  val reporterKey: String,
  val issueStatus: String,
  val components: List<String>,
  val epicKey: String?,
  val internalIssue: Boolean,
)

@Serializable data class EpicIssue(val issueType: String, val summary: String)

/**
 * Obtained when instantiating JiraKira. These attributes represent custom attributes that can be
 * used in worklog entries.
 */
@Serializable
data class TempoWorkAttribute(
  val id: Int,
  val key: String,
  val name: String,
  val type: TempoWorkAttributeType,
  val required: Boolean,
  val sequence: Int,
  val staticListValues: List<TempoStaticListValue>? = null,
  val externalUrl: String? = null,
)

/** The type of the custom attribute. */
@Serializable
data class TempoWorkAttributeType(val name: String, val value: String, val systemType: Boolean)

/** A value that can be used for the custom attribute. */
@Serializable
data class TempoStaticListValue(
  val id: Int,
  val name: String,
  val value: String,
  val removed: Boolean,
  val sequence: Int,
  val workAttributeId: Int,
)

@Serializable data class IssueTimeSlotAccountResponse(val values: List<IssueTimeSlotAccount>)

@Serializable
data class IssueTimeSlotAccount(val key: String, val value: String, val selected: Boolean)

@Serializable(with = CreateWorklogInputSerializer::class)
data class CreateWorklogInput(
  val issueId: String,
  val comment: String,
  val started: KOffsetDateTime,
  val timeSpentSeconds: Int,
  @SerialName("attributes") val attributes: HashMap<String, String> = HashMap(),
)

@Serializable
private data class CreateWorklogInputJira(
  val originTaskId: String,
  val comment: String,
  val started: String,
  val timeSpentSeconds: Int,
  val attributes: Map<String, WorklogInputAttributeJira>,
  val worker: String,
)

@Serializable private data class WorklogInputAttributeJira(val value: String)

object CreateWorklogInputSerializer : KSerializer<CreateWorklogInput> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("CreateWorklogInput") {
      element<String>("issueId")
      element<String>("comment")
      element("started", OffsetDateTimeSerializer.descriptor)
      element<Int>("timeSpentSeconds")
      element<HashMap<String, String>>("attributes")
    }

  override fun serialize(encoder: Encoder, value: CreateWorklogInput) {
    val compositeEncoder = encoder.beginStructure(descriptor)
    compositeEncoder.encodeStringElement(descriptor, 0, value.issueId)
    compositeEncoder.encodeStringElement(descriptor, 1, value.comment)
    compositeEncoder.encodeSerializableElement(
      descriptor,
      2,
      OffsetDateTimeSerializer,
      value.started,
    )
    compositeEncoder.encodeIntElement(descriptor, 3, value.timeSpentSeconds)
    compositeEncoder.encodeSerializableElement(
      descriptor,
      3,
      MapSerializer(String.serializer(), String.serializer()),
      value.attributes,
    )
    compositeEncoder.endStructure(descriptor)
  }

  override fun deserialize(decoder: Decoder): CreateWorklogInput {
    val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
    val attributes = HashMap<String, String>()

    // Extract known fields
    val issueId =
      jsonObject["issueId"]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing required field: issueId")
    val comment =
      jsonObject["comment"]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing required field: comment")
    val started =
      jsonObject["started"]?.let { Json.decodeFromJsonElement(OffsetDateTimeSerializer, it) }
        ?: throw SerializationException("Missing required field: started")
    val timeSpentSeconds =
      jsonObject["timeSpentSeconds"]?.jsonPrimitive?.int
        ?: throw SerializationException("Missing required field: timeSpentSeconds")

    // Collect unrecognized fields
    jsonObject.forEach { (key, value) ->
      when (key) {
        "issueId",
        "comment",
        "started",
        "timeSpentSeconds" -> {} // Skip known fields
        else -> attributes[key] = value.jsonPrimitive.content
      }
    }

    return CreateWorklogInput(
      issueId = issueId,
      comment = comment,
      started = started,
      timeSpentSeconds = timeSpentSeconds,
      attributes = attributes,
    )
  }
}

@Serializable(with = UpdateWorklogInputSerializer::class)
data class UpdateWorklogInput(
  val worklogEntryId: Int,
  val started: KOffsetDateTime?,
  val timeSpentSeconds: Int?,
  val comment: String,
  val attributes: Map<String, String>,
)

@Serializable
private data class UpdateWorklogInputJira(
  val originId: Int,
  val started: String?,
  val timeSpentSeconds: Int?,
  val comment: String?,
  val attributes: Map<String, WorklogInputAttributeJira>,
)

object UpdateWorklogInputSerializer : KSerializer<UpdateWorklogInput> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("UpdateWorklogInput") {
      element<Int>("worklogEntryId")
      element("started", OffsetDateTimeSerializer.descriptor)
      element<Int?>("timeSpentSeconds")
      element<String?>("comment")
      element<Map<String, String>>("attributes")
    }

  @OptIn(ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: UpdateWorklogInput) {
    val compositeEncoder = encoder.beginStructure(descriptor)
    compositeEncoder.encodeIntElement(descriptor, 0, value.worklogEntryId)
    value.started?.let {
      compositeEncoder.encodeNullableSerializableElement(
        descriptor,
        1,
        OffsetDateTimeSerializer,
        it,
      )
    }
    value.timeSpentSeconds?.let {
      compositeEncoder.encodeNullableSerializableElement(descriptor, 2, Int.serializer(), it)
    }
    compositeEncoder.encodeStringElement(descriptor, 3, value.comment)
    compositeEncoder.encodeSerializableElement(
      descriptor,
      4,
      MapSerializer(String.serializer(), String.serializer()),
      value.attributes,
    )
    compositeEncoder.endStructure(descriptor)
  }

  override fun deserialize(decoder: Decoder): UpdateWorklogInput {
    val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
    val attributes = HashMap<String, String>()

    // Extract known fields
    val worklogEntryId =
      jsonObject["worklogEntryId"]?.jsonPrimitive?.int
        ?: throw SerializationException("Missing required field: worklogEntryId")
    val started =
      jsonObject["started"]?.let { Json.decodeFromJsonElement(OffsetDateTimeSerializer, it) }
    val timeSpentSeconds = jsonObject["timeSpentSeconds"]?.jsonPrimitive?.int
    val comment =
      jsonObject["comment"]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing required field: comment")

    // Collect unrecognized fields
    jsonObject.forEach { (key, value) ->
      when (key) {
        "worklogEntryId",
        "started",
        "timeSpentSeconds",
        "comment" -> {} // Skip known fields
        else -> attributes[key] = value.jsonPrimitive.content
      }
    }

    return UpdateWorklogInput(
      worklogEntryId = worklogEntryId,
      started = started,
      timeSpentSeconds = timeSpentSeconds,
      comment = comment,
      attributes = attributes,
    )
  }
}
