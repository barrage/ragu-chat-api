package net.barrage.llmao.llm.factories

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import io.ktor.server.config.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.llm.Chat
import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.conversation.AzureAI
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.llm.conversation.OpenAI
import net.barrage.llmao.llm.types.*
import net.barrage.llmao.models.Agent
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.AgentService
import net.barrage.llmao.services.ChatService
import net.barrage.llmao.weaviate.Weaver
import net.barrage.llmao.weaviate.collections.Documentation
import net.barrage.llmao.websocket.Emitter

abstract class ChatFactory(
    open val vectorDb: Weaver
) {
    private val agentService = AgentService()
    private val chatService = ChatService()

    private val vectorOptions = Documentation.vectorQueryOptions

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
            language = config.language,
        )

        println(model)

        val llm = getConversationLlm(llmConfig)

        val infra = ChatInfra(
            llm,
            PromptFormatter(agent.context, config.language),
            emitter,
            vectorDb,
            vectorOptions,
            getEncoder(model),
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
        val chat: ChatDTO = chatService.getUserChat(id, userId)

        val agent = agentService.get(chat.agentId)

        val llmConfig = chat.llmConfig.toLLMConversationConfig()

        val llm = getConversationLlm(llmConfig)

        val infra = ChatInfra(
            llm,
            PromptFormatter(agent.context, chat.llmConfig.language),
            emitter,
            vectorDb,
            vectorOptions,
            getEncoder(llmConfig.model),
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
                language = chat.llmConfig.language,
                messageReceived = true,
            ),
            infra,
            history
        )
    }

    abstract fun getConversationLlm(config: LLMConversationConfig): ConversationLlm
}

class AzureChatFactory(
    private val apiKey: String,
    private val endpoint: String,
    private val apiVersion: String,
    override val vectorDb: Weaver,
) : ChatFactory(vectorDb) {
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
    private val apiKey: String,
    override val vectorDb: Weaver
) : ChatFactory(vectorDb) {
    override fun getConversationLlm(config: LLMConversationConfig): ConversationLlm {
        return OpenAI(apiKey, config)
    }
}

fun chatFactory(config: ApplicationConfig, vectorDb: Weaver): ChatFactory {
    return if (config.property("llm.provider").getString() == "azure") {
        AzureChatFactory(
            config.property("llm.azure.LLMKey").getString(),
            config.property("llm.azure.LLMEndpoint").getString(),
            config.property("llm.azure.apiVersion").getString(),
            vectorDb
        )
    } else {
        OpenAIChatFactory(
            config.property("llm.openai.authorization").getString(),
            vectorDb
        )
    }
}

fun getEncoder(llmModel: LLMModels): Encoding {
    val registry = Encodings.newDefaultEncodingRegistry()
    return when (llmModel) {
        LLMModels.GPT4 -> registry.getEncodingForModel(ModelType.GPT_4)
        LLMModels.GPT35TURBO -> registry.getEncodingForModel(ModelType.GPT_3_5_TURBO)
    }
}
