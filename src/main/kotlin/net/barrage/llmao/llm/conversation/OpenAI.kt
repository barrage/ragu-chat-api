package net.barrage.llmao.llm.conversation

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import net.barrage.llmao.llm.ChatMessage
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage

class OpenAI(
    private val apiKey: String,
    private val cfg: LLMConversationConfig,
) : ConversationLlm {
    private var client: OpenAI? = null

    init {
        this.client = OpenAI(token = this.apiKey)
    }

    override suspend fun chatCompletion(messages: List<ChatMessage>): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.cfg.model.model),
            messages = messages.map { it.toOpenAiChatMessage() },
            temperature = this.cfg.temperature,
        )

        return this.client!!.chatCompletion(chatRequest).choices[0].message.content!!
    }

    override suspend fun completionStream(messages: List<ChatMessage>): Flow<List<TokenChunk>> {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.cfg.model.model),
            messages = messages.map { it.toOpenAiChatMessage() },
            temperature = this.cfg.temperature,
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
                        it.choices[0].finishReason?.value
                    )
                )
            }
    }

    override suspend fun generateChatTitle(proompt: String): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId("text-davinci-003"),
            messages = listOf(OpenAiChatMessage.User(proompt)),
            temperature = this.cfg.temperature,
        )

        val response = this.client!!.chatCompletion(chatRequest)
        return response.choices[0].message.content!!
    }

    override suspend fun summarizeConversation(proompt: String, maxTokens: Int): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId(this.cfg.model.model),
            messages = listOf(OpenAiChatMessage.User(proompt)),
            maxTokens = maxTokens,
            temperature = this.cfg.temperature,
        )

        return this.client!!.chatCompletion(chatRequest).choices[0].message.content!!
    }

    override fun config(): LLMConversationConfig {
        return this.cfg
    }
}