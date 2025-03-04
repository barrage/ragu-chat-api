package net.barrage.llmao.core.services

import java.util.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.auth.AuthenticationResult
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AuthenticationService(
  private val providers: ProviderFactory<AuthenticationProvider>,
  private val sessionRepo: SessionRepository,
  private val userRepo: UserRepository,
) {

  suspend fun getUserForSession(sessionId: KUUID): User? {
    val serverSession = sessionRepo.get(sessionId) ?: return null
    return userRepo.get(serverSession.userId)
  }

  /**
   * Authenticate the user using the provider from the payload and establish a session upon success.
   */
  suspend fun authenticateUser(login: LoginPayload): AuthenticationResult {
    val provider = providers.getProvider(login.provider)

    val userInfo = provider.authenticate(login)

    val sessionId = UUID.randomUUID()

    val user =
      userRepo.getByEmail(userInfo.email)
        ?: throw AppError.api(
          ErrorReason.EntityDoesNotExist,
          "User '${userInfo.email}' does not exist",
        )

    if (!user.active || user.deletedAt != null) {
      throw AppError.api(
        ErrorReason.EntityDoesNotExist,
        "User '${userInfo.email}' is not active or deleted",
      )
    }

    val session = sessionRepo.create(sessionId, user.id)

    return AuthenticationResult(user, session, userInfo)
  }

  /**
   * Validate a session with the given ID exists and its active user is an administrator. Returns
   * `null` if:
   * - The session does not exist or is expired
   * - The user cannot be found or their active flag is `false`
   * - The user's role is not ADMIN
   */
  suspend fun validateAdminSession(sessionId: KUUID): Pair<Session, User>? {
    val serverSession = sessionRepo.get(sessionId)

    if (serverSession == null || !serverSession.isValid()) {
      return null
    }

    val user = userRepo.get(serverSession.userId) ?: return null

    if (!user.active || user.role != Role.ADMIN) {
      return null
    }

    return Pair(serverSession, user)
  }

  /**
   * Validate a session with the given ID exists and its active user is an administrator. Returns
   * `null` if:
   * - The session does not exist or is expired
   * - The user cannot be found or their active flag is `false`
   */
  suspend fun validateUserSession(sessionId: KUUID): Pair<Session, User>? {
    val serverSession = sessionRepo.get(sessionId)

    if (serverSession == null || !serverSession.isValid()) {
      return null
    }

    val user = userRepo.get(serverSession.userId) ?: return null

    if (!user.active) {
      return null
    }

    return Pair(serverSession, user)
  }

  /** Utility for development routes */
  suspend fun store(sessionId: KUUID, userId: KUUID) {
    try {
      sessionRepo.create(sessionId, userId)
    } catch (e: Exception) {
      when {
        e.message?.contains("violates foreign key constraint") == true -> {
          throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")
        }
        else -> throw AppError.internal(e.message ?: "An unexpected error occurred")
      }
    }
  }

  suspend fun get(sessionId: KUUID): Session? {
    return sessionRepo.get(sessionId)
  }

  suspend fun extend(sessionId: KUUID) {
    val serverSession = sessionRepo.get(sessionId) ?: return

    if (!serverSession.isValid()) {
      return
    }

    sessionRepo.extend(sessionId)
  }

  suspend fun logout(sessionId: KUUID) {
    sessionRepo.get(sessionId) ?: return
    sessionRepo.expire(sessionId)
  }
}
