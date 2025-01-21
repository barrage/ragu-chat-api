package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.UpdateUser
import net.barrage.llmao.core.models.UpdateUserAdmin
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.UserCounts
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class UserRepository(private val dslContext: DSLContext) {
  suspend fun getAll(pagination: PaginationSort): CountedList<User> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()
    val total =
      dslContext.selectCount().from(USERS).where(USERS.DELETED_AT.isNull).awaitSingle().value1()
        ?: 0

    val users =
      dslContext
        .select(
          USERS.ID,
          USERS.EMAIL,
          USERS.FULL_NAME,
          USERS.FIRST_NAME,
          USERS.LAST_NAME,
          USERS.ROLE,
          USERS.ACTIVE,
          USERS.CREATED_AT,
          USERS.UPDATED_AT,
          USERS.DELETED_AT,
        )
        .from(USERS)
        .where(USERS.DELETED_AT.isNull)
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.into(USERS).toUser() }
        .toList()

    return CountedList(total, users)
  }

  suspend fun get(id: UUID): User? {
    return dslContext
      .select(
        USERS.ID,
        USERS.EMAIL,
        USERS.FULL_NAME,
        USERS.FIRST_NAME,
        USERS.LAST_NAME,
        USERS.ROLE,
        USERS.ACTIVE,
        USERS.CREATED_AT,
        USERS.UPDATED_AT,
        USERS.DELETED_AT,
      )
      .from(USERS)
      .where(USERS.ID.eq(id).and(USERS.DELETED_AT.isNull))
      .awaitFirstOrNull()
      ?.into(USERS)
      ?.toUser()
  }

  suspend fun getByEmail(email: String): User? {
    return dslContext
      .select(
        USERS.ID,
        USERS.EMAIL,
        USERS.FULL_NAME,
        USERS.FIRST_NAME,
        USERS.LAST_NAME,
        USERS.ROLE,
        USERS.ACTIVE,
        USERS.CREATED_AT,
        USERS.UPDATED_AT,
        USERS.DELETED_AT,
      )
      .from(USERS)
      .where(USERS.EMAIL.eq(email).and(USERS.DELETED_AT.isNull))
      .awaitFirstOrNull()
      ?.into(USERS)
      ?.toUser()
  }

  suspend fun create(user: CreateUser): User {
    return dslContext
      .insertInto(USERS)
      .set(USERS.EMAIL, user.email)
      .set(USERS.FULL_NAME, user.fullName)
      .set(USERS.FIRST_NAME, user.firstName)
      .set(USERS.LAST_NAME, user.lastName)
      .set(USERS.ROLE, user.role.name)
      .returning()
      .awaitSingle()
      .into(USERS)
      .toUser()
  }

  suspend fun updateNames(id: UUID, update: UpdateUser): User {
    return dslContext
      .update(USERS)
      .set(USERS.FULL_NAME, DSL.coalesce(DSL.`val`(update.fullName), USERS.FULL_NAME))
      .set(USERS.FIRST_NAME, DSL.coalesce(DSL.`val`(update.firstName), USERS.FIRST_NAME))
      .set(USERS.LAST_NAME, DSL.coalesce(DSL.`val`(update.lastName), USERS.LAST_NAME))
      .where(USERS.ID.eq(id).and(USERS.DELETED_AT.isNull))
      .returning()
      .awaitSingle()
      .into(USERS)
      .toUser()
  }

  suspend fun updateFull(id: UUID, update: UpdateUserAdmin): User {
    return dslContext
      .update(USERS)
      .set(USERS.FULL_NAME, DSL.coalesce(DSL.`val`(update.fullName), USERS.FULL_NAME))
      .set(USERS.FIRST_NAME, DSL.coalesce(DSL.`val`(update.firstName), USERS.FIRST_NAME))
      .set(USERS.LAST_NAME, DSL.coalesce(DSL.`val`(update.lastName), USERS.LAST_NAME))
      .set(USERS.EMAIL, DSL.coalesce(DSL.`val`(update.email), USERS.EMAIL))
      .set(USERS.ACTIVE, DSL.coalesce(DSL.`val`(update.active), USERS.ACTIVE))
      .set(USERS.ROLE, DSL.coalesce(DSL.`val`(update.role?.name), USERS.ROLE))
      .where(USERS.ID.eq(id).and(USERS.DELETED_AT.isNull))
      .returning()
      .awaitSingle()
      .into(USERS)
      .toUser()
  }

  suspend fun delete(id: UUID): Boolean {
    return dslContext
      .update(USERS)
      .set(USERS.FULL_NAME, "deleted")
      .set(USERS.FIRST_NAME, "deleted")
      .set(USERS.LAST_NAME, "deleted")
      .set(USERS.EMAIL, "$id@deleted.net")
      .set(USERS.ACTIVE, false)
      .set(USERS.DELETED_AT, OffsetDateTime.now())
      .where(USERS.ID.eq(id))
      .awaitSingle() == 1
  }

  private fun getSortOrder(pagination: PaginationSort): List<SortField<*>> {
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

    val order = mutableListOf<SortField<*>>()
    if (sortOrder == SortOrder.DESC) {
      order.add(sortField.desc())
    } else {
      order.add(sortField.asc())
    }

    if (sortField == USERS.CREATED_AT) {
      order.add(USERS.FULL_NAME.asc())
    }

    return order
  }

  suspend fun getUserCounts(): UserCounts {
    val total: Int =
      dslContext.selectCount().from(USERS).where(USERS.DELETED_AT.isNull).awaitSingle().value1()
        ?: 0

    val active: Int =
      dslContext
        .selectCount()
        .from(USERS)
        .where(USERS.DELETED_AT.isNull.and(USERS.ACTIVE.isTrue))
        .awaitSingle()
        .value1() ?: 0

    val inactive: Int = total - active

    val admin: Int =
      dslContext
        .selectCount()
        .from(USERS)
        .where(USERS.DELETED_AT.isNull.and(USERS.ACTIVE.isTrue).and(USERS.ROLE.eq("ADMIN")))
        .awaitSingle()
        .value1() ?: 0

    val user: Int = active - admin

    return UserCounts(total, active, inactive, admin, user)
  }

  suspend fun insertUsers(users: List<CreateUser>): List<String> {
    return dslContext
      .insertInto(
        USERS,
        USERS.EMAIL,
        USERS.FULL_NAME,
        USERS.FIRST_NAME,
        USERS.LAST_NAME,
        USERS.ROLE,
        USERS.ACTIVE,
      )
      .apply {
        users.forEach { user ->
          values(user.email, user.fullName, user.firstName, user.lastName, user.role.name, true)
        }
      }
      .onConflict(USERS.EMAIL)
      .doNothing()
      .returning()
      .asFlow()
      .map { it.email }
      .toList()
  }
}
