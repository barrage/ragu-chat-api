package net.barrage.llmao.repositories

import com.aallam.openai.api.core.FinishReason
import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.chats.toChatDTO
import net.barrage.llmao.dtos.llmconfigs.toLLMConfigDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.dtos.messages.MessageDTO
import net.barrage.llmao.dtos.messages.toMessageDTO
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.toChat
import net.barrage.llmao.models.toMessage
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.FailedMessagesRecord
import net.barrage.llmao.tables.records.LlmConfigsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.FAILED_MESSAGES
import net.barrage.llmao.tables.references.LLM_CONFIGS
import net.barrage.llmao.tables.references.MESSAGES
import java.time.OffsetDateTime

class ChatRepository {
    fun getAll(): List<ChatDTO> {
        return dslContext
            .select()
            .from(CHATS)
            .leftJoin(LLM_CONFIGS).on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
            .leftJoin(MESSAGES).on(CHATS.ID.eq(MESSAGES.CHAT_ID))
            .fetch()
            .groupBy { it[CHATS.ID] }
            .map { toChatDTO(it.value) }
    }

    fun get(id: KUUID): ChatDTO {
        return dslContext
            .select()
            .from(CHATS)
            .leftJoin(LLM_CONFIGS).on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
            .leftJoin(MESSAGES).on(CHATS.ID.eq(MESSAGES.CHAT_ID))
            .where(CHATS.ID.eq(id))
            .fetch()
            .groupBy { it[CHATS.ID] }
            .map { toChatDTO(it.value) }
            .first()
    }

    fun getAllForUser(id: KUUID): List<ChatDTO> {
        return dslContext
            .select()
            .from(CHATS)
            .leftJoin(LLM_CONFIGS).on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
            .leftJoin(MESSAGES).on(CHATS.ID.eq(MESSAGES.CHAT_ID))
            .where(CHATS.USER_ID.eq(id))
            .fetch()
            .groupBy { it[CHATS.ID] }
            .map { toChatDTO(it.value) }
    }

    fun getMessages(id: KUUID): List<Message> {
        return dslContext.select()
            .from(MESSAGES)
            .where(MESSAGES.CHAT_ID.eq(id))
            .fetchInto(MessagesRecord::class.java)
            .map { it.toMessage() }
    }

    fun getMessagesForUser(id: KUUID, userId: KUUID): List<Message> {
        return dslContext.select()
            .from(MESSAGES)
            .join(CHATS).on(MESSAGES.CHAT_ID.eq(CHATS.ID))
            .where(MESSAGES.CHAT_ID.eq(id).and(CHATS.USER_ID.eq(userId)))
            .fetchInto(MessagesRecord::class.java)
            .map { it.toMessage() }
    }

    fun getMessage(chatId: KUUID, messageId: KUUID): Message? {
        return dslContext
            .selectFrom(MESSAGES)
            .where(
                MESSAGES.ID.eq(messageId).and(
                    MESSAGES.CHAT_ID.eq(chatId)
                )
            )
            .fetchOne(MessagesRecord::toMessage)
    }

    fun getMessageForUser(chatId: KUUID, messageId: KUUID, userId: KUUID): Message? {
        return dslContext
            .select()
            .from(MESSAGES)
            .join(CHATS).on(MESSAGES.CHAT_ID.eq(CHATS.ID))
            .where(
                MESSAGES.ID.eq(messageId)
                    .and(MESSAGES.CHAT_ID.eq(chatId))
                    .and(CHATS.USER_ID.eq(userId))
            )
            .fetchOne { it.into(MessagesRecord::class.java).toMessage() }
    }

    fun evaluateMessage(id: KUUID, evaluation: EvaluateMessageDTO): Message? {
        return dslContext
            .update(MESSAGES)
            .set(MESSAGES.EVALUATION, evaluation.evaluation)
            .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(MESSAGES.ID.eq(id))
            .returning()
            .fetchOne(MessagesRecord::toMessage)
    }

    fun updateTitle(id: KUUID, updated: UpdateChatTitleDTO): Chat? {
        return dslContext
            .update(CHATS)
            .set(CHATS.TITLE, updated.title)
            .set(CHATS.UPDATED_AT, OffsetDateTime.now())
            .where(CHATS.ID.eq(id))
            .returning()
            .fetchOne(ChatsRecord::toChat)
    }

    fun updateTitleForUser(id: KUUID, updated: UpdateChatTitleDTO, userId: KUUID): Chat? {
        return dslContext
            .update(CHATS)
            .set(CHATS.TITLE, updated.title)
            .set(CHATS.UPDATED_AT, OffsetDateTime.now())
            .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
            .returning()
            .fetchOne(ChatsRecord::toChat)
    }

    fun insertWithConfig(chatsRecord: ChatsRecord, llmConfigsRecord: LlmConfigsRecord): ChatDTO {
        var insertedChat: ChatsRecord? = null
        var insertedLLMConfig: LlmConfigsRecord? = null

        dslContext.transaction { config ->
            insertedChat = config.dsl()
                .insertInto(CHATS)
                .set(chatsRecord)
                .returning()
                .fetchOne()!!

            llmConfigsRecord.apply {
                chatId = insertedChat!!.id!!
            }

            insertedLLMConfig = config.dsl()
                .insertInto(LLM_CONFIGS)
                .set(llmConfigsRecord)
                .returning()
                .fetchOne()!!

            return@transaction
        }

        if (insertedChat == null || insertedLLMConfig == null) {
            throw Exception("Failed to insert chat")
        } else return ChatDTO(
            id = insertedChat!!.id!!,
            userId = insertedChat!!.userId!!,
            agentId = insertedChat!!.agentId!!,
            title = insertedChat!!.title,
            createdAt = insertedChat!!.createdAt!!,
            updatedAt = insertedChat!!.updatedAt!!,
            llmConfig = insertedLLMConfig!!.toLLMConfigDTO(),
            messages = emptyList()
        )
    }

    fun insertFailedMessage(
        failReason: FinishReason,
        chatId: KUUID,
        userId: KUUID,
        returning: Boolean,
        content: String
    ): FailedMessagesRecord? {
        return dslContext.insertInto(FAILED_MESSAGES).set(FAILED_MESSAGES.FAIL_REASON, failReason.value)
            .set(FAILED_MESSAGES.CHAT_ID, chatId)
            .set(FAILED_MESSAGES.USER_ID, userId)
            .set(FAILED_MESSAGES.CONTENT, content)
            .let {
                if (returning) {
                    it.returning().fetchOne() ?: throw NotFoundException("Failed to insert failed message")
                } else {
                    it.execute().let {
                        null
                    }
                }
            }
    }

    fun insertUserMessage(id: KUUID, userId: KUUID, proompt: String): MessageDTO {
        return dslContext.insertInto(MESSAGES)
            .set(MESSAGES.CHAT_ID, id)
            .set(MESSAGES.SENDER, userId.toString())
            .set(MESSAGES.SENDER_TYPE, "user")
            .set(MESSAGES.CONTENT, proompt)
            .returning().fetchOne(MessagesRecord::toMessageDTO) ?: throw NotFoundException("Failed to insert user message")
    }

    fun insertAssistantMessage(id: KUUID, agentId: Int, response: String, messageId: KUUID): MessageDTO {
        return dslContext.insertInto(MESSAGES)
            .set(MESSAGES.CHAT_ID, id)
            .set(MESSAGES.SENDER, agentId.toString())
            .set(MESSAGES.SENDER_TYPE, "assistant")
            .set(MESSAGES.CONTENT, response)
            .set(MESSAGES.RESPONSE_TO, messageId)
            .returning().fetchOne(MessagesRecord::toMessageDTO) ?: throw NotFoundException("Failed to insert assistant message")
    }

    fun insertSystemMessage(id: KUUID, message: String): MessageDTO {
        return dslContext.insertInto(MESSAGES)
            .set(MESSAGES.CHAT_ID, id)
            .set(MESSAGES.SENDER_TYPE, "system")
            .set(MESSAGES.CONTENT, message)
            .returning().fetchOne(MessagesRecord::toMessageDTO) ?: throw NotFoundException("Failed to insert system message")
    }
}