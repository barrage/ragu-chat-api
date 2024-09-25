package net.barrage.llmao.services

import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.error.apiError
import net.barrage.llmao.models.User
import net.barrage.llmao.repositories.UserRepository
import net.barrage.llmao.serializers.KUUID
import java.util.*

class UserService {
  private val usersRepository = UserRepository()

  fun getAll(page: Int, size: Int, sortBy: String, sortOrder: String): UserResponse {
    val offset = (page - 1) * size

    val users = usersRepository.getAll(offset, size, sortBy, sortOrder)
    val count = usersRepository.countAll()

    return UserResponse(users, count)
  }

  fun get(id: KUUID): UserDTO {
    return usersRepository.get(id) ?: throw NotFoundException("User not found")
  }

  fun getByEmail(email: String): UserDTO {
    return usersRepository.getByEmail(email) ?: throw NotFoundException("User not found")
  }

  fun create(user: NewUserDTO): UserDTO {
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
