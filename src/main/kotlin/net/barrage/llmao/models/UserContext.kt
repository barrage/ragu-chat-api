package net.barrage.llmao.models

import net.barrage.llmao.dtos.users.UserDto
import net.barrage.llmao.serializers.KUUID

object UserContext {
    var currentUser: UserDto? = null
    var sessionId: KUUID? = null
}