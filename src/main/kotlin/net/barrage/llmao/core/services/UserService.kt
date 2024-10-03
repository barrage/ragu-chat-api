package net.barrage.llmao.core.services

import io.ktor.server.plugins.*
import java.util.*
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class UserService(private val usersRepository: UserRepository) {
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
  fun updateAdmin(id: KUUID, update: UpdateUserAdmin): User {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "User with id '$id'")
    }

    return usersRepository.updateFull(id, update)
  }

  fun setActiveStatus(id: UUID, active: Boolean): Int {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (user.active == active) {
      throw BadRequestException("User is already active")
    }

    return usersRepository.setActiveStatus(id, active)
  }

  fun delete(id: UUID) {
    if (!usersRepository.delete(id)) {
      throw NotFoundException("User not found")
    }
  }
}
