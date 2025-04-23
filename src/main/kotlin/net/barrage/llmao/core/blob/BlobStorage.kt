package net.barrage.llmao.core.blob

import net.barrage.llmao.core.Identity

const val AVATARS_PATH = "avatars"
const val ATTACHMENTS_PATH = "attachments"

interface BlobStorage<T> : Identity {

  /**
   * Store the given data.
   *
   * @param path The path to store the BLOB at, provider specific.
   */
  fun store(path: String, data: T)

  /** Retrieve the bytes for the given BLOB path. */
  fun retrieve(path: String): T?

  /** Delete the data for given name. */
  fun delete(path: String)

  /** Check if the BLOB exists at the given path. */
  fun exists(path: String): Boolean
}
