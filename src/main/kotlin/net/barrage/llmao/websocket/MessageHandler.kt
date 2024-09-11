package net.barrage.llmao.websocket

import net.barrage.llmao.llm.Chat
import net.barrage.llmao.serializers.KUUID

val chats = mutableMapOf<KUUID, Chat>()

class MessageHandler(message: WSMessage) {
    // handle chat message
}