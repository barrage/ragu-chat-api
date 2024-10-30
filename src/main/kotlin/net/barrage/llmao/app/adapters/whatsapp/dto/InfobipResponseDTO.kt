package net.barrage.llmao.app.adapters.whatsapp.dto

import kotlinx.serialization.Serializable

/** Request received on the Infobip webhook. */
@Serializable
data class InfobipResponseDTO(
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
data class Message(
  /** Message content. */
  val text: String,
  /** We are mostly interested in the 'TEXT' type. */
  val type: InfobipMessageType,
)

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

enum class InfobipMessageType {
  ORDER,
  UNSUPPORTED,
  TEXT,
  LOCATION,
  IMAGE,
  DOCUMENT,
  AUDIO,
  VIDEO,
  VOICE,
  CONTACT,
  INFECTED_CONTENT,
  BUTTON,
  STICKER,
  INTERACTIVE_BUTTON_REPLY,
  INTERACTIVE_LIST_REPLY,
  INTERACTIVE_FLOW_REPLY,
  INTERACTIVE_PAYMENT_CONFIRMATION,
}
