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

    fun getAll(): List<ChatDTO> {
        return chatRepository.getAll()
    }

    fun getMessages(id: KUUID): List<Message> {
        return chatRepository.getMessages(id)
    }

    fun updateTitle(id: KUUID, updated: UpdateChatTitleDTO): Chat {
        return chatRepository.updateTitle(id, updated) ?: throw NotFoundException("Chat not found")
    }

    fun evaluateMessage(chatId: KUUID, messageId: KUUID, evaluation: EvaluateMessageDTO): Message {
        chatRepository.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")

        return chatRepository.evaluateMessage(messageId, evaluation) ?: throw NotFoundException("Message not found")
    }
}