package net.barrage.llmao.dtos.users

data class UserResponse(
    val users: List<UserDto>,
    val count: Int
)
