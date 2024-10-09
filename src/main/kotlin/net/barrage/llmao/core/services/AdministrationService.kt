package net.barrage.llmao.core.services

import net.barrage.llmao.app.ProviderState

class AdministrationService(private val providers: ProviderState) {
  suspend fun listLanguageModels(provider: String): List<String> {
    val llmProvider = providers.llm.getProvider(provider)
    return llmProvider.listModels()
  }

  fun listProviders(): Map<String, List<String>> {
    return providers.list()
  }
}
