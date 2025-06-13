package net.barrage.llmao.app.workflow.jirakira

import kotlinx.coroutines.launch
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
import java.time.OffsetDateTime

class JiraKiraWorkflow(
    override val id: KUUID,
    private val user: User,
    private val emitter: Emitter,
    private val tools: Tools,
    private val agent: JiraKira,
    private val repository: JiraKiraRepository,
    private val jiraUser: JiraUser,
) : WorkflowRealTime<DefaultWorkflowInput>(inputSerializer = DefaultWorkflowInput.serializer()) {
    private var state: JiraKiraWorkflowState = JiraKiraWorkflowState.New

    override suspend fun handleInput(input: DefaultWorkflowInput) {
        val content =
            input.attachments?.let { ChatMessageProcessor.toContentMulti(input.text, it) }
                ?: ContentSingle(input.text!!)

        val context =
            """
        |$JIRA_KIRA_CONTEXT
        |The JIRA user you are talking to is called ${jiraUser.displayName} and their email is ${jiraUser.email}.
        |The user is logged in to Jira as ${jiraUser.name} and their Jira user key is ${jiraUser.key}.
        |The time zone of the user is ${jiraUser.timeZone}. The current time is ${OffsetDateTime.now()}.
        """
                .trimMargin()

        val userMessage = ChatMessage.user(content)

        var (finishReason, messages) = agent.collectCompletion(context, userMessage, tools, emitter)

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
            val attachmentsInsert =
                input.attachments?.let { ChatMessageProcessor.storeMessageAttachments(it) }
            val userMessageInsert = userMessage.toInsert(attachmentsInsert)

            val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

            val assistantMessage = messages.last()
            assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
            assert(assistantMessage.content != null) { "Last assistant message must have content" }

            val groupId =
                when (state) {
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

                        groupId
                    }

                    is JiraKiraWorkflowState.Persisted -> {
                        log.debug("{} - persisting message pair", id)
                        repository.insertMessages(workflowId = id, messages = messagesInsert)
                    }
                }

            emitter.emit(
                StreamComplete(
                    workflowId = id,
                    reason = finishReason,
                    messageGroupId = groupId,
                    attachmentPaths = attachmentsInsert,
                    content = assistantMessage.content!!.text(),
                )
            )
        }
    }
}

private sealed class JiraKiraWorkflowState {
    data object New : JiraKiraWorkflowState()

    data object Persisted : JiraKiraWorkflowState()
}
