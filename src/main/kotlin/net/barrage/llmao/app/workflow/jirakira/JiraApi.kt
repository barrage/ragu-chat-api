package net.barrage.llmao.app.workflow.jirakira

import com.nimbusds.jose.util.StandardCharset
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
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
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.OffsetDateTimeSerializer
import net.barrage.llmao.error.AppError

private val json = Json { ignoreUnknownKeys = true }

/** JIRA API whose availability is based on the api KEY. Instantiated per workflow. */
class JiraApi(
  private val endpoint: String,
  private val apiKey: String,
  private val client: HttpClient,
) {
  suspend fun getCurrentJiraUser(): JiraUser {
    LOG.debug("Fetching Jira user metadata")
    val account =
      client
        .get("$endpoint/rest/auth/1/session") { header("Authorization", "Bearer $apiKey") }
        .body<JiraUserSession>()
    return client.get(account.self) { header("Authorization", "Bearer $apiKey") }.body<JiraUser>()
  }

  suspend fun getOpenIssuesForProject(input: ListOpenIssuesInput): ListOpenIssuesOutput {
    LOG.debug("Listing open Jira issues for project ${input.project}")

    val fields = "summary,description,assignee,watcher,worklog,created,updated,priority"
    val jql =
      URLEncoder.encode(
        "project=\"${input.project}\" AND assignee=currentUser() AND statusCategory!=Done",
        StandardCharset.UTF_8,
      )
    val issues =
      client
        .get("$endpoint/rest/api/2/search?jql=$jql&fields=$fields") {
          header("Authorization", "Bearer $apiKey")
        }
        .body<JiraIssueResponse>()
        .issues
        .map {
          JiraIssueShort(
            id = it.id,
            key = it.key,
            summary = it.fields.summary,
            assigneeName = it.fields.assignee?.name,
            priority = it.fields.priority.name,
            created = it.fields.created,
            updated = it.fields.updated,
          )
        }

    LOG.debug("Found ${issues.size} open issues for project {}", input.project)

    LOG.debug("{}", issues.joinToString(", ") { it.key })

    return ListOpenIssuesOutput(issues)
  }

  suspend fun getIssueId(issueKey: String): String {
    LOG.debug("Fetching issue ID for key: {}", issueKey)

    val issue =
      client
        .get("$endpoint/rest/api/2/issue/$issueKey") { header("Authorization", "Bearer $apiKey") }
        .body<JiraIssue>()

    LOG.debug("Found issue ID {} for key {}", issue.id, issueKey)

    return issue.id
  }

  suspend fun getIssueKey(id: String): IssueKey {
    LOG.debug("Fetching issue key for ID: {}", id)

    val issue =
      client
        .get("$endpoint/rest/api/2/issue/$id") { header("Authorization", "Bearer $apiKey") }
        .body<JiraIssue>()

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

    /** Custom attribute key that represents the time slot account for billable work. */
    timeSlotAttributeKey: String,

    /** The value to use for the time slot account. */
    timeSlotAccountValue: String?,
  ): CreateWorklogOutput {
    LOG.debug("Creating worklog entry for issue ${input.issueId}")

    val attributes =
      timeSlotAccountValue?.let {
        mutableMapOf(timeSlotAttributeKey to CreateWorklogInputAttributeJira(timeSlotAccountValue))
      } ?: mutableMapOf()

    for ((key, value) in input.attributes) {
      attributes[key] = CreateWorklogInputAttributeJira(value)
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    val body =
      CreateWorklogInputJira(
        originTaskId = input.issueId,
        comment = input.comment,
        started = formatter.format(input.started),
        timeSpentSeconds = input.timeSpentSeconds,
        attributes = attributes,
        worker = jiraUserId,
      )

    val response =
      client.post("$endpoint/rest/tempo-timesheets/4/worklogs") {
        header("Authorization", "Bearer $apiKey")
        header("Content-Type", "application/json")
        setBody(body)
      }

    if (!response.status.isSuccess()) {
      LOG.error("Worklog creation failed: ${response.bodyAsText()}")
      throw AppError.internal("Failed to create worklog entry")
    }

    val entry = response.body<List<TempoWorklog>>().first()

    LOG.debug(
      "Worklog for issue ${input.issueId} created successfully for issue (time: {})",
      entry.timeSpent,
    )

    return CreateWorklogOutput(entry)
  }

  suspend fun getDefaultBillingAccountForIssue(issueKey: String): IssueTimeSlotAccount? {
    // The response is obtained as an application/x-javascript;charset=UTF-8 response.
    // The actual JSON will be wrapped like: `__fn__({...})`
    val response =
      client.get(
        "$endpoint/rest/tempo-rest/1.0/accounts/json/billingKeyList/$issueKey?callback=__fn__"
      ) {
        header("Authorization", "Bearer $apiKey")
        header("Accept", "application/x-javascript;charset=UTF-8")
      }

    LOG.debug("Response: {}", response)

    val body = response.bodyAsText()

    LOG.debug("Json: {}", response)

    val accounts =
      json.decodeFromString<IssueTimeSlotAccountResponse>(
        body.substringAfter("__fn__(", "").dropLast(1)
      )

    return accounts.values.find { it.selected } ?: accounts.values.firstOrNull()
  }

  suspend fun listWorklogAttributes(): List<TempoWorkAttribute> {
    LOG.debug("Listing worklog attributes")
    val attributes =
      client
        .get("$endpoint/rest/tempo-core/1/work-attribute") {
          header("Authorization", "Bearer $apiKey")
        }
        .body<List<TempoWorkAttribute>>()
    LOG.debug("Found ${attributes.size} worklog attributes")
    for (attribute in attributes) {
      LOG.debug(" - {}: {}", attribute.key, attribute.name)
    }
    return attributes
  }

  @Serializable
  private data class CreateWorklogInputJira(
    val originTaskId: String,
    val comment: String,
    val started: String,
    val timeSpentSeconds: Int,
    val attributes: Map<String, CreateWorklogInputAttributeJira>,
    val worker: String,
  )

  @Serializable private data class CreateWorklogInputAttributeJira(val value: String)

  @Serializable data class IssueKey(val issueKey: String)

  @Serializable data class CreateWorklogOutput(val worklog: TempoWorklog)

  @Serializable data class ListOpenIssuesInput(val project: String)

  @Serializable data class ListOpenIssuesOutput(val issues: List<JiraIssueShort>)
}

@Serializable
data class JiraIssueResponse(
  val startAt: Int,
  val maxResults: Int,
  val total: Int,
  val issues: List<JiraIssue>,
)

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

@Serializable
data class JiraIssue(
  val id: String,
  val self: String,
  val key: String,
  val fields: JiraIssueFields,
)

@Serializable
data class JiraIssueFields(
  val summary: String,
  val created: KOffsetDateTime,
  val updated: KOffsetDateTime,
  val description: String?,
  val worklog: JiraWorklog?,
  val assignee: JiraUser?,
  val priority: JiraIssuePriority,
)

@Serializable data class JiraIssuePriority(val name: String)

@Serializable
data class JiraWorklog(
  val startAt: Int,
  val maxResults: Int,
  val total: Int,
  val worklogs: List<JiraWorklogEntry>,
)

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

@Serializable
data class JiraWorklogEntry(
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
data class JiraUserSession(val self: String, val name: String, val loginInfo: JiraLoginInfo)

@Serializable
data class JiraLoginInfo(
  val failedLoginCount: Int,
  val loginCount: Int,
  val lastFailedLoginTime: KOffsetDateTime,
  val previousLoginTime: KOffsetDateTime,
)

@Serializable
data class TempoWorklog(
  val billableSeconds: Int,
  val timeSpent: String,
  val comment: String,
  val location: Location,
  val attributes: Map<String, WorkAttribute>,
  val timeSpentSeconds: Int,
  val issue: Issue,
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
  val estimatedRemainingSeconds: Int,
  val epicIssue: EpicIssue,
  val projectId: Int,
  val projectKey: String,
  val issueType: String,
  val versions: List<Int>,
  val summary: String,
  val reporterKey: String,
  val issueStatus: String,
  val components: List<String>,
  val epicKey: String,
  val internalIssue: Boolean,
)

@Serializable data class EpicIssue(val issueType: String, val summary: String)

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

@Serializable
data class TempoWorkAttributeType(val name: String, val value: String, val systemType: Boolean)

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
      4,
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
