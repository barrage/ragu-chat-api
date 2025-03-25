package net.barrage.llmao.core.model.common

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Data class to update **nullable** properties. Should be used only on primitives.
 *
 * If you need to update **required** properties, use a nullable primitive directly. For example, if
 * you need to update a required string property, define the payload as
 *
 * ```json
 * data class Update(val foo: String? = null)
 * ```
 *
 * If this class is being used as a type, it should be nullable and the default value should be
 * [Undefined].
 *
 * ```json
 * data class Update(val update: PropertyUpdate<String>? = PropertyUpdate.Undefined)
 * ```
 *
 * The semantics of the update payload are as follows:
 * - update == null -> Set property to null
 * - update is Undefined -> Leave property as is
 * - update is Value -> Set property to value
 *
 * **Serialization caveat**
 *
 * When serializing (encoding) this in tests, we have to omit the `Undefined` value. Since
 * serializers delegate to this serializer only on *value* creation, we need to annotate
 * [PropertyUpdate] fields with `@EncodeDefault(EncodeDefault.Mode.NEVER)`.
 */
@Serializable(with = PropertyUpdateSerializer::class)
sealed class PropertyUpdate<out T> {
  data object Undefined : PropertyUpdate<Nothing>()

  data class Value<T>(val value: T) : PropertyUpdate<T>()

  fun value(): T? {
    return when (this) {
      is Value -> value
      else -> null
    }
  }
}

class PropertyUpdateSerializer<T>(private val classSerializer: KSerializer<T>) :
  KSerializer<PropertyUpdate<T>> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("PropertyUpdate", classSerializer.descriptor)

  override fun serialize(encoder: Encoder, value: PropertyUpdate<T>) =
    // We serialize this only in tests
    when (value) {
      is PropertyUpdate.Value -> classSerializer.serialize(encoder, value.value)
      is PropertyUpdate.Undefined -> {}
    }

  override fun deserialize(decoder: Decoder): PropertyUpdate<T> {
    return classSerializer.deserialize(decoder).let { PropertyUpdate.Value(it) }
  }
}
