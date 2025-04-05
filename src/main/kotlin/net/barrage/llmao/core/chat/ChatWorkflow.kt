package net.barrage.llmao.core.chat

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.tryUuid

class ChatWorkflow(
  id: KUUID,
  user: User,
  emitter: Emitter<ChatWorkflowMessage>,
  messageProcessor: ChatMessageProcessor,
  override val agent: ChatAgent,
  private val repository: ChatRepositoryWrite,

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,
) :
  ConversationWorkflow(
    id = id,
    user = user,
    emitter = emitter,
    agent = agent,
    messageProcessor = messageProcessor,
  ) {
  override suspend fun onInteractionComplete(
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup {
    val originalPrompt = userMessage.content!!.text()
    val attachmentsInsert = attachments?.let { messageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)

    val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

    val assistantMessage = messages.last()
    assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
    assert(assistantMessage.content != null) { "Last assistant message must have content" }

    return when (state) {
      ChatWorkflowState.New -> {
        log.debug("{} - persisting chat with message pair", id)

        val groupId =
          repository.insertChatWithMessages(
            chatId = id,
            userId = user.id,
            username = user.username,
            agentId = tryUuid(agent.id()),
            agentConfigurationId = agent.configurationId,
            messages = messagesInsert,
          )

        val title = agent.createTitle(originalPrompt, assistantMessage.content!!.text())

        log.debug("{} - generated title ({})", id, title)

        repository.updateTitle(id, user.id, title)
        emitter.emit(ChatWorkflowMessage.ChatTitleUpdated(id, title))
        state = ChatWorkflowState.Persisted(title)

        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
      is ChatWorkflowState.Persisted -> {
        log.debug("{} - persisting message pair", id)
        val groupId =
          repository.insertMessages(
            chatId = id,
            agentConfigurationId = agent.configurationId,
            messages = messagesInsert,
          )
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
