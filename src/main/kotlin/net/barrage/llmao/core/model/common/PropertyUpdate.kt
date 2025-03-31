package net.barrage.llmao.core.model.common

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull

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
 * If this class is being used as a type, it should never be nullable and the default value should
 * be [Undefined].
 *
 * ```json
 * data class Update(val update: PropertyUpdate<String> = PropertyUpdate.Undefined)
 * ```
 *
 * When clients send the value as `null`, it will be deserialized into [Null] and if they send a
 * value it will be deserialized into [Value].
 *
 * If clients omit the field, anything that uses this as a type is required to instantiate this to
 * [Undefined] as the update semantics rely on it.
 *
 * The semantics of the update payload are as follows:
 * - update == Null -> Set property to null
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
  /** All PropertyUpdate fields should be instantiated to this. */
  data object Undefined : PropertyUpdate<Nothing>()

  /** If the clients send explicit nulls, this will be the instance. */
  data object Null : PropertyUpdate<Nothing>()

  /** If the clients send a value, this will be the instance. */
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

  @OptIn(ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: PropertyUpdate<T>) {
    val encoder = encoder as? JsonEncoder ?: throw SerializationException("Only JSON is supported")
    // We serialize this only in tests
    when (value) {
      is PropertyUpdate.Value -> encoder.encodeSerializableValue(classSerializer, value.value)
      is PropertyUpdate.Null -> encoder.encodeNull()
      is PropertyUpdate.Undefined -> {}
    }
  }

  // This will only run if the field is defined.
  override fun deserialize(decoder: Decoder): PropertyUpdate<T> {
    val decoder = decoder as? JsonDecoder ?: throw SerializationException("Only JSON is supported")
    val element = decoder.decodeJsonElement()
    return if (element is JsonNull) {
      PropertyUpdate.Null
    } else {
      val value = Json.decodeFromJsonElement(classSerializer, element)
      PropertyUpdate.Value(value)
    }
  }
}
