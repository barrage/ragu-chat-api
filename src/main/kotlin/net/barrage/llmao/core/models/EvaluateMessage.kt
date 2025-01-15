package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.tables.records.MessageEvaluationsRecord

@Serializable data class EvaluateMessage(val evaluation: Boolean?, val feedback: String? = null)

fun MessageEvaluationsRecord.toEvaluateMessage() =
  EvaluateMessage(evaluation = this.evaluation, feedback = this.feedback)
