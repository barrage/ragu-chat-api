/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.chat.tables

import java.util.UUID
import kotlin.collections.Collection
import kotlin.collections.List
import net.barrage.llmao.chat.Public
import net.barrage.llmao.chat.indexes.AGENT_TOOLS_AGENT_ID_IDX
import net.barrage.llmao.chat.keys.AGENT_TOOLS_PKEY
import net.barrage.llmao.chat.keys.AGENT_TOOLS_UNIQUE_AGENT_TOOL
import net.barrage.llmao.chat.keys.AGENT_TOOLS__AGENT_TOOLS_AGENT_ID_FKEY
import net.barrage.llmao.chat.tables.Agents.AgentsPath
import net.barrage.llmao.chat.tables.records.AgentToolsRecord
import org.jooq.Condition
import org.jooq.Field
import org.jooq.ForeignKey
import org.jooq.Index
import org.jooq.InverseForeignKey
import org.jooq.Name
import org.jooq.Path
import org.jooq.PlainSQL
import org.jooq.QueryPart
import org.jooq.Record
import org.jooq.SQL
import org.jooq.Schema
import org.jooq.Select
import org.jooq.Stringly
import org.jooq.Table
import org.jooq.TableField
import org.jooq.TableOptions
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

/** This class is generated by jOOQ. */
@Suppress("warnings")
open class AgentTools(
  alias: Name,
  path: Table<out Record>?,
  childPath: ForeignKey<out Record, AgentToolsRecord>?,
  parentPath: InverseForeignKey<out Record, AgentToolsRecord>?,
  aliased: Table<AgentToolsRecord>?,
  parameters: Array<Field<*>?>?,
  where: Condition?,
) :
  TableImpl<AgentToolsRecord>(
    alias,
    Public.PUBLIC,
    path,
    childPath,
    parentPath,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table(),
    where,
  ) {
  companion object {

    /** The reference instance of <code>public.agent_tools</code> */
    val AGENT_TOOLS: AgentTools = AgentTools()
  }

  /** The class holding records for this type */
  override fun getRecordType(): Class<AgentToolsRecord> = AgentToolsRecord::class.java

  /** The column <code>public.agent_tools.id</code>. */
  val ID: TableField<AgentToolsRecord, UUID?> =
    createField(
      DSL.name("id"),
      SQLDataType.UUID.nullable(false)
        .defaultValue(DSL.field(DSL.raw("uuid_generate_v4()"), SQLDataType.UUID)),
      this,
      "",
    )

  /** The column <code>public.agent_tools.agent_id</code>. */
  val AGENT_ID: TableField<AgentToolsRecord, UUID?> =
    createField(DSL.name("agent_id"), SQLDataType.UUID.nullable(false), this, "")

  /** The column <code>public.agent_tools.tool_name</code>. */
  val TOOL_NAME: TableField<AgentToolsRecord, String?> =
    createField(DSL.name("tool_name"), SQLDataType.CLOB.nullable(false), this, "")

  private constructor(
    alias: Name,
    aliased: Table<AgentToolsRecord>?,
  ) : this(alias, null, null, null, aliased, null, null)

  private constructor(
    alias: Name,
    aliased: Table<AgentToolsRecord>?,
    parameters: Array<Field<*>?>?,
  ) : this(alias, null, null, null, aliased, parameters, null)

  private constructor(
    alias: Name,
    aliased: Table<AgentToolsRecord>?,
    where: Condition?,
  ) : this(alias, null, null, null, aliased, null, where)

  /** Create an aliased <code>public.agent_tools</code> table reference */
  constructor(alias: String) : this(DSL.name(alias))

  /** Create an aliased <code>public.agent_tools</code> table reference */
  constructor(alias: Name) : this(alias, null)

  /** Create a <code>public.agent_tools</code> table reference */
  constructor() : this(DSL.name("agent_tools"), null)

  constructor(
    path: Table<out Record>,
    childPath: ForeignKey<out Record, AgentToolsRecord>?,
    parentPath: InverseForeignKey<out Record, AgentToolsRecord>?,
  ) : this(
    Internal.createPathAlias(path, childPath, parentPath),
    path,
    childPath,
    parentPath,
    AGENT_TOOLS,
    null,
    null,
  )

  /** A subtype implementing {@link Path} for simplified path-based joins. */
  open class AgentToolsPath : AgentTools, Path<AgentToolsRecord> {
    constructor(
      path: Table<out Record>,
      childPath: ForeignKey<out Record, AgentToolsRecord>?,
      parentPath: InverseForeignKey<out Record, AgentToolsRecord>?,
    ) : super(path, childPath, parentPath)

    private constructor(alias: Name, aliased: Table<AgentToolsRecord>) : super(alias, aliased)

    override fun `as`(alias: String): AgentToolsPath = AgentToolsPath(DSL.name(alias), this)

    override fun `as`(alias: Name): AgentToolsPath = AgentToolsPath(alias, this)

    override fun `as`(alias: Table<*>): AgentToolsPath = AgentToolsPath(alias.qualifiedName, this)
  }

  override fun getSchema(): Schema? = if (aliased()) null else Public.PUBLIC

  override fun getIndexes(): List<Index> = listOf(AGENT_TOOLS_AGENT_ID_IDX)

  override fun getPrimaryKey(): UniqueKey<AgentToolsRecord> = AGENT_TOOLS_PKEY

  override fun getUniqueKeys(): List<UniqueKey<AgentToolsRecord>> =
    listOf(AGENT_TOOLS_UNIQUE_AGENT_TOOL)

  override fun getReferences(): List<ForeignKey<AgentToolsRecord, *>> =
    listOf(AGENT_TOOLS__AGENT_TOOLS_AGENT_ID_FKEY)

  /** Get the implicit join path to the <code>public.agents</code> table. */
  fun agents(): AgentsPath = agents

  val agents: AgentsPath by lazy { AgentsPath(this, AGENT_TOOLS__AGENT_TOOLS_AGENT_ID_FKEY, null) }

  override fun `as`(alias: String): AgentTools = AgentTools(DSL.name(alias), this)

  override fun `as`(alias: Name): AgentTools = AgentTools(alias, this)

  override fun `as`(alias: Table<*>): AgentTools = AgentTools(alias.qualifiedName, this)

  /** Rename this table */
  override fun rename(name: String): AgentTools = AgentTools(DSL.name(name), null)

  /** Rename this table */
  override fun rename(name: Name): AgentTools = AgentTools(name, null)

  /** Rename this table */
  override fun rename(name: Table<*>): AgentTools = AgentTools(name.qualifiedName, null)

  /** Create an inline derived table from this table */
  override fun where(condition: Condition?): AgentTools =
    AgentTools(qualifiedName, if (aliased()) this else null, condition)

  /** Create an inline derived table from this table */
  override fun where(conditions: Collection<Condition>): AgentTools = where(DSL.and(conditions))

  /** Create an inline derived table from this table */
  override fun where(vararg conditions: Condition?): AgentTools = where(DSL.and(*conditions))

  /** Create an inline derived table from this table */
  override fun where(condition: Field<Boolean?>?): AgentTools = where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL override fun where(condition: SQL): AgentTools = where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(@Stringly.SQL condition: String): AgentTools = where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(@Stringly.SQL condition: String, vararg binds: Any?): AgentTools =
    where(DSL.condition(condition, *binds))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(@Stringly.SQL condition: String, vararg parts: QueryPart): AgentTools =
    where(DSL.condition(condition, *parts))

  /** Create an inline derived table from this table */
  override fun whereExists(select: Select<*>): AgentTools = where(DSL.exists(select))

  /** Create an inline derived table from this table */
  override fun whereNotExists(select: Select<*>): AgentTools = where(DSL.notExists(select))
}
