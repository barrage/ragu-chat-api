package net.barrage.llmao.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.barrage.llmao.websocket.*

class C2SMessageSerializer : KSerializer<C2SMessage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("C2SMessage") {
        element("userId", KUUIDSerializer.descriptor)
        element("type", C2SMessageType.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): C2SMessage {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        val base = jsonDecoder.decodeJsonElement().jsonObject
        val type = base["type"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("No type found")
        return when (type) {
            "chat" -> {
                jsonDecoder.json.decodeFromJsonElement(C2SChatMessage.serializer(), base)
            }

            "system" -> {
                val header = base["payload"]?.jsonObject?.get("header")?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("No header found")
                when (header) {
                    "open_chat" -> jsonDecoder.json.decodeFromJsonElement(C2SServerMessageOpenChat.serializer(), base)
                    "close_chat" -> jsonDecoder.json.decodeFromJsonElement(C2SServerMessageCloseChat.serializer(), base)
                    "stop_stream" -> jsonDecoder.json.decodeFromJsonElement(
                        C2SServerMessageStopStream.serializer(),
                        base
                    )

                    else -> throw IllegalArgumentException("Unknown header")
                }
            }

            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun serialize(encoder: Encoder, value: C2SMessage) {
        when (value) {
            is C2SChatMessage -> C2SChatMessage.serializer().serialize(encoder, value)
            is C2SServerMessageOpenChat -> C2SServerMessageOpenChat.serializer().serialize(encoder, value)
            is C2SServerMessageCloseChat -> C2SServerMessageCloseChat.serializer().serialize(encoder, value)
            is C2SServerMessageStopStream -> C2SServerMessageStopStream.serializer().serialize(encoder, value)
        }
    }
}