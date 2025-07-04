/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.bonvoyage.tables

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.util.UUID
import kotlin.collections.Collection
import kotlin.collections.List
import net.barrage.llmao.bonvoyage.Public
import net.barrage.llmao.bonvoyage.indexes.BONVOYAGE_TRAVEL_REQUESTS_STATUS_IDX
import net.barrage.llmao.bonvoyage.indexes.BONVOYAGE_TRAVEL_REQUESTS_TRIP_ID_IDX
import net.barrage.llmao.bonvoyage.indexes.BONVOYAGE_TRAVEL_REQUESTS_USER_ID_IDX
import net.barrage.llmao.bonvoyage.keys.BONVOYAGE_TRAVEL_REQUESTS_PKEY
import net.barrage.llmao.bonvoyage.keys.BONVOYAGE_TRAVEL_REQUESTS__BONVOYAGE_TRAVEL_REQUESTS_TRIP_ID_FKEY
import net.barrage.llmao.bonvoyage.tables.BonvoyageTrips.BonvoyageTripsPath
import net.barrage.llmao.bonvoyage.tables.records.BonvoyageTravelRequestsRecord
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
open class BonvoyageTravelRequests(
  alias: Name,
  path: Table<out Record>?,
  childPath: ForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
  parentPath: InverseForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
  aliased: Table<BonvoyageTravelRequestsRecord>?,
  parameters: Array<Field<*>?>?,
  where: Condition?,
) :
  TableImpl<BonvoyageTravelRequestsRecord>(
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

    /** The reference instance of <code>public.bonvoyage_travel_requests</code> */
    val BONVOYAGE_TRAVEL_REQUESTS: BonvoyageTravelRequests = BonvoyageTravelRequests()
  }

  /** The class holding records for this type */
  override fun getRecordType(): Class<BonvoyageTravelRequestsRecord> =
    BonvoyageTravelRequestsRecord::class.java

  /** The column <code>public.bonvoyage_travel_requests.id</code>. */
  val ID: TableField<BonvoyageTravelRequestsRecord, UUID?> =
    createField(
      DSL.name("id"),
      SQLDataType.UUID.nullable(false)
        .defaultValue(DSL.field(DSL.raw("uuid_generate_v4()"), SQLDataType.UUID)),
      this,
      "",
    )

  /** The column <code>public.bonvoyage_travel_requests.user_id</code>. */
  val USER_ID: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("user_id"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.user_full_name</code>. */
  val USER_FULL_NAME: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("user_full_name"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.user_email</code>. */
  val USER_EMAIL: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("user_email"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.start_location</code>. */
  val START_LOCATION: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("start_location"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.stops</code>. */
  val STOPS: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("stops"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.end_location</code>. */
  val END_LOCATION: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("end_location"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.transport_type</code>. */
  val TRANSPORT_TYPE: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("transport_type"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.description</code>. */
  val DESCRIPTION: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("description"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.vehicle_type</code>. */
  val VEHICLE_TYPE: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("vehicle_type"), SQLDataType.CLOB, this, "")

  /** The column <code>public.bonvoyage_travel_requests.vehicle_registration</code>. */
  val VEHICLE_REGISTRATION: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("vehicle_registration"), SQLDataType.CLOB, this, "")

  /** The column <code>public.bonvoyage_travel_requests.is_driver</code>. */
  val IS_DRIVER: TableField<BonvoyageTravelRequestsRecord, Boolean?> =
    createField(
      DSL.name("is_driver"),
      SQLDataType.BOOLEAN.nullable(false)
        .defaultValue(DSL.field(DSL.raw("false"), SQLDataType.BOOLEAN)),
      this,
      "",
    )

  /** The column <code>public.bonvoyage_travel_requests.start_date</code>. */
  val START_DATE: TableField<BonvoyageTravelRequestsRecord, LocalDate?> =
    createField(DSL.name("start_date"), SQLDataType.LOCALDATE.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.end_date</code>. */
  val END_DATE: TableField<BonvoyageTravelRequestsRecord, LocalDate?> =
    createField(DSL.name("end_date"), SQLDataType.LOCALDATE.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.expected_start_time</code>. */
  val EXPECTED_START_TIME: TableField<BonvoyageTravelRequestsRecord, OffsetTime?> =
    createField(DSL.name("expected_start_time"), SQLDataType.TIMEWITHTIMEZONE(6), this, "")

  /** The column <code>public.bonvoyage_travel_requests.expected_end_time</code>. */
  val EXPECTED_END_TIME: TableField<BonvoyageTravelRequestsRecord, OffsetTime?> =
    createField(DSL.name("expected_end_time"), SQLDataType.TIMEWITHTIMEZONE(6), this, "")

  /** The column <code>public.bonvoyage_travel_requests.status</code>. */
  val STATUS: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("status"), SQLDataType.CLOB.nullable(false), this, "")

  /** The column <code>public.bonvoyage_travel_requests.reviewer_id</code>. */
  val REVIEWER_ID: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("reviewer_id"), SQLDataType.CLOB, this, "")

  /** The column <code>public.bonvoyage_travel_requests.review_comment</code>. */
  val REVIEW_COMMENT: TableField<BonvoyageTravelRequestsRecord, String?> =
    createField(DSL.name("review_comment"), SQLDataType.CLOB, this, "")

  /** The column <code>public.bonvoyage_travel_requests.trip_id</code>. */
  val TRIP_ID: TableField<BonvoyageTravelRequestsRecord, UUID?> =
    createField(DSL.name("trip_id"), SQLDataType.UUID, this, "")

  /** The column <code>public.bonvoyage_travel_requests.created_at</code>. */
  val CREATED_AT: TableField<BonvoyageTravelRequestsRecord, OffsetDateTime?> =
    createField(
      DSL.name("created_at"),
      SQLDataType.TIMESTAMPWITHTIMEZONE(6)
        .nullable(false)
        .defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.TIMESTAMPWITHTIMEZONE)),
      this,
      "",
    )

  private constructor(
    alias: Name,
    aliased: Table<BonvoyageTravelRequestsRecord>?,
  ) : this(alias, null, null, null, aliased, null, null)

  private constructor(
    alias: Name,
    aliased: Table<BonvoyageTravelRequestsRecord>?,
    parameters: Array<Field<*>?>?,
  ) : this(alias, null, null, null, aliased, parameters, null)

  private constructor(
    alias: Name,
    aliased: Table<BonvoyageTravelRequestsRecord>?,
    where: Condition?,
  ) : this(alias, null, null, null, aliased, null, where)

  /** Create an aliased <code>public.bonvoyage_travel_requests</code> table reference */
  constructor(alias: String) : this(DSL.name(alias))

  /** Create an aliased <code>public.bonvoyage_travel_requests</code> table reference */
  constructor(alias: Name) : this(alias, null)

  /** Create a <code>public.bonvoyage_travel_requests</code> table reference */
  constructor() : this(DSL.name("bonvoyage_travel_requests"), null)

  constructor(
    path: Table<out Record>,
    childPath: ForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
    parentPath: InverseForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
  ) : this(
    Internal.createPathAlias(path, childPath, parentPath),
    path,
    childPath,
    parentPath,
    BONVOYAGE_TRAVEL_REQUESTS,
    null,
    null,
  )

  /** A subtype implementing {@link Path} for simplified path-based joins. */
  open class BonvoyageTravelRequestsPath :
    BonvoyageTravelRequests, Path<BonvoyageTravelRequestsRecord> {
    constructor(
      path: Table<out Record>,
      childPath: ForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
      parentPath: InverseForeignKey<out Record, BonvoyageTravelRequestsRecord>?,
    ) : super(path, childPath, parentPath)

    private constructor(
      alias: Name,
      aliased: Table<BonvoyageTravelRequestsRecord>,
    ) : super(alias, aliased)

    override fun `as`(alias: String): BonvoyageTravelRequestsPath =
      BonvoyageTravelRequestsPath(DSL.name(alias), this)

    override fun `as`(alias: Name): BonvoyageTravelRequestsPath =
      BonvoyageTravelRequestsPath(alias, this)

    override fun `as`(alias: Table<*>): BonvoyageTravelRequestsPath =
      BonvoyageTravelRequestsPath(alias.qualifiedName, this)
  }

  override fun getSchema(): Schema? = if (aliased()) null else Public.PUBLIC

  override fun getIndexes(): List<Index> =
    listOf(
      BONVOYAGE_TRAVEL_REQUESTS_STATUS_IDX,
      BONVOYAGE_TRAVEL_REQUESTS_TRIP_ID_IDX,
      BONVOYAGE_TRAVEL_REQUESTS_USER_ID_IDX,
    )

  override fun getPrimaryKey(): UniqueKey<BonvoyageTravelRequestsRecord> =
    BONVOYAGE_TRAVEL_REQUESTS_PKEY

  override fun getReferences(): List<ForeignKey<BonvoyageTravelRequestsRecord, *>> =
    listOf(BONVOYAGE_TRAVEL_REQUESTS__BONVOYAGE_TRAVEL_REQUESTS_TRIP_ID_FKEY)

  /** Get the implicit join path to the <code>public.bonvoyage_trips</code> table. */
  fun bonvoyageTrips(): BonvoyageTripsPath = bonvoyageTrips

  val bonvoyageTrips: BonvoyageTripsPath by lazy {
    BonvoyageTripsPath(
      this,
      BONVOYAGE_TRAVEL_REQUESTS__BONVOYAGE_TRAVEL_REQUESTS_TRIP_ID_FKEY,
      null,
    )
  }

  override fun `as`(alias: String): BonvoyageTravelRequests =
    BonvoyageTravelRequests(DSL.name(alias), this)

  override fun `as`(alias: Name): BonvoyageTravelRequests = BonvoyageTravelRequests(alias, this)

  override fun `as`(alias: Table<*>): BonvoyageTravelRequests =
    BonvoyageTravelRequests(alias.qualifiedName, this)

  /** Rename this table */
  override fun rename(name: String): BonvoyageTravelRequests =
    BonvoyageTravelRequests(DSL.name(name), null)

  /** Rename this table */
  override fun rename(name: Name): BonvoyageTravelRequests = BonvoyageTravelRequests(name, null)

  /** Rename this table */
  override fun rename(name: Table<*>): BonvoyageTravelRequests =
    BonvoyageTravelRequests(name.qualifiedName, null)

  /** Create an inline derived table from this table */
  override fun where(condition: Condition?): BonvoyageTravelRequests =
    BonvoyageTravelRequests(qualifiedName, if (aliased()) this else null, condition)

  /** Create an inline derived table from this table */
  override fun where(conditions: Collection<Condition>): BonvoyageTravelRequests =
    where(DSL.and(conditions))

  /** Create an inline derived table from this table */
  override fun where(vararg conditions: Condition?): BonvoyageTravelRequests =
    where(DSL.and(*conditions))

  /** Create an inline derived table from this table */
  override fun where(condition: Field<Boolean?>?): BonvoyageTravelRequests =
    where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(condition: SQL): BonvoyageTravelRequests = where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(@Stringly.SQL condition: String): BonvoyageTravelRequests =
    where(DSL.condition(condition))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(@Stringly.SQL condition: String, vararg binds: Any?): BonvoyageTravelRequests =
    where(DSL.condition(condition, *binds))

  /** Create an inline derived table from this table */
  @PlainSQL
  override fun where(
    @Stringly.SQL condition: String,
    vararg parts: QueryPart,
  ): BonvoyageTravelRequests = where(DSL.condition(condition, *parts))

  /** Create an inline derived table from this table */
  override fun whereExists(select: Select<*>): BonvoyageTravelRequests = where(DSL.exists(select))

  /** Create an inline derived table from this table */
  override fun whereNotExists(select: Select<*>): BonvoyageTravelRequests =
    where(DSL.notExists(select))
}
