package net.barrage.llmao.llm.types

import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Languages
import net.barrage.llmao.serializers.KUUID

@Serializable
class ChatConfig(
  val id: KUUID? = null,
  val userId: KUUID,
  var agentId: Int,
  var title: String? = null,
  val maxHistory: Int? = null,
  val summarizeAfterTokens: Int? = null,
  var language: Languages = Languages.CRO,
  var messageReceived: Boolean? = null,
)
