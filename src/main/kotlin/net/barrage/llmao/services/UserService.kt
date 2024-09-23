package net.barrage.llmao.services

import io.ktor.server.plugins.*
import java.util.*
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.repositories.UserRepository
import net.barrage.llmao.serializers.KUUID

class UserService {
  private val usersRepository = UserRepository()

  fun getAll(page: Int, size: Int, sortBy: String, sortOrder: String): UserResponse {
    val offset = (page - 1) * size

    val users = usersRepository.getAll(offset, size, sortBy, sortOrder)
    val count = usersRepository.countAll()

    return UserResponse(users, count)
  }

  fun get(id: KUUID): UserDto {
    return usersRepository.get(id) ?: throw NotFoundException("User not found")
  }

  fun getByEmail(email: String): UserDto {
    return usersRepository.getByEmail(email) ?: throw NotFoundException("User not found")
  }

  fun create(user: NewUserDTO): UserDto {
    val existingUser = usersRepository.getByEmail(user.email)

    if (existingUser != null) {
      throw BadRequestException("User with email ${user.email} already exists")
    }

    return usersRepository.create(user)
  }

  fun createDev(user: NewDevUserDTO): UserDto {
    val existingUser = usersRepository.getByEmail(user.email)

    if (existingUser != null) {
      throw BadRequestException("User with email ${user.email} already exists")
    }

    val newUser =
      NewUserDTO(
        email = user.email,
        firstName = user.firstName,
        lastName = user.lastName,
        role = user.role,
        defaultAgentId = 1,
      )
    return usersRepository.create(newUser)
  }

  fun update(id: KUUID, update: UpdateUser): UserDto {
    val user: UserDto = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw BadRequestException("User is deactivated")
    }

    return usersRepository.update(id, update)
  }

  fun updateRole(id: UUID, update: UpdateUserRoleDTO): UserDto {
    val user: UserDto = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw BadRequestException("User is deactivated")
    }

    return usersRepository.updateRole(id, update.role)
  }

  fun activate(id: UUID): UserDto {
    val user: UserDto = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (user.active) {
      throw BadRequestException("User is already active")
    }
    return usersRepository.activate(id)
  }

  fun deactivate(id: UUID): UserDto {
    val user: UserDto = usersRepository.get(id) ?: throw NotFoundException("User not found")

    if (!user.active) {
      throw BadRequestException("User is already deactivated")
    }
    return usersRepository.deactivate(id)
  }

  fun delete(id: UUID) {
    if (!usersRepository.delete(id)) {
      throw NotFoundException("User not found")
    }
  }
}
