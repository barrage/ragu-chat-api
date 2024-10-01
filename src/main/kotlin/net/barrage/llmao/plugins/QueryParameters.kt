package net.barrage.llmao.plugins

import io.ktor.server.application.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.isAccessible
import net.barrage.llmao.models.SortOrder

/** Annotate class fields you want to include for parsing with [query]. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class QueryParameter(val key: String = "")

/**
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

    if (qp.isEmpty()) continue

    val key = qp.first().key.ifBlank { field.name }

    val value = query[key]

    if (value != null) {
      field.isAccessible = true
      setFieldValue(field, instance, value)
    }
  }

  return instance
}

// Helper function to set the field value, handling basic type conversions
private fun setFieldValue(field: KMutableProperty<*>, instance: Any, value: String) {
  val fieldType = field.returnType.classifier as KClass<*>

  // Convert the query parameter string to the appropriate field type
  val convertedValue: Any? =
    when (fieldType) {
      Int::class.java -> value.toIntOrNull()
      Long::class.java -> value.toLongOrNull()
      Float::class.java -> value.toFloatOrNull()
      Double::class.java -> value.toDoubleOrNull()
      Boolean::class.java -> value.toBoolean()
      String::class.java -> value

      // Custom application types
      SortOrder::class -> if (value == "asc") SortOrder.ASC else SortOrder.DESC
      else -> null
    }

  if (convertedValue != null) {
    field.setter.call(instance, convertedValue)
  }
}
