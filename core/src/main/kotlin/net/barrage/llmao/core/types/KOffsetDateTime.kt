package net.barrage.llmao.core.types

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Shorthand for [OffsetDateTime] with a custom serializer. */
typealias KOffsetDateTime = @Serializable(with = OffsetDateTimeSerializer::class) OffsetDateTime

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: OffsetDateTime) {
    encoder.encodeString(value.atZoneSameInstant(ZoneOffset.UTC).toString())
  }

  override fun deserialize(decoder: Decoder): OffsetDateTime {
    val string = decoder.decodeString()
    // Handle cases where the offset is in +NNNN format instead of +NN:NN
    val normalized =
      string.replace(Regex("\\+(\\d{4})$")) { matchResult ->
        val offset = matchResult.groupValues[1]
        "+${offset.substring(0, 2)}:${offset.substring(2, 4)}"
      }
    return OffsetDateTime.parse(normalized)
  }
}

fun KOffsetDateTime(localDate: KLocalDate, offsetTime: KOffsetTime): KOffsetDateTime {
  return KOffsetDateTime.of(localDate, offsetTime.toLocalTime(), offsetTime.offset)
}
