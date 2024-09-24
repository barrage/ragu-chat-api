package net.barrage.llmao.models

import net.barrage.llmao.dtos.users.UserDTO
import net.barrage.llmao.serializers.KUUID

object UserContext {
    var currentUser: UserDTO? = null
    var sessionId: KUUID? = null
}