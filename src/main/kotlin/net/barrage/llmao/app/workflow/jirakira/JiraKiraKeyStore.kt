package net.barrage.llmao.app.workflow.jirakira

import net.barrage.llmao.core.types.KUUID

interface JiraKiraKeyStore {
  suspend fun setUserApiKey(userId: KUUID, apiKey: String)

  suspend fun getUserApiKey(userId: KUUID): String?

  suspend fun removeUserApiKey(userId: KUUID)
}
