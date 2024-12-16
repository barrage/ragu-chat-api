package net.barrage.llmao.core.auth

import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User

data class AuthenticationResult(val user: User, val session: Session, val userInfo: UserInfo)
