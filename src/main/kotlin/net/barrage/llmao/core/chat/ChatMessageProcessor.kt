package net.barrage.llmao.core.chat

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageContentPart
import net.barrage.llmao.core.llm.ChatMessageImage
import net.barrage.llmao.core.llm.ContentMulti
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.ImageType
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.Message
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.MessageAttachmentType
import net.barrage.llmao.core.storage.ATTACHMENTS_PATH

private val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.chat.ChatMessageProcessor")

/**
 * Responsible for converting native chat message attachments such as storage and retrieval.
 *
 * TODO: For now, we are always loading images directly into memory whenever chat histories are
 *   loaded (they are not sent to the client, only the URLs are sent). This should be modified such
 *   that images are loaded only on inference, and a caching mechanism should be implemented to
 *   reduce the memory footprint of chats.
 */
class ChatMessageProcessor(private val providers: ProviderState) {
  /**
   * Converts the incoming prompt and attachments into a [ContentMulti]. This is done to preserve
   * the original as it is needed for [storeMessageAttachments] later, since we prompt first and
   * only store images on success.
   */
  fun toContentMulti(text: String, attachments: List<IncomingMessageAttachment>): ContentMulti {
    val contentParts = mutableListOf<ChatMessageContentPart>(ChatMessageContentPart.Text(text))
    val content =
      attachments.fold(contentParts) { acc, attachment ->
        when (attachment) {
          is IncomingMessageAttachment.Image ->
            when (attachment.data) {
              is IncomingImageData.Url ->
                acc.add(
                  ChatMessageContentPart.Image(
                    imageUrl = ChatMessageImage(url = attachment.data.url, detail = "high")
                  )
                )
              is IncomingImageData.Raw -> {
                // Throws if the image type is not supported
                ImageType.fromContentType(
                  attachment.data.data.substringBefore(";").substringAfter("data:")
                )
                acc.add(
                  ChatMessageContentPart.Image(
                    imageUrl = ChatMessageImage(url = attachment.data.data, detail = "high")
                  )
                )
              }
            }
        }
        acc
      }

    return ContentMulti(content)
  }

  /**
   * Process message attachments.
   *
   * For raw images, we store them to the BLOB storage provider and return the path to the image.
   * Only the image data (base64 bytes) is stored, not the full URI.
   *
   * Image URLs are just included in the final output, as they can be loaded on the client and are
   * processed by gippities internally.
   */
  @OptIn(ExperimentalStdlibApi::class)
  fun storeMessageAttachments(input: List<IncomingMessageAttachment>): List<MessageAttachment> {
    val processedAttachments = mutableListOf<MessageAttachment>()

    for ((index, attachment) in input.withIndex()) {
      when (attachment) {
        is IncomingMessageAttachment.Image -> {
          when (attachment.data) {
            is IncomingImageData.Url -> {
              processedAttachments.add(
                MessageAttachment(
                  type = MessageAttachmentType.IMAGE_URL,
                  provider = null,
                  order = index,
                  url = attachment.data.url,
                )
              )
            }
            is IncomingImageData.Raw -> {
              val (type, data) =
                with(attachment.data.data) {
                  val split = split(";")

                  if (split.size != 2) {
                    throw AppError.api(ErrorReason.InvalidParameter, "Invalid image URI")
                  }

                  val (type, data) = split

                  if (!data.startsWith("base64,")) {
                    throw AppError.api(
                      ErrorReason.InvalidParameter,
                      "Only base64 images are supported",
                    )
                  }

                  Pair(
                    ImageType.fromContentType(type.substringAfter("data:")),
                    data.substringAfter("base64,").decodeBase64Bytes(),
                  )
                }

              val hash = MessageDigest.getInstance("SHA-256").digest(data).toHexString()

              val path = "$hash.$type"

              val attachmentPath = "$ATTACHMENTS_PATH/$path"

              if (!providers.image.exists(attachmentPath)) {
                LOG.info("Storing image at $attachmentPath")
                providers.image.store(attachmentPath, Image(data = data, type = type))
              }

              processedAttachments.add(
                MessageAttachment(
                  type = MessageAttachmentType.IMAGE_RAW,
                  provider = providers.image.id(),
                  order = index,
                  url = path,
                )
              )
            }
          }
        }
      }
    }

    return processedAttachments
  }

  /** Convert a [Message] to a [ChatMessage], loading any message attachments from BLOB storage. */
  fun loadToChatMessage(model: Message): ChatMessage {
    val attachments = model.attachments?.let(::loadMessageAttachments)

    val content =
      if (attachments != null) {
        assert(model.senderType == "user") { "Only user messages can have attachments" }
        assert(model.content != null) { "User messages with attachments must have content" }
        ContentMulti(listOf(ChatMessageContentPart.Text(model.content!!)) + attachments)
      } else {
        model.content?.let(::ContentSingle)
      }

    return ChatMessage(
      role = model.senderType,
      content = content,
      toolCalls = model.toolCalls?.let { Json.decodeFromString(it) },
      toolCallId = model.toolCallId,
      finishReason = model.finishReason,
    )
  }

  private fun loadMessageAttachments(
    attachments: List<MessageAttachment>
  ): List<ChatMessageContentPart> =
    attachments
      .map { attachment ->
        when (attachment.type) {
          MessageAttachmentType.IMAGE_URL -> {
            ChatMessageContentPart.Image(ChatMessageImage(attachment.url))
          }
          MessageAttachmentType.IMAGE_RAW -> {
            if (attachment.provider != providers.image.id()) {
              LOG.warn("Attempted to load image from unsupported provider: ${attachment.provider}")
              return@map null
            }

            val path = "$ATTACHMENTS_PATH/${attachment.url}"

            val image = providers.image.retrieve(path)

            if (image == null) {
              LOG.warn("Failed to load image at $path")
              return@map null
            }

            ChatMessageContentPart.Image(
              ChatMessageImage(
                url = "data:${image.type.contentType()};base64,${image.data.encodeBase64()}"
              )
            )
          }
        }
      }
      .filterNotNull()
}
