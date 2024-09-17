package net.barrage.llmao.llm

import com.aallam.openai.api.core.FinishReason
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.ModelType
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.llm.types.*
import net.barrage.llmao.services.ChatService
import net.barrage.llmao.weaviate.loadWeaviate
import net.barrage.llmao.websocket.ChatTitleUpdated
import net.barrage.llmao.websocket.FinishEvent
import net.barrage.llmao.websocket.S2CTitleUpdatedMessage

class Chat(
    val config: ChatConfig,
    val infra: ChatInfra,
    private var history: MutableList<ChatMessage> = mutableListOf(),
) {
    private val chatService = ChatService()
    var streamActive: Boolean = false

    private fun persist() {
        chatService.insertWithConfig(config, infra.llm.config())
    }

    private suspend fun generateTitle(proompt: String) {
        val titlePrompt = infra.formatter.title(proompt)
        val title = infra.llm.generateChatTitle(titlePrompt).trim()

        chatService.updateTitle(this.config.id!!, UpdateChatTitleDTO(title))

        this.infra.emitter?.emitSystemMessage(S2CTitleUpdatedMessage(ChatTitleUpdated(this.config.id, title)))
    }

    suspend fun updateTitle(title: String) {
        this.config.title = title
        chatService.updateTitle(this.config.id!!, UpdateChatTitleDTO(title))

        this.infra.emitter?.emitSystemMessage(S2CTitleUpdatedMessage(ChatTitleUpdated(this.config.id, title)))
    }

    suspend fun stream(proompt: String) {
        this.streamActive = true

        val stream: Flow<List<TokenChunk>>?
        if (this.config.messageReceived != true) {
            this.persist()
            this.config.messageReceived = true
        }

        val query = this.prepareProompt(proompt)

        val buf: MutableList<String> = mutableListOf()

        try {
            stream = infra.llm.completionStream(query)
        } catch (
            e: Exception
        ) {
            if (e.message == "Content filter triggered") {
                val response = contentFilterErrorMessage()
                val failedMessage = this.chatService.insertFailedMessage(FinishReason.ContentFilter, this.config.id!!, this.config.userId, true, proompt)
                this.infra.emitter!!.emitFinishResponse(FinishEvent(this.config.id, failedMessage!!.id, response, FinishReason.ContentFilter))
            }
            else {
                e.printStackTrace()
            }
            this.streamActive = false
            return
        }

        stream.collect { tokens ->
            if (!this.streamActive) {
                println("Canceling stream for ${this.config.id}")

                if (buf.isNotEmpty()) {
                    val response = buf.joinToString("")
                    this.processResponse(proompt, response)
                }
            }

            for (chunk in tokens) {
                if (chunk.content.isNullOrBlank() && chunk.stopReason != FinishReason.Stop) {
                    continue
                }

                if (!chunk.content.isNullOrBlank()) {
                    this.infra.emitter!!.emitChunk(chunk)
                    buf.add(chunk.content)
                }

                if (chunk.stopReason == FinishReason.Stop) {
                    break
                }
            }
        }.let {
            val response = buf.joinToString("")
            this.streamActive = false

            println("Chat ${this.config.id} got response: $response")

            this.processResponse(proompt, response)
        }
    }


    private fun contentFilterErrorMessage(): String {
        val language = this.infra.llm.config().language.language

        return if (language == "cro") "Ispričavam se, ali trenutno ne mogu pružiti odgovor. Molim vas da preformulirate svoj zahtjev ili postavite drugo pitanje."
        else "I apologize, but I'm unable to provide a response at this time. Please try rephrasing your request or ask something else."
    }

    suspend fun respond(proompt: String): String {
        if (this.config.messageReceived != true) {
            this.persist()
            this.config.messageReceived = true
        }

        if (this.config.title.isNullOrBlank()) {
            this.generateTitle(proompt)
        }

        val query = this.prepareProompt(proompt)
        val response = infra.llm.chatCompletion(query).trim()

        return this.processResponse(proompt, response).content
    }

    fun closeStream() {
        this.streamActive = false
    }

    private suspend fun processResponse(proompt: String, response: String): ChatMessage {
        val messages: List<ChatMessage> = listOf(
            userChatMessage(proompt),
            assistantChatMessage(response)
        )

        this.addToHistory(messages)

        val userMessage = chatService.insertUserMessage(this.config.id!!, this.config.userId, proompt)
        val assistantMessage =
            chatService.insertAssistantMessage(this.config.id, this.config.agentId, response, userMessage.id)

        if (this.infra.emitter != null) {
            val emitPayload = FinishEvent(this.config.id, assistantMessage.id, null, FinishReason.Stop)

            if (!this.infra.llm.config().chat.stream) {
                emitPayload.content = assistantMessage.content
            }

            this.infra.emitter.emitFinishResponse(emitPayload)
        }

        return assistantChatMessage(response)
    }

    private fun prepareProompt(proompt: String): List<ChatMessage> {
        val system = this.infra.formatter.systemMessage()
        val history = this.history
        val query = this.formatProompt(proompt)
        val messages = mutableListOf(system, *history.toTypedArray(), query)
        return messages
    }

    private fun formatProompt(proompt: String): ChatMessage {
        val context = "No given context"

        return this.infra.formatter.userMessage(proompt, context)
    }

    private fun countHistoryTokens(): Int {
        val registry = Encodings.newDefaultEncodingRegistry()
        val enc: Encoding = when (this.infra.llm.config().model) {
            LLMModels.GPT4 -> registry.getEncodingForModel(ModelType.GPT_4)
            LLMModels.GPT35TURBO -> registry.getEncodingForModel(ModelType.GPT_3_5_TURBO)
        }
        val history = this.history.joinToString("") { it.content }
        return enc.encode(history).size()
    }

    private suspend fun addToHistory(messages: List<ChatMessage>) {
        this.history.addAll(messages)

        if ((this.config.summarizeAfterTokens != null && this.countHistoryTokens() > this.config.summarizeAfterTokens)
            || (this.config.maxHistory != null && this.history.size > this.config.maxHistory)
        ) {
            val conversation = this.history.joinToString("\n") {
                val s = when (it.role) {
                    "user" -> "User"
                    "assistant" -> "Assistant"
                    "system" -> "System"
                    else -> ""
                }
                return@joinToString "$s: ${it.content}"
            }

            val summaryPrompt = this.infra.formatter.summary(conversation)

            val summary = this.infra.llm.summarizeConversation(summaryPrompt)

            try {
                chatService.insertSystemMessage(this.config.id!!, summary.trim())
            } catch (e: Exception) {
                println("Error while inserting system message: ${e.message}")
            }

            this.history = mutableListOf(systemChatMessage(summary.trim()))
        } else if (this.config.maxHistory != null) {
            this.history.removeFirst()
        }
    }
}
