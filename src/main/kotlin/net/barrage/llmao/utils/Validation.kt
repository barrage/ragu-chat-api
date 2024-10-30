package net.barrage.llmao.utils

import io.ktor.server.plugins.requestvalidation.*
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.functions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Valid only on `String` fields. Validate the string is not blank. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class NotBlank(
  val code: String = "notBlank",
  val message: String = "Value cannot be blank",
)

/** Valid only on `String` fields. Validate the string is a correct email. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Email(
  val code: String = "email",
  val message: String = "Value is not valid email",
)

/**
 * Valid only on `Int` | `Long` | `Float` | `Double` fields. Validate the number falls within the
 * specified range.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Range(
  val min: Double = Double.MAX_VALUE * -1,
  val max: Double = Double.MAX_VALUE,
  val code: String = "range",
  val message: String = "",
)

/** Valid only on `String` fields. Validate that it's a number. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Number(
  val code: String = "number",
  val message: String = "Value is not valid number",
)

/**
 * Valid on `String` fields. Validate that the character length falls within the specified range.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class CharRange(
  val min: Int = 0,
  val max: Int = Int.MAX_VALUE,
  val code: String = "charRange",
  val message: String = "",
)

/**
 * Validate the data object as a whole.
 *
 * The provided method must exist on the data class, its return type must be
 * `List<ValidationError>`, and ideally should never throw.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaValidation(val fn: String)

/**
 * Auto-implements the `validate` method based on annotations used.
 *
 * We do not have an annotation processor so we must be careful to use these on the correct types,
 * **especially** with schema validation functions.
 */
interface Validation {
  fun validate(): ValidationResult {
    val clazz = this::class

    val errors = mutableListOf<ValidationError>()

    val schemaValidation = clazz.findAnnotations<SchemaValidation>().firstOrNull()

    schemaValidation?.let { schema ->
      // Throws an exception if the function does not exist.
      val fn = clazz.functions.first { it.name == schema.fn }
      val schemaErrors = fn.call(this) as List<*>
      for (error in schemaErrors) {
        errors.add(error as ValidationError)
      }
    }

    val fields = clazz.declaredMemberProperties

    for (field in fields) {
      for (annotation in field.annotations) {
        val error = validateInternal(this, field, annotation)
        error?.let { errors.addAll(it) }
      }
    }

    return if (errors.isEmpty()) {
      ValidationResult.Valid
    } else {
      ValidationResult.Invalid(errors.map { Json.encodeToString(it) })
    }
  }
}

/** Validation dirty work. All downstream functions return `true` if the validation passed */
private fun validateInternal(
  instance: Validation,
  field: KProperty<*>,
  annotation: Annotation,
): List<ValidationError>? {

  // Grab field metadata
  val fieldName = field.name

  // Calling a getter never throws since it never takes in any
  // parameters and is auto-generated by Kotlin.
  // Skip validating null fields
  val value = field.getter.call(instance) ?: return null

  when (annotation) {
    is NotBlank -> {
      return if (validateNotBlank(value as String)) {
        null
      } else {
        listOf(ValidationError(annotation.code, annotation.message, fieldName))
      }
    }
    is Email -> {
      return if (validateEmail(value as String)) {
        null
      } else {
        listOf(ValidationError(annotation.code, annotation.message, fieldName))
      }
    }
    is Number -> {
      return if (validateNumber(value as String)) {
        null
      } else {
        listOf(ValidationError(annotation.code, annotation.message, fieldName))
      }
    }
    is Range -> {
      return if (validateRange(value, annotation.min, annotation.max)) {
        null
      } else {
        val message =
          annotation.message.ifBlank {
            "Value must be in range ${annotation.min} - ${annotation.max}"
          }
        listOf(ValidationError(annotation.code, message, fieldName))
      }
    }
    is CharRange -> {
      return if (validateCharRange(value as String, annotation.min, annotation.max)) {
        null
      } else {
        val message =
          annotation.message.ifBlank {
            "Value must be between ${annotation.min} - ${annotation.max} characters long"
          }
        listOf(ValidationError(annotation.code, message, fieldName))
      }
    }
    else -> {
      return null
    }
  }
}

@Serializable
data class ValidationError(
  /** Validator code. */
  val code: String,

  /** Validation failure description. */
  val message: String,

  /** Name of the field that failed validation. */
  val fieldName: String? = null,
)

fun MutableList<ValidationError>.addSchemaErr(code: String = "schema", message: String) {
  add(ValidationError(code, message))
}

private fun validateEmail(email: String): Boolean {
  return email.isNotBlank() && email.matches(Regex("^[\\w\\-.]+@([\\w-]+\\.)+[\\w-]{2,4}$"))
}

private fun validateNumber(number: String): Boolean {
  return number.isNotBlank() && number.matches(Regex("^[0-9]+$"))
}

private fun validateNotBlank(param: String): Boolean {
  return param.isNotBlank()
}

private fun validateRange(input: Any, min: Double, max: Double): Boolean {
  return when (input) {
    is Int -> input.toDouble() in min..max
    is Long -> input.toDouble() in min..max
    is Float -> input.toDouble() in min..max
    is Double -> input in min..max
    else -> false
  }
}

private fun validateCharRange(input: String, min: Int, max: Int): Boolean {
  return input.length in min..max
}
