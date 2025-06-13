package net.barrage.llmao.core.database

import net.barrage.llmao.core.model.common.PropertyUpdate
import org.jooq.Condition
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.TableField
import org.jooq.UpdateSetMoreStep
import org.jooq.UpdateSetStep
import org.jooq.impl.DSL
import org.jooq.impl.DSL.excluded

/**
 * Utility for setting a condition to match the value if it is not null.
 *
 * Outputs either `TABLE_FIELD.eq(field)` or `DSL.noCondition()`.
 */
fun <R : Record, T> T?.optionalEq(field: TableField<R, T>): Condition =
    this?.let { field.eq(it) } ?: DSL.noCondition()

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Semantics are defined in [PropertyUpdate].
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
    update: PropertyUpdate<T>,
    field: TableField<R, T>,
): UpdateSetMoreStep<R> {
    return when (update) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> this

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, update.value)

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

/** The same as a regular `set` but with a remapping function. */
fun <U, R : Record, T> UpdateSetMoreStep<R>.set(
    update: PropertyUpdate<U>,
    field: TableField<R, T>,
    remap: (U) -> T,
): UpdateSetMoreStep<R> {
    return when (update) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> this

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, remap(update.value))

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

/** The same as a regular `set` but with a remapping function. */
fun <U, R : Record, T> UpdateSetStep<R>.set(
    update: PropertyUpdate<U>,
    field: TableField<R, T>,
    remap: (U) -> T,
): UpdateSetMoreStep<R> {
    return when (update) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> this as UpdateSetMoreStep<R>

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, remap(update.value))

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Semantics are defined in [PropertyUpdate].
 */
fun <R : Record, T> UpdateSetStep<R>.set(
    update: PropertyUpdate<T>,
    field: TableField<R, T>,
): UpdateSetMoreStep<R> {
    return when (update) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> this as UpdateSetMoreStep<R>

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, update.value)

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
    update: PropertyUpdate<T>,
    field: TableField<R, T>,
): InsertOnDuplicateSetMoreStep<R> {
    return when (update) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> set(field, excluded(field))

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, update.value)

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

fun <R : Record, T> InsertSetMoreStep<R>.set(
    value: PropertyUpdate<T>,
    field: TableField<R, T>,
    defaultIfUndefined: T? = null,
): InsertSetMoreStep<R> {
    return when (value) {
        // Do nothing when property is not set
        is PropertyUpdate.Undefined -> defaultIfUndefined?.let { set(field, it) } ?: this

        // Property is being updated to new value
        is PropertyUpdate.Value -> set(field, value.value)

        // Property is being removed
        is PropertyUpdate.Null -> setNull(field)
    }
}

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <R : Record, T> UpdateSetStep<R>.set(
    update: T?,
    field: TableField<R, T>,
    defaultIfNull: T? = null,
): UpdateSetMoreStep<R> =
    update?.let { set(field, it) }
        ?: defaultIfNull?.let { set(field, it) }
        ?: this as UpdateSetMoreStep<R>

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <U, R : Record, T> UpdateSetStep<R>.set(
    update: U?,
    field: TableField<R, T>,
    remap: (U) -> T,
): UpdateSetMoreStep<R> = update?.let { set(field, remap(it)) } ?: this as UpdateSetMoreStep<R>

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
    update: T?,
    field: TableField<R, T>,
    defaultIfNull: T? = null,
): UpdateSetMoreStep<R> =
    update?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * statement as is.
 */
fun <R : Record, T> InsertSetMoreStep<R>.set(
    value: T?,
    field: TableField<R, T>,
    defaultIfNull: T? = null,
): InsertSetMoreStep<R> =
    value?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * value as is.
 */
fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
    value: T?,
    field: TableField<R, T>,
    defaultIfNull: T? = null,
): InsertOnDuplicateSetMoreStep<R> =
    value?.let { set(field, it) }
        ?: defaultIfNull?.let { set(field, it) }
        ?: set(field, excluded(field))
