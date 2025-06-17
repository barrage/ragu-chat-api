package net.barrage.llmao.app.http

import io.ktor.server.application.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.isAccessible
import net.barrage.llmao.core.model.common.SortOrder
import net.barrage.llmao.types.KLocalDate
import net.barrage.llmao.types.KOffsetDateTime

/** Annotate class fields you want to include for parsing with [query]. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QueryParameter(val key: String = "")

/**
 * TODO: Annotation processor so we don't have to reflect in runtime.
 *
 * Extension function for application calls that parse query parameters to a Kotlin data class.
 * Note, each parameter must be parsable from a string, and the class being populated must have a
 * null-constructor (a constructor with no parameters). Additionally, any field being populated with
 * this (i.e. annotated with [QueryParameter]) must be `var`.
 */
fun <T : Any> ApplicationCall.query(clazz: KClass<T>): T {
  val query = request.queryParameters

  // Get fields and null-constructor
  val fields = clazz.declaredMemberProperties
  val ctor = clazz.constructors.find { it.parameters.isEmpty() }

  if (ctor == null) {
    throw IllegalArgumentException("class $clazz does not have null constructor")
  }

  val instance = ctor.call()

  for (f in fields) {
    val field = f as? KMutableProperty<*> ?: continue

    val qp = field.findAnnotations(QueryParameter::class)

    if (qp.isEmpty()) {
      continue
    }

    val key = qp.first().key.ifBlank { field.name }

    val value = query[key]

    if (value != null) {
      field.isAccessible = true
      setFieldValue(field, instance, value)
    }
  }

  return instance
}

/** Shorthand for indexing into the request's queryParameters map. */
fun ApplicationCall.queryParam(key: String): String? = request.queryParameters[key]

// Helper function to set the field value, handling basic type conversions
private fun setFieldValue(field: KMutableProperty<*>, instance: Any, value: String) {
  val fieldType = field.returnType.classifier as KClass<*>

  // Convert the query parameter string to the appropriate field type
  val convertedValue: Any? =
    when (fieldType) {
      Int::class -> value.toIntOrNull()
      Long::class -> value.toLongOrNull()
      Float::class -> value.toFloatOrNull()
      Double::class -> value.toDoubleOrNull()
      Boolean::class -> value.toBoolean()
      String::class -> value

      // Custom application types
      SortOrder::class -> if (value == "asc") SortOrder.ASC else SortOrder.DESC
      KLocalDate::class ->
        try {
          KLocalDate.parse(value)
        } catch (e: Exception) {
          LOG.error("Failed to parse date: $value", e)
          null
        }
      KOffsetDateTime::class ->
        try {
          KOffsetDateTime.parse(value)
        } catch (e: Exception) {
          LOG.error("Failed to parse date-time: $value", e)
          null
        }
      else -> null
    }

  if (convertedValue != null) {
    field.setter.call(instance, convertedValue)
  }
}
