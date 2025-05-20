package net.barrage.llmao.types

import java.time.OffsetTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias KOffsetTime = @Serializable(with = OffsetTimeSerializer::class) OffsetTime

object OffsetTimeSerializer : KSerializer<OffsetTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: OffsetTime) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): OffsetTime {
    val string = decoder.decodeString()
    return OffsetTime.parse(string)
  }
}
