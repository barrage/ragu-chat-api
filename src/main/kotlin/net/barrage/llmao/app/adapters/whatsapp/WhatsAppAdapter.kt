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
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResponseDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResult
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository
import net.barrage.llmao.app.workflow.chat.ChatAgent
import net.barrage.llmao.app.workflow.chat.ChatAgentCollection
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsService
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.tokens.TokenUsageRepositoryWrite
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tryUuid

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp")

private const val WHATSAPP_CHAT_TOKEN_ORIGIN = "workflow.whatsapp"

class WhatsAppAdapter(
  apiKey: String,
  endpoint: String,
  private val config: WhatsAppSenderConfig,
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val wappRepository: WhatsAppRepository,
  private val settingsService: SettingsService,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
) {
  private var whatsAppApi: WhatsAppApi

  init {
    val apiClient =
      ApiClient.forApiKey(ApiKey.from(apiKey)).withBaseUrl(BaseUrl.from(endpoint)).build()

    whatsAppApi = WhatsAppApi(apiClient)
  }

  suspend fun getAgent(): AgentFull {
    val agentId =
      settingsService.get(SettingKey.WHATSAPP_AGENT_ID)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    return agentRepository.get(tryUuid(agentId))
  }

  suspend fun setAgent(agentId: KUUID) {
    agentRepository.get(agentId)

    val update =
      SettingsUpdate(
        updates = listOf(SettingUpdate(SettingKey.WHATSAPP_AGENT_ID, agentId.toString()))
      )

    settingsService.update(update)
  }

  suspend fun unsetAgent() {
    val update = SettingsUpdate(removals = listOf(SettingKey.WHATSAPP_AGENT_ID))
    settingsService.update(update)
  }

  suspend fun getNumbers(id: KUUID): List<WhatsAppNumber> {
    return wappRepository.getNumbersByUserId(id)
  }

  suspend fun addNumber(id: KUUID, number: PhoneNumber): WhatsAppNumber {
    val response = wappRepository.addNumber(id, number)
    sendWelcomeMessage(number.phoneNumber)
    return response
  }

  suspend fun updateNumber(
    userId: KUUID,
    numberId: KUUID,
    updateNumber: PhoneNumber,
  ): WhatsAppNumber {
    val number = wappRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot update WhatsApp number for other users",
      )
    }

    val response = wappRepository.updateNumber(numberId, updateNumber)
    sendWelcomeMessage(updateNumber.phoneNumber)
    return response
  }

  suspend fun deleteNumber(userId: KUUID, numberId: KUUID) {
    val number = wappRepository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot delete WhatsApp number for other users",
      )
    }

    if (!wappRepository.deleteNumber(numberId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
    }
  }

  suspend fun getAllChats(pagination: PaginationSort): CountedList<WhatsAppChatWithUserName> {
    return wappRepository.getAllChats(pagination)
  }

  suspend fun getChatByUserId(userId: KUUID): WhatsAppChatWithUserAndMessages {
    val chat =
      wappRepository.getChatByUserId(userId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")

    return getChatWithMessages(chat.id)
  }

  suspend fun getChatWithMessages(id: KUUID): WhatsAppChatWithUserAndMessages {
    return wappRepository.getChatWithMessages(id)
  }

  suspend fun handleIncomingMessage(input: InfobipResponseDTO) {
    for (result in input.results) {
      processResult(result)
    }
  }

  private suspend fun processResult(result: InfobipResult) {
    val whatsAppNumber = wappRepository.getNumber(result.from)
    if (whatsAppNumber == null) {
      val message =
        createWhatsAppMessage(result.from, result.to, "You are not registered in our system.")

      sendWhatsAppMessage(message)

      return
    }

    val processedInput = processInputMessage(result.message.text, whatsAppNumber)

    val whatsAppMessage = createWhatsAppMessage(result.from, result.to, processedInput.message)

    val messageInfo = sendWhatsAppMessage(whatsAppMessage)

    storeMessages(
      processedInput.chatId,
      processedInput.userId,
      processedInput.agentId,
      result.message.text,
      processedInput.message,
    )

    LOG.debug(
      "WhatsApp message sent to: {}, status: {}",
      result.from,
      messageInfo.status.description,
    )
  }

  private suspend fun processInputMessage(
    message: String,
    whatsAppNumber: WhatsAppNumber,
  ): ProcessedInput {
    val wappAgentId =
      settingsService.get(SettingKey.WHATSAPP_AGENT_ID)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not configured")

    val agent = agentRepository.get(KUUID.fromString(wappAgentId))
    val chat = getOrInsertChat(whatsAppNumber.userId)
    val chatMessages = wappRepository.getMessages(chat.id, 20)
    val history = chatMessages.map(WhatsAppMessage::toChatMessage).toMutableList()

    val settings = settingsService.getAllWithDefaults()

    val conversation =
      ChatAgent(
        id = agent.agent.id,
        name = agent.agent.name,
        model = agent.configuration.model,
        llmProvider = agent.configuration.llmProvider,
        context = agent.configuration.context,
        collections =
          agent.collections.map {
            ChatAgentCollection(
              name = it.collection,
              amount = it.amount,
              instruction = it.instruction,
              embeddingProvider = it.embeddingProvider,
              embeddingModel = it.embeddingModel,
              vectorProvider = it.vectorProvider,
            )
          },
        instructions = agent.configuration.agentInstructions,
        tools = null,
        completionParameters =
          ChatCompletionParameters(
            model = agent.configuration.model,
            temperature = agent.configuration.temperature,
            presencePenalty =
              agent.configuration.presencePenalty
                ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
            maxTokens =
              agent.configuration.maxCompletionTokens
                ?: settings[SettingKey.WHATSAPP_AGENT_MAX_COMPLETION_TOKENS].toInt(),
          ),
        configurationId = agent.configuration.id,
        providers = providers,

        // Safe to !! because we are fetching with defaults
        titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
        summaryMaxTokens = settings[SettingKey.AGENT_SUMMARY_MAX_COMPLETION_TOKENS].toInt(),
        tokenTracker =
          TokenUsageTracker(
            userId = whatsAppNumber.userId,
            agentId = agent.agent.id,
            agentConfigurationId = agent.configuration.id,
            origin = WHATSAPP_CHAT_TOKEN_ORIGIN,
            originId = chat.id,
            repository = tokenUsageRepositoryW,
          ),
      )

    history.add(ChatMessage.user(message))

    val response = conversation.chatCompletionWithRag(history)

    // Safe to !! because we are not sending any tools in the message, which means the content
    // is always present
    return ProcessedInput(response.content!!, chat.id, whatsAppNumber.userId, agent.agent.id)
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

  private suspend fun getOrInsertChat(userId: KUUID): WhatsAppChat {
    val chat = wappRepository.getChatByUserId(userId)

    if (chat == null) {
      val id = KUUID.randomUUID()
      return wappRepository.storeChat(id, userId)
    }

    return chat
  }

  private suspend fun storeMessages(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    proompt: String,
    response: String,
  ) {
    val userMessage = wappRepository.insertUserMessage(chatId, userId, proompt)
    wappRepository.insertAssistantMessage(chatId, agentId, userMessage.id, response)
  }
}

data class ProcessedInput(
  val message: String,
  val chatId: KUUID,
  val userId: KUUID,
  val agentId: KUUID,
)

data class WhatsAppSenderConfig(val sender: String, val template: String, val appName: String)
