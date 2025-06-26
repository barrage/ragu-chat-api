import kotlinx.serialization.Serializable
import net.barrage.llmao.jirakira.tables.records.JiraWorklogAttributesRecord

/**
 * Descriptions of custom Jira worklog attributes. If an attribute is present in the database, it
 * will be included in tool definitions for creating worklog entries. Only static list type JIRA
 * attributes are supported. The enumeration of the values they can take is obtained from the Jira
 * API when initializing JiraKira.
 */
@Serializable
data class WorklogAttribute(
  /**
   * The Jira custom worklog attribute key. Serves as the property key in JSON schema property
   * definitions.
   */
  val key: String,

  /**
   * The Jira custom worklog attribute description. Serves as the property description in JSON
   * schema property definitions.
   */
  val description: String,

  /** Whether or not the attribute will be marked as required in the JSON schema. */
  val required: Boolean,
)

fun JiraWorklogAttributesRecord.toWorklogAttributeModel() =
  WorklogAttribute(key = id, description = description, required = required)
