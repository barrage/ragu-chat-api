/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.jirakira.tables.records

import java.time.OffsetDateTime
import java.util.UUID
import net.barrage.llmao.jirakira.tables.JirakiraWorkflows
import org.jooq.Record1
import org.jooq.impl.UpdatableRecordImpl

/** This class is generated by jOOQ. */
@Suppress("warnings")
open class JirakiraWorkflowsRecord private constructor() :
  UpdatableRecordImpl<JirakiraWorkflowsRecord>(JirakiraWorkflows.JIRAKIRA_WORKFLOWS) {

  open var id: UUID?
    set(value): Unit = set(0, value)
    get(): UUID? = get(0) as UUID?

  open var userId: String
    set(value): Unit = set(1, value)
    get(): String = get(1) as String

  open var username: String?
    set(value): Unit = set(2, value)
    get(): String? = get(2) as String?

  open var createdAt: OffsetDateTime?
    set(value): Unit = set(3, value)
    get(): OffsetDateTime? = get(3) as OffsetDateTime?

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  override fun key(): Record1<UUID?> = super.key() as Record1<UUID?>

  /** Create a detached, initialised JirakiraWorkflowsRecord */
  constructor(
    id: UUID? = null,
    userId: String,
    username: String? = null,
    createdAt: OffsetDateTime? = null,
  ) : this() {
    this.id = id
    this.userId = userId
    this.username = username
    this.createdAt = createdAt
    resetTouchedOnNotNull()
  }
}
