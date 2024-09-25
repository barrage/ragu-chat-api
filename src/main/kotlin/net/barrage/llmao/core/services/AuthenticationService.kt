package net.barrage.llmao.core.services

import java.util.*
import net.barrage.llmao.app.auth.AuthenticationProviderFactory
import net.barrage.llmao.dtos.auth.LoginPayload
import net.barrage.llmao.error.apiError
import net.barrage.llmao.models.SessionData
import net.barrage.llmao.repositories.SessionRepository
import net.barrage.llmao.repositories.UserRepository

class AuthenticationService(
  private val providers: AuthenticationProviderFactory,
  private val sessionRepo: SessionRepository,
  private val userRepo: UserRepository,
) {

  /**
   * Authenticate the user using the provider from the payload and establish a session upon success.
   */
  suspend fun authenticateUser(login: LoginPayload): SessionData {
    val provider = providers.getProvider(login.provider)
    val userInfo = provider.authenticate(login)
    val sessionId = UUID.randomUUID()

    val user =
      userRepo.getByEmail(userInfo.email)
        ?: throw apiError("Entity not found", "User '${userInfo.email}' does not exist")

    return sessionRepo.create(sessionId, user.id)
  }
}
