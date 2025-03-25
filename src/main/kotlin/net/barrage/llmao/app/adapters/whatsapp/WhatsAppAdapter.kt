package net.barrage.llmao.app.adapters.whatsapp

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
import com.knuddels.jtokkit.api.EncodingRegistry
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResponseDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResult
import net.barrage.llmao.app.adapters.whatsapp.models.UpdateNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository
import net.barrage.llmao.app.chat.ChatAgent
import net.barrage.llmao.app.chat.toChatAgent
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.agent.AgentRepository
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.ChatWithMessages
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tryUuid

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp")

private const val WHATSAPP_CHAT_TOKEN_ORIGIN = "workflow.whatsapp"
private const val MAX_HISTORY_MESSAGES = 50

class WhatsAppAdapter(
  apiKey: String,
  endpoint: String,
  private val config: WhatsAppSenderConfig,
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val whatsAppRepository: WhatsAppRepository,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val chatRepositoryWrite: ChatRepositoryWrite,
  private val settings: Settings,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  private val encodingRegistry: EncodingRegistry,
) {
  private var whatsAppApi: WhatsAppApi

  init {
    val apiClient =
      ApiClient.forApiKey(ApiKey.from(apiKey)).withBaseUrl(BaseUrl.from(endpoint)).build()

    whatsAppApi = WhatsAppApi(apiClient)
  }

  suspend fun getAgent(): AgentFull {
    val agentId =
      settings.get(SettingKey.WHATSAPP_AGENT_ID)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    return agentRepository.get(tryUuid(agentId))
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
  }

  suspend fun setAgent(agentId: KUUID) {
    agentRepository.get(agentId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

    val update =
      SettingsUpdate(
        updates = listOf(SettingUpdate(SettingKey.WHATSAPP_AGENT_ID, agentId.toString()))
      )

    settings.update(update)
  }

  suspend fun unsetAgent() {
    val update = SettingsUpdate(removals = listOf(SettingKey.WHATSAPP_AGENT_ID))
    settings.update(update)
  }

  suspend fun getNumbers(userId: String): List<WhatsAppNumber> {
    return whatsAppRepository.getNumbersByUserId(userId)
  }

  suspend fun addNumber(userId: String, username: String?, number: UpdateNumber): WhatsAppNumber {
    val response = whatsAppRepository.addNumber(userId, username, number)
    sendWelcomeMessage(number.phoneNumber)
    return response
  }

  suspend fun updateNumber(
    userId: String,
    numberId: KUUID,
    updateNumber: UpdateNumber,
  ): WhatsAppNumber {
    val number = whatsAppRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot update WhatsApp number for other users",
      )
    }

    val response = whatsAppRepository.updateNumber(numberId, updateNumber)
    sendWelcomeMessage(updateNumber.phoneNumber)
    return response
  }

  suspend fun deleteNumber(userId: String, numberId: KUUID) {
    val number = whatsAppRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot delete WhatsApp number for other users",
      )
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

  suspend fun handleIncomingMessage(input: InfobipResponseDTO) {
    val agentId =
      settings.get(SettingKey.WHATSAPP_AGENT_ID)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    val agent =
      agentRepository.get(tryUuid(agentId))
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")

    for (result in input.results) {
      handleMessage(result, agent)
    }
  }

  private suspend fun handleMessage(result: InfobipResult, agent: AgentFull) {
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
      )
    val chatAgent = getChatAgent(whatsAppNumber.userId, whatsAppNumber.username, chat, agent)

    val userMessage = ChatMessage.user(result.message.text)

    val buffer = mutableListOf(userMessage)

    chatAgent.chatCompletion(buffer)

    // TODO: Include tools in wapp conversation histories
    val response = buffer.last()

    val whatsAppMessage = createWhatsAppMessage(result.from, result.to, response.content!!)

    val messageInfo = sendWhatsAppMessage(whatsAppMessage)

    storeMessages(chatId = chat.id, agentConfigurationId = chatAgent.configurationId, buffer)

    LOG.debug(
      "WhatsApp message sent to: {}, status: {}",
      result.from,
      messageInfo.status.description,
    )
  }

  private suspend fun getChatAgent(
    userId: String,
    username: String?,
    chat: Chat,
    agent: AgentFull,
  ): ChatAgent {
    val chatMessages =
      chatRepositoryRead.getMessages(
        chatId = chat.id,
        userId = userId,
        Pagination(1, MAX_HISTORY_MESSAGES),
      )

    val messages =
      chatMessages.items.flatMap { it.messages.map { ChatMessage.fromModel(it) } }.toMutableList()

    val settings = settings.getAllWithDefaults()
    val tokenizer = encodingRegistry.getEncodingForModel(agent.configuration.model)
    val history: ChatHistory =
      if (tokenizer.isEmpty) {
        MessageBasedHistory(messages = messages, maxMessages = MAX_HISTORY_MESSAGES)
      } else {
        TokenBasedHistory(
          messages = messages,
          tokenizer = tokenizer.get(),
          maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
        )
      }

    val completionParameters =
      ChatCompletionParameters(
        model = agent.configuration.model,
        temperature = agent.configuration.temperature,
        presencePenalty =
          agent.configuration.presencePenalty
            ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
        maxTokens =
          agent.configuration.maxCompletionTokens
            ?: settings[SettingKey.WHATSAPP_AGENT_MAX_COMPLETION_TOKENS].toInt(),
      )

    val tokenTracker =
      TokenUsageTracker(
        userId = userId,
        username = username,
        agentId = agent.agent.id,
        agentConfigurationId = agent.configuration.id,
        origin = WHATSAPP_CHAT_TOKEN_ORIGIN,
        originId = chat.id,
        repository = tokenUsageRepositoryW,
      )

    return agent.toChatAgent(
      userId = userId,
      history = history,
      providers = providers,
      settings = settings,
      toolchain = null,
      tokenTracker = tokenTracker,
      completionParameters = completionParameters,
      // TODO: Replace with authorization config in future.
      allowedGroups = listOf("user"),
    )
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

      LOG.debug(
        "WhatsApp welcome message sent to: {}, status: {}",
        to,
        messageInfo.messages[0].status.description,
      )
    } catch (e: ApiException) {
      LOG.error("Failed to send WhatsApp welcome message to: $to", e)
      LOG.error("Response body: ${e.rawResponseBody()}")
    } catch (e: Exception) {
      LOG.error("Unexpected error when sending WhatsApp welcome message to: $to", e)
    }
  }

  private suspend fun getOrInsertChat(userId: String, username: String?, agentId: KUUID): Chat {
    val chat = chatRepositoryRead.getSingleByUserId(userId)

    if (chat == null) {
      val id = KUUID.randomUUID()
      return chatRepositoryWrite.insertChat(
        chatId = id,
        agentId = agentId,
        userId = userId,
        username = username,
      )
    }

    return chat.chat
  }

  private suspend fun storeMessages(
    chatId: KUUID,
    agentConfigurationId: KUUID,
    messages: List<ChatMessage>,
  ) {
    chatRepositoryWrite.insertMessages(chatId, agentConfigurationId, messages.map { it.toInsert() })
  }
}

data class WhatsAppSenderConfig(val sender: String, val template: String, val appName: String)
