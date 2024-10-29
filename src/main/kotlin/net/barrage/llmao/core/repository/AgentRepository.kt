package net.barrage.llmao.core.repository

import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.GraphData
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toCollection
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_INSTRUCTIONS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class AgentRepository(private val dslContext: DSLContext) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .fetchOne(0, Int::class.java)!!

    val agents =
      dslContext
        .selectFrom(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch(AgentsRecord::toAgent)

    return CountedList(total, agents)
  }

  fun get(id: KUUID): AgentFull {
    return dslContext
      .select(
        AGENTS.asterisk(),
        AGENT_INSTRUCTIONS.TITLE_INSTRUCTION,
        AGENT_INSTRUCTIONS.LANGUAGE_INSTRUCTION,
        AGENT_INSTRUCTIONS.SUMMARY_INSTRUCTION,
      )
      .from(AGENTS)
      .leftJoin(AGENT_INSTRUCTIONS)
      .on(AGENTS.ID.eq(AGENT_INSTRUCTIONS.AGENT_ID))
      .where(AGENTS.ID.eq(id))
      .fetchOne { record ->
        val agent = record.into(AGENTS).toAgent()
        val titleInstruction = record.get(AGENT_INSTRUCTIONS.TITLE_INSTRUCTION)
        val languageInstruction = record.get(AGENT_INSTRUCTIONS.LANGUAGE_INSTRUCTION)
        val summaryInstruction = record.get(AGENT_INSTRUCTIONS.SUMMARY_INSTRUCTION)
        val collections = getCollections(id)
        AgentFull(
          agent,
          AgentInstructions(titleInstruction, languageInstruction, summaryInstruction),
          collections,
        )
      } ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")
  }

  fun create(create: CreateAgent): Agent? {
    val agent =
      dslContext.transactionResult { tx ->
        val context = DSL.using(tx)
        val agent =
          context
            .insertInto(AGENTS)
            .set(AGENTS.NAME, create.name)
            .set(AGENTS.CONTEXT, create.context)
            .set(AGENTS.DESCRIPTION, create.description)
            .set(AGENTS.LLM_PROVIDER, create.llmProvider)
            .set(AGENTS.MODEL, create.model)
            .set(AGENTS.VECTOR_PROVIDER, create.vectorProvider)
            .set(AGENTS.EMBEDDING_PROVIDER, create.embeddingProvider)
            .set(AGENTS.EMBEDDING_MODEL, create.embeddingModel)
            .set(AGENTS.ACTIVE, create.active)
            .set(AGENTS.LANGUAGE, create.language)
            .set(AGENTS.TEMPERATURE, create.temperature)
            .returning()
            .fetchOne(AgentsRecord::toAgent)

        context
          .insertInto(AGENT_INSTRUCTIONS)
          .set(AGENT_INSTRUCTIONS.AGENT_ID, agent!!.id)
          .set(AGENT_INSTRUCTIONS.TITLE_INSTRUCTION, create.instructions?.titleInstruction)
          .set(AGENT_INSTRUCTIONS.LANGUAGE_INSTRUCTION, create.instructions?.languageInstruction)
          .set(AGENT_INSTRUCTIONS.SUMMARY_INSTRUCTION, create.instructions?.summaryInstruction)
          .execute()

        return@transactionResult agent
      }

    return agent
  }

  fun update(id: KUUID, update: UpdateAgent): Agent? {
    val agent =
      dslContext.transactionResult { tx ->
        val context = DSL.using(tx)
        val agent =
          context
            .update(AGENTS)
            .set(AGENTS.NAME, DSL.coalesce(DSL.`val`(update.name), AGENTS.NAME))
            .set(
              AGENTS.DESCRIPTION,
              DSL.coalesce(DSL.`val`(update.description), AGENTS.DESCRIPTION),
            )
            .set(AGENTS.CONTEXT, DSL.coalesce(DSL.`val`(update.context), AGENTS.CONTEXT))
            .set(
              AGENTS.LLM_PROVIDER,
              DSL.coalesce(DSL.`val`(update.llmProvider), AGENTS.LLM_PROVIDER),
            )
            .set(AGENTS.MODEL, DSL.coalesce(DSL.`val`(update.model), AGENTS.MODEL))
            .set(
              AGENTS.TEMPERATURE,
              DSL.coalesce(DSL.`val`(update.temperature), AGENTS.TEMPERATURE),
            )
            .set(
              AGENTS.VECTOR_PROVIDER,
              DSL.coalesce(DSL.`val`(update.vectorProvider), AGENTS.VECTOR_PROVIDER),
            )
            .set(AGENTS.LANGUAGE, DSL.coalesce(DSL.`val`(update.language), AGENTS.LANGUAGE))
            .set(AGENTS.ACTIVE, DSL.coalesce(DSL.`val`(update.active), AGENTS.ACTIVE))
            .set(
              AGENTS.EMBEDDING_PROVIDER,
              DSL.coalesce(DSL.`val`(update.embeddingProvider), AGENTS.EMBEDDING_PROVIDER),
            )
            .set(
              AGENTS.EMBEDDING_MODEL,
              DSL.coalesce(DSL.`val`(update.embeddingModel), AGENTS.EMBEDDING_MODEL),
            )
            .where(AGENTS.ID.eq(id))
            .returning()
            .fetchOne(AgentsRecord::toAgent)

        update.instructions?.let {
          context
            .update(AGENT_INSTRUCTIONS)
            .set(
              AGENT_INSTRUCTIONS.TITLE_INSTRUCTION,
              DSL.coalesce(DSL.`val`(it.titleInstruction), AGENT_INSTRUCTIONS.TITLE_INSTRUCTION),
            )
            .set(
              AGENT_INSTRUCTIONS.LANGUAGE_INSTRUCTION,
              DSL.coalesce(
                DSL.`val`(it.languageInstruction),
                AGENT_INSTRUCTIONS.LANGUAGE_INSTRUCTION,
              ),
            )
            .set(
              AGENT_INSTRUCTIONS.SUMMARY_INSTRUCTION,
              DSL.coalesce(DSL.`val`(it.summaryInstruction), AGENT_INSTRUCTIONS.SUMMARY_INSTRUCTION),
            )
            .execute()
        }

        agent
      }

    return agent
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections) {
    update.add?.let {
      dslContext
        .batch(
          it.map { (name, amount, instruction) ->
            dslContext
              .insertInto(AGENT_COLLECTIONS)
              .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
              .set(AGENT_COLLECTIONS.COLLECTION, name)
              .set(AGENT_COLLECTIONS.AMOUNT, amount)
              .set(AGENT_COLLECTIONS.INSTRUCTION, instruction)
              .onConflict(AGENT_COLLECTIONS.AGENT_ID, AGENT_COLLECTIONS.COLLECTION)
              .doUpdate()
              .set(AGENT_COLLECTIONS.AMOUNT, amount)
          }
        )
        .execute()
    }

    update.remove?.let {
      dslContext
        .deleteFrom(AGENT_COLLECTIONS)
        .where(AGENT_COLLECTIONS.AGENT_ID.eq(agentId).and(AGENT_COLLECTIONS.COLLECTION.`in`(it)))
        .execute()
    }
  }

  fun getCollections(id: KUUID): List<AgentCollection> {
    val collections =
      dslContext
        .selectFrom(AGENT_COLLECTIONS)
        .where(AGENT_COLLECTIONS.AGENT_ID.eq(id))
        .fetchInto(AgentCollectionsRecord::class.java)
    return collections.map { it.toCollection() }
  }

  private fun getSortOrder(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "name" -> AGENTS.NAME
        "description" -> AGENTS.DESCRIPTION
        "context" -> AGENTS.CONTEXT
        "llmProvider" -> AGENTS.LLM_PROVIDER
        "createdAt" -> AGENTS.CREATED_AT
        "updatedAt" -> AGENTS.UPDATED_AT
        "active" -> AGENTS.ACTIVE
        else -> AGENTS.NAME
      }

    val order =
      if (sortOrder == SortOrder.DESC) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return order
  }

  fun getAgentCounts(): AgentCounts {
    val total: Int = dslContext.selectCount().from(AGENTS).fetchOne(0, Int::class.java)!!

    val active: Int =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(AGENTS.ACTIVE.isTrue)
        .groupBy(AGENTS.ACTIVE)
        .fetchOne(0, Int::class.java)!!

    val inactive: Int = total - active

    val providerCounts: List<GraphData> =
      dslContext
        .select(AGENTS.LLM_PROVIDER, DSL.count())
        .from(AGENTS)
        .where(AGENTS.ACTIVE.isTrue)
        .groupBy(AGENTS.LLM_PROVIDER)
        .fetch()
        .map { GraphData(it.value1()!!, it.value2()!!) }

    return AgentCounts(total, active, inactive, providerCounts)
  }
}
