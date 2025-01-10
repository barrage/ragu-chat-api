package net.barrage.llmao.core.storage

import net.barrage.llmao.core.models.Image
import net.barrage.llmao.core.types.KUUID

interface ImageStorage {
  /** Store the given bytes at the given path. */
  fun store(id: KUUID, imageFormat: String, bytes: ByteArray): Image

  /** Retrieve the bytes at the given path. */
  fun retrieve(id: KUUID): Image?

  /** Delete the file for given ID. */
  fun delete(id: KUUID)

  /** Check if the given path exists. */
  fun exists(path: String): Boolean

  /** Format the path for the given ID. */
  fun formatPath(id: KUUID): String?
}
