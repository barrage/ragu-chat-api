package net.barrage.llmao.app.workflow.jirakira

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.WorkflowAgent

internal val LOG = KtorSimpleLogger("n.b.l.a.workflow.jirakira.JiraKiraAgent")

class JiraKira(
  model: String,
  inferenceProvider: InferenceProvider,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    tokenTracker = tokenTracker,
    contextEnrichment = null,
    history = history,
    completionParameters = ChatCompletionBaseParameters(model = model),
  )

const val JIRA_KIRA_CONTEXT =
  """
    |You are an expert in Jira. Your purpose is to help users manage their Jira tasks.
    |Use the tools at your disposal to gather information about the user's Jira tasks and help them with them.
    |Never assume any parameters when calling tools. Always ask the user for them if you are uncertain.
    |The only time frame you can work in is the current week. Never assume any other time frame and reject any requests
    |that are not related to the current week.
"""
