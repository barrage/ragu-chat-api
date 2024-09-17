package net.barrage.llmao.repositories

import net.barrage.llmao.dtos.users.*
import net.barrage.llmao.enums.Roles
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.USERS
import java.time.OffsetDateTime
import java.util.*

class UserRepository {
    fun getAll(): List<UserDto> {
        return dslContext.selectFrom(USERS)
            .fetch(UsersRecord::toUserDto)
    }

    fun get(id: UUID): UserDto? {
        return dslContext.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne(UsersRecord::toUserDto)
    }

    fun getByEmail(email: String): UserDto? {
        return dslContext.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne(UsersRecord::toUserDto)
    }

    fun create(user: NewUserDTO): UserDto {
        return dslContext.insertInto(USERS)
            .set(USERS.EMAIL, user.email)
            .set(USERS.FIRST_NAME, user.firstName)
            .set(USERS.LAST_NAME, user.lastName)
            .set(USERS.ROLE, user.role.name)
            .set(USERS.DEFAULT_AGENT_ID, user.defaultAgentId)
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun update(id: UUID, update: UpdateUser): UserDto {
        val updateQuery = dslContext.update(USERS)
            .set(USERS.FIRST_NAME, update.firstName)
            .set(USERS.LAST_NAME, update.lastName)
            .set(USERS.DEFAULT_AGENT_ID, update.defaultAgentId)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())

        if (update is AdminUpdateUserDTO) {
            updateQuery.set(USERS.EMAIL, update.email)
        }

        return updateQuery
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun updateRole(id: UUID, role: Roles): UserDto {
        return dslContext.update(USERS)
            .set(USERS.ROLE, role.name)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun activate(id: UUID): UserDto {
        return dslContext.update(USERS)
            .set(USERS.ACTIVE, true)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun deactivate(id: UUID): UserDto {
        return dslContext.update(USERS)
            .set(USERS.ACTIVE, false)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun delete(id: UUID): Boolean {
        return dslContext.deleteFrom(USERS)
            .where(USERS.ID.eq(id))
            .execute() == 1
    }

    fun setDefaultAgent(id: Int, userId: UUID): UserDto {
        return dslContext.update(USERS)
            .set(USERS.DEFAULT_AGENT_ID, id)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }
}
