/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.chat.tables.records

import java.time.OffsetDateTime
import java.util.UUID
import net.barrage.llmao.chat.tables.AgentPermissions
import org.jooq.Record1
import org.jooq.impl.UpdatableRecordImpl

/** This class is generated by jOOQ. */
@Suppress("warnings")
open class AgentPermissionsRecord private constructor() :
  UpdatableRecordImpl<AgentPermissionsRecord>(AgentPermissions.AGENT_PERMISSIONS) {

  open var id: UUID?
    set(value): Unit = set(0, value)
    get(): UUID? = get(0) as UUID?

  open var agentId: UUID
    set(value): Unit = set(1, value)
    get(): UUID = get(1) as UUID

  open var group: String
    set(value): Unit = set(2, value)
    get(): String = get(2) as String

  open var createdBy: String
    set(value): Unit = set(3, value)
    get(): String = get(3) as String

  open var createdAt: OffsetDateTime?
    set(value): Unit = set(4, value)
    get(): OffsetDateTime? = get(4) as OffsetDateTime?

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  override fun key(): Record1<UUID?> = super.key() as Record1<UUID?>

  /** Create a detached, initialised AgentPermissionsRecord */
  constructor(
    id: UUID? = null,
    agentId: UUID,
    group: String,
    createdBy: String,
    createdAt: OffsetDateTime? = null,
  ) : this() {
    this.id = id
    this.agentId = agentId
    this.group = group
    this.createdBy = createdBy
    this.createdAt = createdAt
    resetTouchedOnNotNull()
  }
}
