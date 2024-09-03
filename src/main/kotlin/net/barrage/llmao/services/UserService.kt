package net.barrage.llmao.services

import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.repositories.UserRepository
import java.util.*

class UserService {
    private val usersRepository = UserRepository()

    fun getAll(): List<UserDto> {
        return usersRepository.getAll()
    }

    fun get(id: UUID): UserDto {
        return usersRepository.get(id) ?: throw NotFoundException("User not found")
    }

    fun create(user: NewUserDTO): UserDto {
        return usersRepository.create(user)
    }

    fun update(id: UUID, update: UpdateUserDTO): UserDto {
        val user: UserDto = usersRepository.get(id) ?: throw NotFoundException("User not found")

        if (!user.active) {
            throw BadRequestException("User is deactivated")
        }

        return usersRepository.update(id, update)
    }

    fun updatePassword(id: UUID, update: UpdateUserPasswordDTO): UserDto {
        val user: UserDto =
            usersRepository.getWithIdAndPassword(id, update.password) ?: throw NotFoundException("User not found")

        if (!user.active) {
            throw BadRequestException("User is deactivated")
        }

        return usersRepository.updatePassword(id, update.newPassword)
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
        if (usersRepository.delete(id)) {
            throw NotFoundException("User not found")
        }
    }
}