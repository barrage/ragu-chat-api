package net.barrage.llmao.core.input.whatsapp.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Request received on the Infobip webhook used for WhatsApp.
 *
 * TODO: Check if it should be moved to adapters, if/when we configure multiple input adapters.
 */
@Serializable
data class InfobipResponse(
  /** Holds the messages. Usually only one message will be contained in this. */
  val results: List<InfobipResult>,
  /** How many messages are in this request. */
  val messageCount: Int,
  /** The number of messages that have not been pulled in. */
  val pendingMessageCount: Int,
)

@Serializable
data class InfobipResult(
  /** Phone number of the sender, i.e. the user. */
  val from: String,
  /** Phone number of the receiver, i.e. the Infobip agent (Infobip sender). */
  val to: String,
  /** Integration type of the Infobip service. In our case this will mostly be 'WHATSAPP'. */
  val integrationType: String,
  /**
   * Date of receiving the message.
   *
   * Format: 'YYYY-MM-DDThh:mm:ss.xxx+zzzz'
   */
  val receivedAt: String,
  /** Infobip specific message ID. */
  val messageId: String,
  /** If the message is related to another one, this will be the ID of the related message. */
  val pairedMessageId: String?,
  /** Not really sure about this one. :) */
  val callbackData: String?,
  /** The actual message sent from WhatsApp. */
  val message: Message,
  /** Contact info. */
  val contact: Contact,
  /** Price of the message. */
  val price: Price,
)

@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
sealed class Message {
  @Serializable @SerialName("TEXT") data class Text(val text: String) : Message()

  @Serializable
  @SerialName("INTERACTIVE_BUTTON_REPLY")
  data class ButtonReply(val id: String, val title: String) : Message()
}

@Serializable
data class Contact(
  /** Username of the sender of this message. */
  val name: String
)

@Serializable
data class Price(
  /** Price per message. */
  val pricePerMessage: Double,
  /** Currency of the price. */
  val currency: String,
)
