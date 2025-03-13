package net.barrage.llmao.core.storage

import net.barrage.llmao.core.model.Image

interface ImageStorage {
  /** Store the given image object. */
  fun store(image: Image)

  /**
   * Retrieve the bytes for the given image name. If found, the image object will have the same name
   * as the input.
   */
  fun retrieve(name: String): Image?

  /** Delete the file for given name. */
  fun delete(name: String)

  /** Check if the given path exists. */
  fun exists(path: String): Boolean
}
