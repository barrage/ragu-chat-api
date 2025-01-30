package net.barrage.llmao.core.services

import io.ktor.util.*
import io.ktor.utils.io.*
import java.util.*
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.CsvImportUsersResult
import net.barrage.llmao.core.models.CsvImportedUser
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SearchFiltersAdminUsers
import net.barrage.llmao.core.repository.SessionRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.utility.parseUsers
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class UserService(
  private val usersRepository: UserRepository,
  private val sessionsRepository: SessionRepository,
  private val avatarStorage: ImageStorage,
) {
  suspend fun getAll(
    pagination: PaginationSort,
    filters: SearchFiltersAdminUsers,
  ): CountedList<User> {
    return usersRepository.getAll(pagination, filters)
  }

  suspend fun get(id: KUUID): User {
    return usersRepository.get(id)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")
  }

  suspend fun create(user: CreateUser): User {
    val existingUser = usersRepository.getByEmail(user.email)

    if (existingUser != null) {
      throw AppError.api(ErrorReason.EntityAlreadyExists, "User with email '${user.email}'")
    }

    return usersRepository.create(user)
  }

  suspend fun updateUser(id: KUUID, update: UpdateUser): User {
    val user: User =
      usersRepository.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")

    if (!user.active) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "User with id '$id'")
    }

    return usersRepository.updateNames(id, update)
  }

  // TODO: Separate this out into an administration service.
  suspend fun updateAdmin(id: KUUID, update: UpdateUserAdmin, loggedInUserId: KUUID): User {
    val user: User =
      usersRepository.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")

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

  suspend fun delete(id: KUUID, loggedInUserId: KUUID) {
    if (id == loggedInUserId) {
      throw AppError.api(ErrorReason.CannotDeleteSelf, "Cannot delete self")
    }

    val user =
      usersRepository.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")

    if (usersRepository.delete(id)) {
      val activeSessions = sessionsRepository.getActiveByUserId(id)
      activeSessions.forEach { session ->
        session?.let { sessionsRepository.expire(session.sessionId) }
      }

      if (user.avatar != null) {
        avatarStorage.delete(user.avatar)
      }
    } else {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")
    }
  }

  suspend fun importUsersCsv(csv: ByteReadChannel): CsvImportUsersResult {
    val (users, failed) = parseUsers(csv)

    val inserted = usersRepository.insertUsers(users)

    val successful =
      users.map { user ->
        CsvImportedUser(
          email = user.email,
          fullName = user.fullName,
          role = user.role,
          skipped = inserted.contains(user.email),
        )
      }

    return CsvImportUsersResult(successful, failed)
  }

  suspend fun setUserAvatar(id: KUUID, fileExtension: String, avatar: ByteReadChannel): User {
    val user =
      usersRepository.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")

    val imageName = UUID.randomUUID().toString() + "." + fileExtension

    avatarStorage.store(imageName, avatar.toByteArray())

    if (user.avatar != null) {
      avatarStorage.delete(user.avatar)
    }

    return usersRepository.updateAvatar(id, imageName)
  }

  suspend fun deleteUserAvatar(id: KUUID) {
    val user =
      usersRepository.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "User not found")

    if (user.avatar == null) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "User avatar not found")
    }

    avatarStorage.delete(user.avatar)
    usersRepository.updateAvatar(id, null)
  }
}
