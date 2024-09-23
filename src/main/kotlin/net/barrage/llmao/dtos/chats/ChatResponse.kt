package net.barrage.llmao.dtos.chats

data class ChatResponse(
    val chats: List<ChatDTO>,
    val count: Int
)
