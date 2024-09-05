package net.barrage.llmao.repositories

import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.chats.toChatDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.toChat
import net.barrage.llmao.models.toMessage
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.references.CHATS
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

    fun getMessages(id: KUUID): List<Message> {
        return dslContext.select()
            .from(MESSAGES)
            .where(MESSAGES.CHAT_ID.eq(id))
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
}