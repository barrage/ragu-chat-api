package net.barrage.llmao.app.workflow.tripotron

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.workflow.chat.ChatWorkflowMessage
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.repository.SpecialistRepositoryWrite
import net.barrage.llmao.core.workflow.AgentEventHandler
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.emit
import net.barrage.llmao.types.KUUID

class TripotronWorkflow(
  /** Unique identifier of the workflow. */
  private val id: KUUID,
  private val tripotron: Tripotron,

  /** Current state of the workflow. */
  private var state: TripotronWorkflowState,

  /** Output handle. */
  private val emitter: Emitter,
  private val tripotronWrite: SpecialistRepositoryWrite,
  private val repository: TripotronRepository,
) : Workflow, AgentEventHandler {
  private val scope = CoroutineScope(Dispatchers.Default)
  private var stream: Job? = null

  override fun id(): KUUID = id

  override fun execute(input: String) {
    val input = Json.decodeFromString<TripotronInput>(input)
    stream =
      scope.launch {
        runCatching { execute(input) }
          .onFailure { e ->
            if (e is AppError) emitter.emit(e)
            stream = null
          }
          .onSuccess { stream = null }
      }
  }

  private suspend fun execute(input: TripotronInput) {
    when (input) {
      is TripotronInput.ExpenseUpload -> handleExpenseInput(input)
      is TripotronInput.ExpenseList -> {}
      is TripotronInput.ExpenseVerify -> {}
      is TripotronInput.Finalize -> {}
    }
  }

  override fun cancelStream() {
    if (stream == null || stream?.isCancelled == true) {
      return
    }
    stream!!.cancel()
  }

  override suspend fun onToolCalls(toolCalls: List<ToolCallData>): List<ChatMessage>? {
    return null
  }

  override suspend fun onStreamChunk(chunk: ChatMessageChunk) {}

  override suspend fun onMessage(message: ChatMessage) {
    if (message.role == "assistant") {
      message.content?.let {
        emitter.emit(ChatWorkflowMessage.Response(it.text()), ChatWorkflowMessage.serializer())
      }
    }
  }

  private suspend fun handleExpenseInput(input: TripotronInput.ExpenseUpload) {
    val attachment = listOf(IncomingMessageAttachment.Image(input.data))

    val response = tripotron.completion(input.description, attachment, this)

    val userMessage = response.first()
    val assistantMessage = response.last()

    assert(userMessage.role == "user")
    assert(assistantMessage.role == "assistant")

    val attachments = ChatMessageProcessor.storeMessageAttachments(attachment)

    val insert = listOf(userMessage.toInsert(attachments)) + listOf(assistantMessage.toInsert())

    tripotronWrite.insertMessages(id, insert)
    //    emitter.emit(TripotronOutput.ExpenseRegistered(expense))
  }
}

sealed class TripotronWorkflowState {
  /** Travel data has been sent and the workflow is accepting expense data. */
  data object Started : TripotronWorkflowState()
}
