package net.barrage.llmao.services

import io.ktor.server.plugins.*
import java.util.*
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.error.apiError
import net.barrage.llmao.models.CountedList
import net.barrage.llmao.models.CreateUser
import net.barrage.llmao.models.PaginationSort
import net.barrage.llmao.models.User
import net.barrage.llmao.serializers.KUUID

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
      throw apiError("Entity already exists", "User with email '${user.email}'")
    }

    return usersRepository.create(user)
  }

  fun updateUser(id: KUUID, update: UpdateUser): User {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw apiError("Entity not found", "User with id '$id'")
    }

    return usersRepository.updateNames(id, update)
  }

  // TODO: Separate this out into an administration service.
  fun updateAdmin(id: KUUID, update: UpdateUserAdmin): User {
    val user: User = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw apiError("Entity not found", "User with id '$id'")
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
