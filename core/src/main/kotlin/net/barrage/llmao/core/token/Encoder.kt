package net.barrage.llmao.core.token

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding

object Encoder {
  private val tokenizer = Encodings.newDefaultEncodingRegistry()

  fun tokenizer(model: String): Encoding? {
    val t = tokenizer.getEncodingForModel(model)
    return if (t.isEmpty) null else t.get()
  }
}
