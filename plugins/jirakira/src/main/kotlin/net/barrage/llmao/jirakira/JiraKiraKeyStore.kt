package net.barrage.llmao.jirakira

interface JiraKiraKeyStore {
  suspend fun setUserApiKey(userId: String, apiKey: String)

  suspend fun getUserApiKey(userId: String): String?

  suspend fun removeUserApiKey(userId: String)
}
