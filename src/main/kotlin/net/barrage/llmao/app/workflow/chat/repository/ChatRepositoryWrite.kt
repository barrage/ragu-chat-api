package net.barrage.llmao.app.workflow.chat.repository

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.app.workflow.chat.CHAT_WORKFLOW_ID
import net.barrage.llmao.app.workflow.chat.model.Chat
import net.barrage.llmao.app.workflow.chat.model.toChat
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.repository.MessageRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.CHATS
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class ChatRepositoryWrite(override val dslContext: DSLContext, private val type: String) :
    MessageRepository {
    suspend fun updateTitle(id: KUUID, userId: String, title: String) {
        dslContext
            .update(CHATS)
            .set(CHATS.TITLE, title)
            .where(CHATS.TYPE.eq(type).and(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId))))
            .awaitFirstOrNull() ?: throw AppError.api(
            ErrorReason.EntityDoesNotExist,
            "Chat not found"
        )
    }

    suspend fun insertChatWithMessages(
        chatId: KUUID,
        userId: String,
        username: String?,
        agentId: KUUID,
        agentConfigurationId: KUUID,
        messages: List<MessageInsert>,
    ): KUUID =
        dslContext.transactionCoroutine { ctx ->
            ctx
                .dsl()
                .insertChat(
                    chatId = chatId,
                    userId = userId,
                    username = username,
                    agentId = agentId,
                    type = type,
                    agentConfigurationId = agentConfigurationId,
                )
            insertWorkflowMessages(chatId, CHAT_WORKFLOW_ID, messages, ctx.dsl())
        }

    suspend fun insertChat(
        chatId: KUUID,
        userId: String,
        username: String?,
        agentId: KUUID,
        agentConfigurationId: KUUID,
    ): Chat =
        dslContext.insertChat(
            chatId = chatId,
            userId = userId,
            username = username,
            agentId = agentId,
            type = type,
            agentConfigurationId = agentConfigurationId,
        )
}

private suspend fun DSLContext.insertChat(
    chatId: KUUID,
    userId: String,
    username: String?,
    agentId: KUUID,
    agentConfigurationId: KUUID,
    type: String,
): Chat {
    return insertInto(CHATS)
        .set(CHATS.ID, chatId)
        .set(CHATS.USER_ID, userId)
        .set(CHATS.USERNAME, username)
        .set(CHATS.AGENT_ID, agentId)
        .set(CHATS.AGENT_CONFIGURATION_ID, agentConfigurationId)
        .set(CHATS.TYPE, type)
        .returning()
        .awaitSingle()
        .into(CHATS)
        .toChat()
}
