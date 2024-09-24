package net.barrage.llmao.dtos.users

data class UserResponse(
    val users: List<UserDTO>,
    val count: Int
)
