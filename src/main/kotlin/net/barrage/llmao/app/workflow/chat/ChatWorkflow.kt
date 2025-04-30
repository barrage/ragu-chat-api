package net.barrage.llmao.app.workflow.chat

import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.ProcessedMessageGroup
import net.barrage.llmao.core.workflow.WorkflowBasic
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.types.KUUID

/** Implementation of a [WorkflowBasic] for user-created agents. */
class ChatWorkflow(
  id: KUUID,
  user: User,
  emitter: Emitter,
  tools: Tools?,
  override val agent: ChatAgent,
  private val repository: ChatRepositoryWrite,

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,
) : WorkflowBasic(id = id, user = user, emitter = emitter, agent = agent) {
  fun agentId(): KUUID {
    return agent.agentId
  }

  override suspend fun onInteractionComplete(
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup {
    val originalPrompt = userMessage.content!!.text()
    val attachmentsInsert = attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)

    val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

    val assistantMessage = messages.last()
    assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
    assert(assistantMessage.content != null) { "Last assistant message must have content" }

    return when (state) {
      ChatWorkflowState.New -> {
        val groupId =
          repository.insertChatWithMessages(
            chatId = id,
            userId = user.id,
            username = user.username,
            agentId = agent.agentId,
            agentConfigurationId = agent.configurationId,
            messages = messagesInsert,
          )

        val title = agent.createTitle(originalPrompt, assistantMessage.content!!.text())

        repository.updateTitle(id, user.id, title)
        emitter.emit(ChatTitleUpdated(id, title), WorkflowOutput.serializer())
        state = ChatWorkflowState.Persisted(title)

        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
      is ChatWorkflowState.Persisted -> {
        val groupId = repository.insertMessages(chatId = id, messages = messagesInsert)
        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
    }
  }
}

sealed class ChatWorkflowState {
  /** State when a chat is created from scratch. It has no title and received no messages. */
  data object New : ChatWorkflowState()

  /**
   * State when a chat is persisted in the database. It has a title and received at least one
   * message.
   */
  data class Persisted(val title: String) : ChatWorkflowState()
}
