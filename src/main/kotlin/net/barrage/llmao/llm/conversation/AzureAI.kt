package net.barrage.llmao.llm.conversation

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.LLMConversationConfig
import net.barrage.llmao.llm.types.TokenChunk
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage

class AzureAI(
    private val apiKey: String,
    private val endpoint: String,
    private val apiVersion: String,
    private val cfg: LLMConversationConfig,
) : ConversationLlm {
    private var client: OpenAI? = null
    private var deployment: LLMModels? = null

    fun init() {
        this.deployment = this.cfg.model
        if (this.deployment == null) {
            this.deployment = LLMModels.GPT4
        }

        this.client = OpenAI(
            OpenAIConfig(
                host = OpenAIHost.azure(
                    resourceName = this.endpoint,
                    deploymentId = this.deployment!!.azureModel,
                    apiVersion = this.apiVersion
                ),
                headers = mapOf("api-key" to this.apiKey),
                token = this.apiKey
            )
        )
    }

    override suspend fun chatCompletion(messages: List<ChatMessage>): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.deployment!!.azureModel),
            messages = messages.map { it.toOpenAiChatMessage() },
            temperature = this.cfg.chat.temperature,
        )

        return this.client!!.chatCompletion(chatRequest).choices[0].message.content!!
    }

    override suspend fun completionStream(messages: List<ChatMessage>): Flow<List<TokenChunk>> {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.deployment!!.azureModel),
            messages = messages.map { it.toOpenAiChatMessage() },
            temperature = this.cfg.chat.temperature,
            streamOptions = StreamOptions(true),
        )

        return this.client!!.chatCompletions(chatRequest)
            .filter {
                it.choices.isNotEmpty()
            }
            .map {
                listOf(
                    TokenChunk(
                        it.id,
                        it.created,
                        it.choices[0].delta?.content,
                        it.choices[0].finishReason
                    )
                )
            }
    }

    override suspend fun generateChatTitle(proompt: String): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.deployment!!.azureModel),
            messages = listOf(OpenAIChatMessage.User(proompt)),
            temperature = this.cfg.chat.temperature,
        )

        val response = this.client!!.chatCompletion(chatRequest)
        return response.choices[0].message.content!!
    }

    override suspend fun summarizeConversation(proompt: String, maxTokens: Int?): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.deployment!!.azureModel),
            messages = listOf(OpenAIChatMessage.User(proompt)),
            maxTokens = maxTokens,
            temperature = this.cfg.chat.temperature,
        )

        return this.client!!.chatCompletion(chatRequest).choices[0].message.content!!
    }

    override fun config(): LLMConversationConfig {
        return this.cfg
    }
}
