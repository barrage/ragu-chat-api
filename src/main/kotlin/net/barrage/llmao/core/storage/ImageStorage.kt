package net.barrage.llmao.core.storage

import net.barrage.llmao.core.models.Image

interface ImageStorage {
  /** Store the given bytes at the given path. */
  fun store(imageName: String, bytes: ByteArray): String

  /** Retrieve the bytes at the given path. */
  fun retrieve(imageName: String): Image?

  /** Delete the file for given name. */
  fun delete(name: String)

  /** Check if the given path exists. */
  fun exists(path: String): Boolean
}
