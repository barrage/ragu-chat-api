package net.barrage.llmao.app.workflow.chat

import net.barrage.llmao.core.Api
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.ChatWorkflowBase
import net.barrage.llmao.core.chat.ProcessedMessageGroup
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.tryUuid
import net.barrage.llmao.types.KUUID

/** Implementation of a [ChatWorkflowBase] for user-created agents. */
class ChatWorkflow(
  id: KUUID,
  user: User,
  emitter: Emitter,
  toolchain: Toolchain<Api>?,
  override val agent: ChatAgent,
  private val repository: ChatRepositoryWrite,

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,
) :
  ChatWorkflowBase<Api>(
    id = id,
    user = user,
    emitter = emitter,
    toolchain = toolchain,
    agent = agent,
  ) {
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
        emitter.emit(
          ChatWorkflowMessage.ChatTitleUpdated(id, title),
          ChatWorkflowMessage.serializer(),
        )
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
