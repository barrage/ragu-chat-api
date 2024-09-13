package net.barrage.llmao.llm.factories

import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.llm.Chat
import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.conversation.*
import net.barrage.llmao.llm.types.*
import net.barrage.llmao.models.Agent
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.AgentService
import net.barrage.llmao.services.ChatService
import net.barrage.llmao.websocket.Emitter

abstract class ChatFactory {
    private val agentService = AgentService()
    private val chatService = ChatService()

    fun new(
        model: LLMModels,
        config: ChatConfig,
        emitter: Emitter
    ): Chat {
        val agentId: Int = config.agentId
        val agent: Agent = agentService.get(agentId)

        val llmConfig = LLMConversationConfig(
            chat = LLMConfigChat(
                stream = true,
                temperature = 0.1,
            ),
            model = model,
            language = config.languages,
        )

        val llm = getConversationLlm(llmConfig)

        val infra = ChatInfra(
            llm,
            PromptFormatter(agent.context, config.languages),
            emitter,
        )

        return Chat(
            config = config,
            infra = infra,
        )
    }

    fun fromExisting(
        id: KUUID,
        userId: KUUID,
        emitter: Emitter
    ): Chat {
        val chat = chatService.get(id)
        val agent = agentService.get(chat.agentId)

        val llmConfig = chat.llmConfig.toLLMConversationConfig();

        val llm = getConversationLlm(llmConfig)

        val infra = ChatInfra(
            llm,
            PromptFormatter(agent.context, chat.llmConfig.language),
            emitter,
        )

        // TODO: Implement message history
        val history = chat.messages.map { message ->
            ChatMessage(
                message.senderType,
                message.content,
                message.responseTo,
            )
        }.toMutableList()

        return Chat(
            ChatConfig(
                chat.id,
                chat.userId,
                chat.agentId,
                chat.title,
                languages = chat.llmConfig.language,
                messageReceived = true,
            ),
            infra,
            history
        )
    }

    abstract fun getConversationLlm(config: LLMConversationConfig): ConversationLlm;
}

class AzureChatFactory(
    private val apiKey: String,
    private val endpoint: String,
    private val apiVersion: String
) : ChatFactory() {
    override fun getConversationLlm(config: LLMConversationConfig): ConversationLlm {
        return AzureAI(
            apiKey,
            endpoint,
            apiVersion,
            config,
        )
    }
}

class OpenAIChatFactory(
    private val apiKey: String
) : ChatFactory() {
    override fun getConversationLlm(config: LLMConversationConfig): ConversationLlm {
        return OpenAI(apiKey, config)
    }
}
