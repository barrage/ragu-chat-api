import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.jirakira.tables.references.JIRA_API_KEYS
import net.barrage.llmao.jirakira.tables.references.JIRA_WORKLOG_ATTRIBUTES
import net.barrage.llmao.test.TestPostgres

suspend fun TestPostgres.testJiraApiKey(userId: String, apiKey: String) {
  dslContext
    .insertInto(JIRA_API_KEYS)
    .set(JIRA_API_KEYS.USER_ID, userId)
    .set(JIRA_API_KEYS.API_KEY, apiKey)
    .awaitSingle()
}

suspend fun TestPostgres.testJiraWorklogAttribute(
  id: String,
  description: String,
  required: Boolean,
) {
  dslContext
    .insertInto(JIRA_WORKLOG_ATTRIBUTES)
    .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
    .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
    .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
    .awaitSingle()
}
