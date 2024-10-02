package net.barrage.llmao.core.models

/**
 * A document chunk represents partial content of a document. It can additionally contain some
 * metadata if it is possible to extract it, see [DocumentChunkMeta].
 */
data class DocumentChunk(val content: String, val metadata: DocumentChunkMeta)

data class DocumentChunkMeta(
  /** Name of the file the document was read from. */
  val fileName: String,
  /** The type of the original file */
  val fileType: FileType,
)

fun DocumentChunkMeta.toMap(): Map<String, Any> {
  return mapOf("fileName" to fileName, "fileType" to fileType.name)
}

enum class FileType {
  PDF,
  DOCX,
  TXT,
  JSON,
  CSV,
  MD,
}
