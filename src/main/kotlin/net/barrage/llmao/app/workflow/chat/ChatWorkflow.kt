package net.barrage.llmao.app.workflow.chat

import kotlinx.coroutines.launch
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.DefaultWorkflowInput
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.StreamComplete
import net.barrage.llmao.core.workflow.WorkflowRealTime

/** Implementation of a [WorkflowRealTime] for user-created agents. */
class ChatWorkflow(
    override val id: KUUID,
    private val user: User,
    private val emitter: Emitter,
    private val tools: Tools?,
    private val agent: ChatAgent,
    private val repository: ChatRepositoryWrite,

    /** The current state of this workflow. */
    private var state: ChatWorkflowState,
) : WorkflowRealTime<DefaultWorkflowInput>(inputSerializer = DefaultWorkflowInput.serializer()) {
    fun agentId(): KUUID {
        return agent.agentId
    }

    override suspend fun handleInput(input: DefaultWorkflowInput) {
        val content =
            input.attachments?.let { ChatMessageProcessor.toContentMulti(input.text, it) }
                ?: ContentSingle(input.text!!)

        val userMessage = ChatMessage.user(content)

        var (finishReason, messages) =
            agent.collectAndForwardStream(agent.configuration.context, userMessage, tools, emitter)

        if (messages.isEmpty()) {
            emitter.emit(StreamComplete(workflowId = id, reason = finishReason))
            return
        }

        val assistantMessage = messages.last()

        assert(assistantMessage.role == "assistant")
        assert(assistantMessage.finishReason != null)
        assert(assistantMessage.content != null)

        finishReason = assistantMessage.finishReason!!

        agent.addToHistory(messages = listOf(userMessage) + messages)

        // Have to use the scope here for cases when the workflow is closed.
        scope.launch {
            val originalPrompt = userMessage.content!!.text()
            val attachmentsInsert =
                input.attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
            val userMessageInsert = userMessage.toInsert(attachmentsInsert)

            val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

            val assistantMessage = messages.last()
            assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
            assert(assistantMessage.content != null) { "Last assistant message must have content" }

            val groupId =
                when (state) {
                    ChatWorkflowState.New -> {
                        val groupId =
                            repository.insertChatWithMessages(
                                chatId = id,
                                userId = user.id,
                                username = user.username,
                                agentId = agent.agentId,
                                agentConfigurationId = agent.configuration.id,
                                messages = messagesInsert,
                            )

                        val title =
                            agent.createTitle(originalPrompt, assistantMessage.content!!.text())

                        repository.updateTitle(id, user.id, title)
                        emitter.emit(ChatTitleUpdated(id, title))
                        state = ChatWorkflowState.Persisted(title)

                        groupId
                    }

                    is ChatWorkflowState.Persisted -> {
                        repository.insertWorkflowMessages(
                            workflowId = id,
                            workflowType = CHAT_WORKFLOW_ID,
                            messages = messagesInsert,
                        )
                    }
                }

            emitter.emit(
                StreamComplete(
                    workflowId = id,
                    reason = finishReason,
                    messageGroupId = groupId,
                    attachmentPaths = attachmentsInsert,
                    content = null,
                )
            )
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
