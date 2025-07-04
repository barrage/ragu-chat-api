/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.chat.tables.records

import java.time.OffsetDateTime
import java.util.UUID
import net.barrage.llmao.chat.tables.AgentConfigurations
import org.jooq.Record1
import org.jooq.impl.UpdatableRecordImpl

/** This class is generated by jOOQ. */
@Suppress("warnings")
open class AgentConfigurationsRecord private constructor() :
  UpdatableRecordImpl<AgentConfigurationsRecord>(AgentConfigurations.AGENT_CONFIGURATIONS) {

  open var id: UUID?
    set(value): Unit = set(0, value)
    get(): UUID? = get(0) as UUID?

  open var agentId: UUID
    set(value): Unit = set(1, value)
    get(): UUID = get(1) as UUID

  open var version: Int
    set(value): Unit = set(2, value)
    get(): Int = get(2) as Int

  open var context: String
    set(value): Unit = set(3, value)
    get(): String = get(3) as String

  open var llmProvider: String
    set(value): Unit = set(4, value)
    get(): String = get(4) as String

  open var model: String
    set(value): Unit = set(5, value)
    get(): String = get(5) as String

  open var presencePenalty: Double?
    set(value): Unit = set(6, value)
    get(): Double? = get(6) as Double?

  open var maxHistoryTokens: Int?
    set(value): Unit = set(7, value)
    get(): Int? = get(7) as Int?

  open var temperature: Double?
    set(value): Unit = set(8, value)
    get(): Double? = get(8) as Double?

  open var titleInstruction: String?
    set(value): Unit = set(9, value)
    get(): String? = get(9) as String?

  open var errorMessage: String?
    set(value): Unit = set(10, value)
    get(): String? = get(10) as String?

  open var createdAt: OffsetDateTime?
    set(value): Unit = set(11, value)
    get(): OffsetDateTime? = get(11) as OffsetDateTime?

  open var updatedAt: OffsetDateTime?
    set(value): Unit = set(12, value)
    get(): OffsetDateTime? = get(12) as OffsetDateTime?

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  override fun key(): Record1<UUID?> = super.key() as Record1<UUID?>

  /** Create a detached, initialised AgentConfigurationsRecord */
  constructor(
    id: UUID? = null,
    agentId: UUID,
    version: Int,
    context: String,
    llmProvider: String,
    model: String,
    presencePenalty: Double? = null,
    maxHistoryTokens: Int? = null,
    temperature: Double? = null,
    titleInstruction: String? = null,
    errorMessage: String? = null,
    createdAt: OffsetDateTime? = null,
    updatedAt: OffsetDateTime? = null,
  ) : this() {
    this.id = id
    this.agentId = agentId
    this.version = version
    this.context = context
    this.llmProvider = llmProvider
    this.model = model
    this.presencePenalty = presencePenalty
    this.maxHistoryTokens = maxHistoryTokens
    this.temperature = temperature
    this.titleInstruction = titleInstruction
    this.errorMessage = errorMessage
    this.createdAt = createdAt
    this.updatedAt = updatedAt
    resetTouchedOnNotNull()
  }
}
