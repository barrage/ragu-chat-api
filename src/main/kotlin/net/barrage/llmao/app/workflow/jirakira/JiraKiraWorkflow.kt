package net.barrage.llmao.app.workflow.jirakira

import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.workflow.ChatWorkflowBase
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.ProcessedMessageGroup
import net.barrage.llmao.types.KUUID

class JiraKiraWorkflow(
  id: KUUID,
  user: User,
  emitter: Emitter,
  toolchain: Toolchain<JiraKiraState>,
  override val agent: JiraKira,
  private val repository: JiraKiraRepository,
) :
  ChatWorkflowBase<JiraKiraState>(
    id = id,
    user = user,
    agent = agent,
    emitter = emitter,
    toolchain = toolchain,
    streamingEnabled = false,
  ) {
  private var state: JiraKiraWorkflowState = JiraKiraWorkflowState.New

  override suspend fun onToolCalls(toolCalls: List<ToolCallData>): List<ChatMessage>? {
    val results = mutableListOf<ChatMessage>()
    for (toolCall in toolCalls) {
      emitter.emit(ToolEvent.ToolCall(toolCall), ToolEvent.serializer())
      val result =
        try {
          toolchain!!.processToolCall(toolCall)
        } catch (e: Throwable) {
          if (e is JiraError) {
            LOG.error("Jira API error:", e)
          } else {
            LOG.error("Error in tool call", e)
          }
          "error: ${e.message}"
        }
      emitter.emit(ToolEvent.ToolResult(result), ToolEvent.serializer())
      results.add(ChatMessage.toolResult(result, toolCall.id))
    }
    return results
  }

  override suspend fun onInteractionComplete(
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup {
    val attachmentsInsert = attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)

    val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

    val assistantMessage = messages.last()
    assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
    assert(assistantMessage.content != null) { "Last assistant message must have content" }

    return when (state) {
      JiraKiraWorkflowState.New -> {
        log.debug("{} - persisting chat with message pair", id)

        val groupId =
          repository.insertWorkflowWithMessages(
            workflowId = id,
            userId = user.id,
            username = user.username,
            messages = messagesInsert,
          )

        state = JiraKiraWorkflowState.Persisted

        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
      is JiraKiraWorkflowState.Persisted -> {
        log.debug("{} - persisting message pair", id)
        val groupId = repository.insertMessages(workflowId = id, messages = messagesInsert)
        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
    }
  }
}

private sealed class JiraKiraWorkflowState {
  data object New : JiraKiraWorkflowState()

  data object Persisted : JiraKiraWorkflowState()
}
