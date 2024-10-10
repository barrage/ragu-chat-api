package net.barrage.llmao.core.repository

import java.util.*
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class UserRepository(private val dslContext: DSLContext) {
  fun getAll(pagination: PaginationSort): CountedList<User> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()
    val total = dslContext.selectCount().from(USERS).fetchOne(0, Int::class.java)!!

    val users =
      dslContext
        .selectFrom(USERS)
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch(UsersRecord::toUser)

    return CountedList(total, users)
  }

  fun get(id: UUID): User? {
    return dslContext.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOne(UsersRecord::toUser)
  }

  fun getByEmail(email: String): User? {
    return dslContext.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOne(UsersRecord::toUser)
  }

  fun create(user: CreateUser): User {
    return dslContext
      .insertInto(USERS)
      .set(USERS.EMAIL, user.email)
      .set(USERS.FULL_NAME, user.fullName)
      .set(USERS.FIRST_NAME, user.firstName)
      .set(USERS.LAST_NAME, user.lastName)
      .set(USERS.ROLE, user.role.name)
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun updateNames(id: UUID, update: UpdateUser): User {
    val updateQuery =
      dslContext
        .update(USERS)
        .set(USERS.FULL_NAME, DSL.coalesce(DSL.`val`(update.fullName), USERS.FULL_NAME))
        .set(USERS.FIRST_NAME, DSL.coalesce(DSL.`val`(update.firstName), USERS.FIRST_NAME))
        .set(USERS.LAST_NAME, DSL.coalesce(DSL.`val`(update.lastName), USERS.LAST_NAME))

    return updateQuery.where(USERS.ID.eq(id)).returning().fetchOne(UsersRecord::toUser)!!
  }

  fun updateFull(id: UUID, update: UpdateUserAdmin): User {
    // TODO: Yelling
    return dslContext
      .update(USERS)
      .set(USERS.FULL_NAME, DSL.coalesce(DSL.`val`(update.fullName), USERS.FULL_NAME))
      .set(USERS.FIRST_NAME, DSL.coalesce(DSL.`val`(update.firstName), USERS.FIRST_NAME))
      .set(USERS.LAST_NAME, DSL.coalesce(DSL.`val`(update.lastName), USERS.LAST_NAME))
      .set(USERS.EMAIL, DSL.coalesce(DSL.`val`(update.email), USERS.EMAIL))
      .set(USERS.ACTIVE, DSL.coalesce(DSL.`val`(update.active), USERS.ACTIVE))
      .set(USERS.ROLE, DSL.coalesce(DSL.`val`(update.role?.name), USERS.ROLE))
      .where(USERS.ID.eq(id))
      .returning()
      .fetchOne(UsersRecord::toUser)!!
  }

  fun setActiveStatus(id: UUID, status: Boolean): Int {
    return dslContext
      .update(USERS)
      .set(USERS.ACTIVE, status)
      .where(USERS.ID.eq(id))
      .returning()
      .execute()
  }

  fun delete(id: UUID): Boolean {
    return dslContext.deleteFrom(USERS).where(USERS.ID.eq(id)).execute() == 1
  }

  private fun getSortOrder(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
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

    val order =
      if (sortOrder == SortOrder.DESC) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return order
  }
}
