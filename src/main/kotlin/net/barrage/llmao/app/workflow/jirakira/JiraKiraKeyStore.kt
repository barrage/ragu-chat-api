package net.barrage.llmao.app.workflow.jirakira

interface JiraKiraKeyStore {
  suspend fun setUserApiKey(userId: String, apiKey: String)

  suspend fun getUserApiKey(userId: String): String?

  suspend fun removeUserApiKey(userId: String)
}
