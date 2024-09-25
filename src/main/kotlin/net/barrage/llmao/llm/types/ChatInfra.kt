package net.barrage.llmao.llm.types

import com.knuddels.jtokkit.api.Encoding
import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.models.VectorQueryOptions
import net.barrage.llmao.weaviate.Weaver
import net.barrage.llmao.websocket.Emitter

class ChatInfra(
  val llm: ConversationLlm,
  val formatter: PromptFormatter,
  val emitter: Emitter? = null,
  val vectorDb: Weaver,
  val vectorOptions: VectorQueryOptions,
  val encoder: Encoding,
)
