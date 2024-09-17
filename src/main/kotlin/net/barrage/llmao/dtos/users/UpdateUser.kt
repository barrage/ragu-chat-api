package net.barrage.llmao.dtos.users

interface UpdateUser {
    val firstName: String
    val lastName: String
    val defaultAgentId: Int
}