package net.barrage.llmao.llm.factories

import net.barrage.llmao.app.LlmProviderFactory
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.error.apiError
import net.barrage.llmao.llm.Chat
import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.LlmConfig
import net.barrage.llmao.models.Language
import net.barrage.llmao.models.VectorQueryOptions
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.AgentService

class ChatFactory(
  private val providers: LlmProviderFactory,
  private val agentService: AgentService,
  private val chatService: ChatService,
) {

  fun new(provider: String, userId: KUUID, agentId: Int, model: String, language: Language): Chat {
    val agent = agentService.get(agentId)

    val llm = providers.getProvider(provider)

    if (!llm.supportsModel(model)) {
      throw apiError("Invalid Model", "Provider '$provider' does not support model '$model'")
    }

    val config = LlmConfig(model, 0.1, language, provider)

    val id = KUUID.randomUUID()

    return Chat(
      chatService,
      id = id,
      userId = userId,
      agentId = agentId,
      llm = llm,
      llmConfig = config,
      formatter = PromptFormatter(agent.context, language),
      vectorOptions = VectorQueryOptions(listOf()),
    )
  }

  fun fromExisting(id: KUUID): Chat {
    val chat = chatService.getChat(id)

    val history = chatService.getMessages(id).map(ChatMessage::fromModel).toMutableList()
    val agent = agentService.get(chat.chat.agentId)
    val llm = providers.getProvider(chat.config.provider)

    val config =
      LlmConfig(
        chat.config.model,
        chat.config.temperature,
        chat.config.language,
        chat.config.provider,
      )

    // TODO: Implement message history
    val vectorOptions = VectorQueryOptions(listOf())

    return Chat(
      chatService,
      id = chat.chat.id,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      title = chat.chat.title,
      messageReceived = history.size > 0,
      llmConfig = config,
      history = history,
      llm = llm,
      vectorOptions = vectorOptions,
      formatter = PromptFormatter(agent.context, chat.config.language),
    )
  }
}
