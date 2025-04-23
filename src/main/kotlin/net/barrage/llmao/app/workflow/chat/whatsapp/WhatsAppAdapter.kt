package net.barrage.llmao.app.workflow.chat.whatsapp

import com.infobip.model.WhatsAppMessage as InfobipWhatsAppMessage
import com.infobip.ApiClient
import com.infobip.ApiException
import com.infobip.ApiKey
import com.infobip.BaseUrl
import com.infobip.api.WhatsAppApi
import com.infobip.model.WhatsAppBulkMessage
import com.infobip.model.WhatsAppSingleMessageInfo
import com.infobip.model.WhatsAppTemplateBodyContent
import com.infobip.model.WhatsAppTemplateContent
import com.infobip.model.WhatsAppTemplateDataContent
import com.infobip.model.WhatsAppTextContent
import com.infobip.model.WhatsAppTextMessage
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.app.workflow.chat.ChatAgent
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.model.Chat
import net.barrage.llmao.app.workflow.chat.model.ChatWithMessages
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.app.workflow.chat.whatsapp.model.InfobipResponse
import net.barrage.llmao.app.workflow.chat.whatsapp.model.InfobipResult
import net.barrage.llmao.app.workflow.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.SettingUpdate
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.administration.settings.SettingsUpdate
import net.barrage.llmao.core.chat.ChatHistory
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.MessageBasedHistory
import net.barrage.llmao.core.chat.TokenBasedHistory
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.tryUuid
import net.barrage.llmao.types.KUUID

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
) {
  private var whatsAppApi: WhatsAppApi
  private val log = KtorSimpleLogger("n.b.l.a.adapters.whatsapp")

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

    val messages = chatAgent.completion(result.message.text)

    // TODO: Include tools in wapp conversation histories
    val response = messages.last()

    val whatsAppMessage =
      createWhatsAppMessage(result.from, result.to, (response.content as ContentSingle).content)

    val messageInfo = sendWhatsAppMessage(whatsAppMessage)

    storeMessages(chatId = chat.id, agentConfigurationId = chatAgent.configurationId, messages)

    log.debug(
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
      chatMessages.items
        .flatMap { it.messages.map(ChatMessageProcessor::loadToChatMessage) }
        .toMutableList()

    val settings = settings.getAllWithDefaults()
    val tokenizer = Encoder.tokenizer(agent.configuration.model)
    val history: ChatHistory =
      tokenizer?.let {
        TokenBasedHistory(
          messages = messages,
          tokenizer = it,
          maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
        )
      } ?: MessageBasedHistory(messages = messages, maxMessages = MAX_HISTORY_MESSAGES)

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

    val user = User(id = userId, entitlements = listOf("user"), username = username, email = null)
    val tokenTracker =
      TokenUsageTracker(
        user = user,
        agentId = agent.agent.id,
        originType = WHATSAPP_CHAT_TOKEN_ORIGIN,
        originId = chat.id,
        repository = tokenUsageRepositoryW,
      )

    return ChatAgent(
      agentId = agent.agent.id,
      configurationId = agent.configuration.id,
      name = agent.agent.name,
      instructions = agent.configuration.agentInstructions,
      titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
      user = user,
      model = agent.configuration.model,
      inferenceProvider = providers.llm[agent.configuration.llmProvider],
      context = agent.configuration.context,
      completionParameters = completionParameters,
      tokenTracker = tokenTracker,
      history = history,
      contextEnrichment =
        ContextEnrichmentFactory.collectionEnrichment(tokenTracker, user, agent.collections)?.let {
          listOf(it)
        },
      // TODO: implement
      tools = null,
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
