/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.bonvoyage.tables.records

import java.time.OffsetDateTime
import java.util.UUID
import net.barrage.llmao.bonvoyage.tables.BonvoyageTravelManagerUserMappings
import org.jooq.Record1
import org.jooq.impl.UpdatableRecordImpl

/** This class is generated by jOOQ. */
@Suppress("warnings")
open class BonvoyageTravelManagerUserMappingsRecord private constructor() :
  UpdatableRecordImpl<BonvoyageTravelManagerUserMappingsRecord>(
    BonvoyageTravelManagerUserMappings.BONVOYAGE_TRAVEL_MANAGER_USER_MAPPINGS
  ) {

  open var id: UUID?
    set(value): Unit = set(0, value)
    get(): UUID? = get(0) as UUID?

  open var travelManagerId: String
    set(value): Unit = set(1, value)
    get(): String = get(1) as String

  open var userId: String
    set(value): Unit = set(2, value)
    get(): String = get(2) as String

  open var delivery: String
    set(value): Unit = set(3, value)
    get(): String = get(3) as String

  open var createdAt: OffsetDateTime?
    set(value): Unit = set(4, value)
    get(): OffsetDateTime? = get(4) as OffsetDateTime?

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  override fun key(): Record1<UUID?> = super.key() as Record1<UUID?>

  /** Create a detached, initialised BonvoyageTravelManagerUserMappingsRecord */
  constructor(
    id: UUID? = null,
    travelManagerId: String,
    userId: String,
    delivery: String,
    createdAt: OffsetDateTime? = null,
  ) : this() {
    this.id = id
    this.travelManagerId = travelManagerId
    this.userId = userId
    this.delivery = delivery
    this.createdAt = createdAt
    resetTouchedOnNotNull()
  }
}
