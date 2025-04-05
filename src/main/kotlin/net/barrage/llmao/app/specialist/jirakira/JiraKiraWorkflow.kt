package net.barrage.llmao.app.specialist.jirakira

import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.ChatWorkflowMessage
import net.barrage.llmao.core.chat.ConversationWorkflow
import net.barrage.llmao.core.chat.ProcessedMessageGroup
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.specialist.SpecialistRepositoryWrite
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

class JiraKiraWorkflow(
  id: KUUID,
  user: User,
  emitter: Emitter<ChatWorkflowMessage>,
  messageProcessor: ChatMessageProcessor,
  override val agent: JiraKira,
  private val repository: SpecialistRepositoryWrite,
) :
  ConversationWorkflow(
    id = id,
    user = user,
    agent = agent,
    emitter = emitter,
    messageProcessor = messageProcessor,
    streamingEnabled = false,
  ) {
  private var state: JiraKiraWorkflowState = JiraKiraWorkflowState.New

  override suspend fun onInteractionComplete(
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup {
    val attachmentsInsert = attachments?.let { messageProcessor.storeMessageAttachments(it) }
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
