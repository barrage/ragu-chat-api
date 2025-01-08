package net.barrage.llmao.app.adapters.whatsapp

import com.infobip.ApiClient
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
import io.ktor.server.config.*
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResponseDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.InfobipResult
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgent
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateCollectionsResult
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.ConversationService
import net.barrage.llmao.core.services.processAdditions
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

internal val LOG = io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp")

class WhatsAppAdapter(
  private val config: ApplicationConfig,
  private val conversation: ConversationService,
  private val providers: ProviderState,
  private val repository: WhatsAppRepository,
) {
  private var whatsAppApi: WhatsAppApi

  init {
    val apiKey = config.string("infobip.apiKey")
    val endpoint = config.string("infobip.endpoint")
    val apiClient =
      ApiClient.forApiKey(ApiKey.from(apiKey)).withBaseUrl(BaseUrl.from(endpoint)).build()

    whatsAppApi = WhatsAppApi(apiClient)
  }

  suspend fun getNumbers(id: KUUID): List<WhatsAppNumber> {
    return repository.getNumbersByUserId(id)
  }

  suspend fun addNumber(id: KUUID, number: PhoneNumber): WhatsAppNumber {
    val response = repository.addNumber(id, number)
    sendWelcomeMessage(number.phoneNumber)
    return response
  }

  suspend fun updateNumber(
    userId: KUUID,
    numberId: KUUID,
    updateNumber: PhoneNumber,
  ): WhatsAppNumber {
    val number = repository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot update WhatsApp number for other users",
      )
    }

    val response = repository.updateNumber(numberId, updateNumber)
    sendWelcomeMessage(updateNumber.phoneNumber)
    return response
  }

  suspend fun deleteNumber(userId: KUUID, numberId: KUUID) {
    val number = repository.getNumberById(numberId)

    if (number.userId != userId) {
      throw AppError.api(
        ErrorReason.Authentication,
        "Cannot delete WhatsApp number for other users",
      )
    }

    if (!repository.deleteNumber(numberId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
    }
  }

  suspend fun getAllAgents(pagination: PaginationSort): CountedList<WhatsAppAgent> {
    return repository.getAgents(pagination)
  }

  suspend fun getAgent(id: KUUID): WhatsAppAgentFull {
    return repository.getAgent(id)
  }

  suspend fun createAgent(create: CreateAgent): WhatsAppAgent {
    providers.validateSupportedConfigurationParams(
      llmProvider = create.configuration.llmProvider,
      model = create.configuration.model,
    )

    return repository.createAgent(create)
      ?: throw AppError.api(ErrorReason.Internal, "Something went wrong while creating agent")
  }

  suspend fun getAllChats(pagination: PaginationSort): CountedList<WhatsAppChatWithUserName> {
    return repository.getAllChats(pagination)
  }

  suspend fun getChatByUserId(userId: KUUID): WhatsAppChatWithUserAndMessages {
    val chat =
      repository.getChatByUserId(userId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")

    return getChatWithMessages(chat.id)
  }

  suspend fun getChatWithMessages(id: KUUID): WhatsAppChatWithUserAndMessages {
    return repository.getChatWithMessages(id)
  }

  suspend fun updateCollections(
    agentId: KUUID,
    update: UpdateCollections,
  ): UpdateCollectionsResult {
    val (additions, failures) = processAdditions(providers, update)

    repository.updateCollections(agentId, additions, update.remove)

    return UpdateCollectionsResult(additions.map { it.info }, update.remove.orEmpty(), failures)
  }

  suspend fun removeCollectionFromAllAgents(collectionName: String, provider: String) {
    repository.removeCollectionFromAllAgents(collectionName, provider)
  }

  suspend fun updateAgent(agentId: KUUID, update: UpdateAgent): WhatsAppAgent {
    providers.validateSupportedConfigurationParams(
      llmProvider = update.configuration?.llmProvider,
      model = update.configuration?.model,
    )

    return repository.updateAgent(agentId, update)
  }

  suspend fun deleteAgent(agentId: KUUID) {
    // This is to ensure the agent exists
    val agent = repository.getAgent(agentId)
    if (agent.agent.active) {
      throw AppError.api(ErrorReason.InvalidOperation, "Cannot delete active agent")
    }

    repository.deleteAgent(agentId)
  }

  suspend fun handleIncomingMessage(input: InfobipResponseDTO) {
    for (result in input.results) {
      processResult(result)
    }
  }

  private suspend fun processResult(result: InfobipResult) {
    val whatsAppNumber = repository.getNumber(result.from)
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
    val agentFull = repository.getActiveAgentFull()
    val chat = getChat(whatsAppNumber.userId)
    val chatMessages = repository.getMessages(chat.id, 20)
    val history = chatMessages.map(WhatsAppMessage::toChatMessage)

    val agentConfig = agentFull.getConfiguration()
    val agentCollections = agentFull.collections

    val query = conversation.prepareChatPrompt(message, agentConfig, agentCollections, history)

    val llm = providers.llm.getProvider(agentConfig.llmProvider)

    val output = llm.chatCompletion(query, LlmConfig(agentConfig.model, agentConfig.temperature))
    return ProcessedInput(output, chat.id, whatsAppNumber.userId, agentFull.agent.id)
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
    val sender = config.string("infobip.sender")
    val template = config.string("infobip.template")
    val appName = config.string("infobip.appName")
    val message =
      InfobipWhatsAppMessage()
        .from(sender)
        .to(to)
        .content(
          WhatsAppTemplateContent()
            .language("en")
            .templateName(template)
            .templateData(
              WhatsAppTemplateDataContent()
                .body(WhatsAppTemplateBodyContent().addPlaceholdersItem(appName))
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
    } catch (e: com.infobip.ApiException) {
      LOG.error("Failed to send WhatsApp welcome message to: $to", e)
      LOG.error("Response body: ${e.rawResponseBody()}")
    } catch (e: Exception) {
      LOG.error("Unexpected error when sending WhatsApp welcome message to: $to", e)
    }
  }

  private suspend fun getChat(userId: KUUID): WhatsAppChat {
    val chat = repository.getChatByUserId(userId)
    if (chat == null) {
      val id = KUUID.randomUUID()
      return repository.storeChat(id, userId)
    }
    LOG.trace("Found chat {}", chat)
    return chat
  }

  private suspend fun storeMessages(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    proompt: String,
    response: String,
  ) {
    val userMessage = repository.insertUserMessage(chatId, userId, proompt)
    repository.insertAssistantMessage(chatId, agentId, userMessage.id, response)
  }
}

data class ProcessedInput(
  val message: String,
  val chatId: KUUID,
  val userId: KUUID,
  val agentId: KUUID,
)
