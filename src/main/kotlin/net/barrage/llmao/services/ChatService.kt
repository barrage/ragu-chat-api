package net.barrage.llmao.services

import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.repositories.ChatRepository
import net.barrage.llmao.serializers.KUUID

class ChatService {
    private val chatRepository = ChatRepository()

    fun getAll(userId: KUUID? = null): List<ChatDTO> {
        return if (userId != null) {
            chatRepository.getAllForUser(userId)
        } else {
            chatRepository.getAll()
        }
    }

    fun getMessages(id: KUUID, userId: KUUID? = null): List<Message> {
        return if (userId != null) {
            chatRepository.getMessagesForUser(id, userId)
        } else {
            chatRepository.getMessages(id)
        }
    }

    fun updateTitle(id: KUUID, updated: UpdateChatTitleDTO, userId: KUUID? = null): Chat {
        return if (userId != null) {
            chatRepository.updateTitleForUser(id, updated, userId) ?: throw NotFoundException("Chat not found")
        } else {
            chatRepository.updateTitle(id, updated) ?: throw NotFoundException("Chat not found")
        }
    }

    fun evaluateMessage(
        chatId: KUUID,
        messageId: KUUID,
        evaluation: EvaluateMessageDTO,
        userId: KUUID? = null
    ): Message {
        if (userId != null) {
            chatRepository.getMessageForUser(chatId, messageId, userId)
                ?: throw NotFoundException("Message not found")
        } else {
            chatRepository.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
        }

        return chatRepository.evaluateMessage(messageId, evaluation) ?: throw NotFoundException("Message not found")
    }
}