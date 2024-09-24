package net.barrage.llmao.repositories

import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.enums.Roles
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.USERS
import java.time.OffsetDateTime
import java.util.*

class UserRepository {
  fun getAll(offset: Int, size: Int, sortBy: String, sortOrder: String): List<UserDTO> {
    val sortField =
      when (sortBy) {
        "email" -> USERS.EMAIL
        "firstName" -> USERS.FIRST_NAME
        "lastName" -> USERS.LAST_NAME
        "role" -> USERS.ROLE
        "createdAt" -> USERS.CREATED_AT
        "updatedAt" -> USERS.UPDATED_AT
        else -> USERS.LAST_NAME
      }

    val orderField =
      if (sortOrder.equals("desc", ignoreCase = true)) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return dslContext
      .selectFrom(USERS)
      .orderBy(orderField)
      .limit(size)
      .offset(offset)
      .fetch(UsersRecord::toUser)
  }

  fun countAll(): Int {
    return dslContext.selectCount().from(USERS).fetchOne(0, Int::class.java)!!
  }

  fun get(id: UUID): UserDTO? {
    return dslContext.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOne(UsersRecord::toUser)
  }

  fun getByEmail(email: String): UserDTO? {
    return dslContext.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne(UsersRecord::toUser)
  }

  fun create(user: NewUserDTO): UserDTO {
    return dslContext
      .insertInto(USERS)
      .set(USERS.EMAIL, user.email)
      .set(USERS.FIRST_NAME, user.firstName)
      .set(USERS.LAST_NAME, user.lastName)
      .set(USERS.ROLE, user.role.name)
      .set(USERS.DEFAULT_AGENT_ID, user.defaultAgentId)
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun update(id: UUID, update: UpdateUser): UserDTO {
    val updateQuery =
      dslContext
        .update(USERS)
        .set(USERS.FIRST_NAME, update.firstName)
        .set(USERS.LAST_NAME, update.lastName)
        .set(USERS.DEFAULT_AGENT_ID, update.defaultAgentId)
        .set(USERS.UPDATED_AT, OffsetDateTime.now())

    if (update is AdminUpdateUserDTO) {
      updateQuery.set(USERS.EMAIL, update.email)
    }

    return updateQuery.where(USERS.ID.eq(id)).returning().fetchOne(UsersRecord::toUser)!!
  }

  fun updateRole(id: UUID, role: Roles): UserDTO {
    return dslContext
      .update(USERS)
      .set(USERS.ROLE, role.name)
      .set(USERS.UPDATED_AT, OffsetDateTime.now())
      .where(USERS.ID.eq(id))
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun activate(id: UUID): UserDTO {
    return dslContext
      .update(USERS)
      .set(USERS.ACTIVE, true)
      .set(USERS.UPDATED_AT, OffsetDateTime.now())
      .where(USERS.ID.eq(id))
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun deactivate(id: UUID): UserDTO {
    return dslContext
      .update(USERS)
      .set(USERS.ACTIVE, false)
      .set(USERS.UPDATED_AT, OffsetDateTime.now())
      .where(USERS.ID.eq(id))
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun delete(id: UUID): Boolean {
    return dslContext.deleteFrom(USERS).where(USERS.ID.eq(id)).execute() == 1
  }

  fun setDefaultAgent(id: Int, userId: UUID): UserDTO {
    return dslContext
      .update(USERS)
      .set(USERS.DEFAULT_AGENT_ID, id)
      .set(USERS.UPDATED_AT, OffsetDateTime.now())
      .where(USERS.ID.eq(userId))
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }
}
