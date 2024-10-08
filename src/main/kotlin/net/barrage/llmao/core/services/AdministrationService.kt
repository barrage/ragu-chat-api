package net.barrage.llmao.core.services

import net.barrage.llmao.ProviderState

class AdministrationService(private val providers: ProviderState) {
  suspend fun listLanguageModels(provider: String): List<String> {
    val llmProvider = providers.llm.getProvider(provider)
    return llmProvider.listModels()
  }
}
