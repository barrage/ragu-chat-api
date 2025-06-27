package net.barrage.llmao.chat.whatsapp

import com.infobip.ApiClient
import com.infobip.ApiException
import com.infobip.ApiKey
import com.infobip.BaseUrl
import com.infobip.api.WhatsAppApi
import com.infobip.model.WhatsAppBulkMessage
import com.infobip.model.WhatsAppMessage as InfobipWhatsAppMessage
import com.infobip.model.WhatsAppSingleMessageInfo
import com.infobip.model.WhatsAppTemplateBodyContent
import com.infobip.model.WhatsAppTemplateContent
import com.infobip.model.WhatsAppTemplateDataContent
import com.infobip.model.WhatsAppTextContent
import com.infobip.model.WhatsAppTextMessage
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.chat.CHAT_WORKFLOW_ID
import net.barrage.llmao.chat.ChatAgent
import net.barrage.llmao.chat.ChatWorkflowFactory
import net.barrage.llmao.chat.WhatsappAgentId
import net.barrage.llmao.chat.model.AgentFull
import net.barrage.llmao.chat.model.Chat
import net.barrage.llmao.chat.model.ChatWithMessages
import net.barrage.llmao.chat.repository.AgentRepository
import net.barrage.llmao.chat.repository.ChatRepositoryRead
import net.barrage.llmao.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.chat.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.input.whatsapp.model.InfobipResponse
import net.barrage.llmao.core.input.whatsapp.model.InfobipResult
import net.barrage.llmao.core.input.whatsapp.model.Message
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.tryUuid
import net.barrage.llmao.core.types.KUUID

private const val MAX_HISTORY_MESSAGES = 10

class WhatsAppAdapter(
  apiKey: String,
  endpoint: String,
  private val config: WhatsAppSenderConfig,
  private val agentRepository: AgentRepository,
  private val whatsAppRepository: WhatsAppRepository,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val chatRepositoryWrite: ChatRepositoryWrite,
  private val settings: Settings,
) {
  private var whatsAppApi: WhatsAppApi
  private val log = KtorSimpleLogger("n.b.l.a.workflow.chat.whatsapp")

  init {
    val apiClient =
      ApiClient.forApiKey(ApiKey.from(apiKey)).withBaseUrl(BaseUrl.from(endpoint)).build()

    whatsAppApi = WhatsAppApi(apiClient)
  }

  suspend fun getAgent(): AgentFull {
    val agentId =
      settings.get(WhatsappAgentId.KEY)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    return agentRepository.get(tryUuid(agentId))
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
  }

  suspend fun setAgent(agentId: KUUID) {
    agentRepository.get(agentId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

    val update =
      SettingsUpdate(updates = listOf(SettingUpdate(WhatsappAgentId.KEY, agentId.toString())))

    settings.update(update)
  }

  suspend fun unsetAgent() {
    val update = SettingsUpdate(removals = listOf(WhatsappAgentId.KEY))
    settings.update(update)
  }

  suspend fun getNumbers(userId: String): List<WhatsAppNumber> {
    return whatsAppRepository.getNumbersByUserId(userId)
  }

  suspend fun addNumber(userId: String, username: String, number: String): WhatsAppNumber {
    val response = whatsAppRepository.addNumber(userId, username, number)
    sendWelcomeMessage(number)
    return response
  }

  suspend fun updateNumber(
    userId: String,
    numberId: KUUID,
    updateNumber: UpdateNumber,
  ): WhatsAppNumber {
    val number = whatsAppRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
    }

    val response = whatsAppRepository.updateNumber(numberId, updateNumber)

    sendWelcomeMessage(updateNumber.phoneNumber)

    return response
  }

  suspend fun deleteNumber(userId: String, numberId: KUUID) {
    val number = whatsAppRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
    }

    if (!whatsAppRepository.deleteNumber(numberId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
    }
  }

  suspend fun getAllChats(pagination: PaginationSort): CountedList<Chat> {
    return chatRepositoryRead.getAll(pagination)
  }

  suspend fun getChatByUserId(userId: String): ChatWithMessages {
    return chatRepositoryRead.getSingleByUserId(userId, messageLimit = MAX_HISTORY_MESSAGES)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")
  }

  suspend fun getChatById(chatId: KUUID): ChatWithMessages {
    return chatRepositoryRead.getWithMessages(chatId, Pagination(1, MAX_HISTORY_MESSAGES))
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")
  }

  suspend fun handleIncomingMessage(input: InfobipResponse) {
    val agentId =
      settings.get(WhatsappAgentId.KEY)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    val agent =
      agentRepository.get(tryUuid(agentId))
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")

    for (result in input.results) {
      handleMessage(result, agent)
    }
  }

  private suspend fun handleMessage(result: InfobipResult, agent: AgentFull) {
    if (result.message !is Message.Text) {
      log.warn("Unsupported message type: {}", result.message)
      return
    }

    val whatsAppNumber = whatsAppRepository.getNumber(result.from)

    if (whatsAppNumber == null) {
      val message =
        createWhatsAppMessage(result.from, result.to, "You are not registered in our system.")
      sendWhatsAppMessage(message)

      return
    }

    val chat =
      getOrInsertChat(
        userId = whatsAppNumber.userId,
        username = whatsAppNumber.username,
        agentId = agent.agent.id,
        agentConfigurationId = agent.configuration.id,
      )

    val chatAgent = getChatAgent(whatsAppNumber.userId, whatsAppNumber.username, chat.id, agent)

    val messages =
      chatAgent.completion(chatAgent.configuration.context, (result.message as Message.Text).text)

    // TODO: Include tools in wapp conversation histories
    val response = messages.last()

    val whatsAppMessage =
      createWhatsAppMessage(result.from, result.to, (response.content as ContentSingle).content)

    val messageInfo = sendWhatsAppMessage(whatsAppMessage)

    chatRepositoryWrite.insertWorkflowMessages(
      workflowId = chat.id,
      CHAT_WORKFLOW_ID,
      messages.map { it.toInsert() },
    )

    log.debug(
      "WhatsApp message sent to: {}, status: {}",
      result.from,
      messageInfo.status.description,
    )
  }

  private suspend fun getChatAgent(
    userId: String,
    username: String,
    chatId: KUUID,
    agent: AgentFull,
  ): ChatAgent {
    val chatMessages =
      chatRepositoryRead.getWorkflowMessages(
        workflowId = chatId,
        Pagination(1, MAX_HISTORY_MESSAGES),
      )

    val agent = ChatWorkflowFactory.createChatAgent(chatId, userId, username, listOf("user"), agent)

    val messages =
      chatMessages.items
        .flatMap { it.messages.map(ChatMessageProcessor::loadToChatMessage) }
        .toMutableList()

    agent.addToHistory(messages)

    return agent
  }

  private fun createWhatsAppMessage(
    to: String,
    from: String,
    content: String,
  ): WhatsAppTextMessage {
    return WhatsAppTextMessage().apply {
      this.to = to
      this.from = from
      this.content = WhatsAppTextContent().apply { text = content }
    }
  }

  private fun sendWhatsAppMessage(message: WhatsAppTextMessage): WhatsAppSingleMessageInfo {
    return whatsAppApi.sendWhatsAppTextMessage(message).execute()
  }

  private fun sendWelcomeMessage(to: String) {
    val message =
      InfobipWhatsAppMessage()
        .from(config.sender)
        .to(to)
        .content(
          WhatsAppTemplateContent()
            .language("en")
            .templateName(config.template)
            .templateData(
              WhatsAppTemplateDataContent()
                .body(WhatsAppTemplateBodyContent().addPlaceholdersItem(config.appName))
            )
        )

    val bulkMessage = WhatsAppBulkMessage().addMessagesItem(message)

    try {
      val messageInfo = whatsAppApi.sendWhatsAppTemplateMessage(bulkMessage).execute()

      log.debug(
        "WhatsApp welcome message sent to: {}, status: {}",
        to,
        messageInfo.messages[0].status.description,
      )
    } catch (e: ApiException) {
      log.error("Failed to send WhatsApp welcome message to: $to", e)
      log.error("Response body: ${e.rawResponseBody()}")
    } catch (e: Exception) {
      log.error("Unexpected error when sending WhatsApp welcome message to: $to", e)
    }
  }

  private suspend fun getOrInsertChat(
    userId: String,
    username: String?,
    agentId: KUUID,
    agentConfigurationId: KUUID,
  ): Chat {
    val chat = chatRepositoryRead.getSingleByUserId(userId)

    if (chat == null) {
      val id = KUUID.randomUUID()
      return chatRepositoryWrite.insertChat(
        chatId = id,
        agentId = agentId,
        userId = userId,
        username = username,
        agentConfigurationId = agentConfigurationId,
      )
    }

    return chat.chat
  }
}

data class WhatsAppSenderConfig(val sender: String, val template: String, val appName: String)
