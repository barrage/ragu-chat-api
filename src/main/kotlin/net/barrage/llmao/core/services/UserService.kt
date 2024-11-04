package net.barrage.llmao.core.services

import io.ktor.server.plugins.*
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class UserService(
  private val usersRepository: UserRepository,
  private val sessionsRepository: SessionRepository,
) {
  fun getAll(pagination: PaginationSort): CountedList<User> {
    return usersRepository.getAll(pagination)
  }

  fun get(id: KUUID): User {
    return usersRepository.get(id) ?: throw NotFoundException("User not found")
  }

  fun getByEmail(email: String): User {
    return usersRepository.getByEmail(email) ?: throw NotFoundException("User not found")
  }

  fun create(user: CreateUser): User {
    val existingUser = usersRepository.getByEmail(user.email)

    if (existingUser != null) {
      throw AppError.api(ErrorReason.EntityAlreadyExists, "User with email '${user.email}'")
    }

    return usersRepository.create(user)
  }

  fun updateUser(id: KUUID, update: UpdateUser): User {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "User with id '$id'")
    }

    return usersRepository.updateNames(id, update)
  }

  // TODO: Separate this out into an administration service.
  fun updateAdmin(id: KUUID, update: UpdateUserAdmin, loggedInUserId: KUUID): User {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (id == loggedInUserId) {
      if (user.active != update.active) {
        throw AppError.api(ErrorReason.CannotUpdateSelf, "Cannot update Active on self")
      }
      if (user.role != update.role) {
        throw AppError.api(ErrorReason.CannotUpdateSelf, "Cannot update Role on self")
      }
    }

    // Invalidate sessions if user is being deactivated
    if (user.active != update.active) {
      val activeSessions = sessionsRepository.getActiveByUserId(user.id)
      activeSessions.forEach { session ->
        session?.let { sessionsRepository.expire(session.sessionId) }
      }
    }

    return usersRepository.updateFull(id, update)
  }

  fun delete(id: KUUID, loggedInUserId: KUUID) {
    if (id == loggedInUserId) {
      throw AppError.api(ErrorReason.CannotDeleteSelf, "Cannot delete self")
    }

    if (usersRepository.delete(id)) {
      val activeSessions = sessionsRepository.getActiveByUserId(id)
      activeSessions.forEach { session ->
        session?.let { sessionsRepository.expire(session.sessionId) }
      }
    } else {
      throw NotFoundException("User not found")
    }
  }
}
