package net.barrage.llmao.core.llm

import io.ktor.util.encodeBase64
import io.ktor.util.logging.KtorSimpleLogger
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.llm.ChatMessageProcessor.storeMessageAttachments
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.Message
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.MessageAttachmentType

private val LOG = KtorSimpleLogger("n.b.l.c.llm.ChatMessageProcessor")

/**
 * Responsible for converting native chat message attachments such as storage and retrieval.
 *
 * TODO: For now, we are always loading images directly into memory whenever chat histories are
 *   loaded (they are not sent to the client, only the URLs are sent). This should be modified such
 *   that images are loaded only on inference, and a caching mechanism should be implemented to
 *   reduce the memory footprint of chats.
 */
object ChatMessageProcessor {
  private lateinit var providers: ProviderState

  /** Maximum image size supported. We default to 20MB. */
  private var maxImageSizeMB: Int = 20

  fun init(providers: ProviderState) {
    this.providers = providers
  }

  /**
   * Converts the incoming prompt and attachments into a [ContentMulti]. This is done to preserve
   * the original as it is needed for [storeMessageAttachments] later, since we prompt first and
   * only store images on success.
   */
  fun toContentMulti(text: String?, attachments: List<IncomingMessageAttachment>): ContentMulti {
    val contentParts =
      text?.let { mutableListOf<ChatMessageContentPart>(ChatMessageContentPart.Text(it)) }
        ?: mutableListOf()
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
                // or the URI is malformed.
                val image = Image.fromBase64Uri(attachment.data.data)

                if (image.data.size > maxImageSizeMB * 1024 * 1024) {
                  throw AppError.api(
                    ErrorReason.PayloadTooLarge,
                    "Image size exceeds the maximum allowed size",
                  )
                }

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
              val image = Image.fromBase64Uri(attachment.data.data)

              val hash = MessageDigest.getInstance("SHA-256").digest(image.data).toHexString()

              val path = "$hash.${image.type}"

              val attachmentPath = "$ATTACHMENTS_PATH/$path"

              if (!providers.image.exists(attachmentPath)) {
                LOG.info("Storing image at $attachmentPath")
                providers.image.store(attachmentPath, image)
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
