package net.barrage.llmao.repositories

import net.barrage.llmao.dtos.users.NewUserDTO
import net.barrage.llmao.dtos.users.UpdateUserDTO
import net.barrage.llmao.dtos.users.UserDto
import net.barrage.llmao.dtos.users.toUserDto
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

    fun getWithIdAndPassword(id: UUID, password: String): UserDto? {
        return dslContext.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .and(USERS.PASSWORD.eq(password))
            .fetchOne(UsersRecord::toUserDto)
    }

    fun create(user: NewUserDTO): UserDto {
        return dslContext.insertInto(USERS)
            .set(USERS.USERNAME, user.username)
            .set(USERS.EMAIL, user.email)
            .set(USERS.PASSWORD, user.password)
            .set(USERS.FIRST_NAME, user.firstName)
            .set(USERS.LAST_NAME, user.lastName)
            .set(USERS.ROLE, user.role)
            .set(USERS.DEFAULT_AGENT_ID, user.defaultAgentId)
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun update(id: UUID, update: UpdateUserDTO): UserDto {
        return dslContext.update(USERS)
            .set(USERS.USERNAME, update.username)
            .set(USERS.EMAIL, update.email)
            .set(USERS.FIRST_NAME, update.firstName)
            .set(USERS.LAST_NAME, update.lastName)
            .set(USERS.DEFAULT_AGENT_ID, update.defaultAgentId)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun updatePassword(id: UUID, password: String): UserDto {
        return dslContext.update(USERS)
            .set(USERS.PASSWORD, password)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returning()
            .fetchOne(UsersRecord::toUserDto)!!
    }

    fun updateRole(id: UUID, role: String): UserDto {
        return dslContext.update(USERS)
            .set(USERS.ROLE, role)
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
}