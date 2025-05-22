package net.barrage.llmao.app.workflow.bonvoyage

import com.itextpdf.io.image.ImageData
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.string

internal const val BONVOYAGE_WORKFLOW_ID = "BONVOYAGE"

class BonvoyagePlugin() : Plugin {
  private lateinit var admin: BonvoyageAdminApi
  private lateinit var user: BonvoyageUserApi
  private val log = KtorSimpleLogger("n.b.l.a.workflow.bonvoyage.BonvoyagePlugin")

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    BonvoyageConfig.init(config)
    BonvoyageWorkflowFactory.init(state)
    WorkflowFactoryManager.register(BonvoyageWorkflowFactory)

    val repository = BonvoyageRepository(state.database)
    val scheduler = BonvoyageNotificationScheduler(state.email, repository)
    scheduler.start()

    val settings = state.settings.getAll()

    if (settings.getOptional(BonvoyageLlmProvider.KEY) == null) {
      log.warn(
        "No Bonvoyage LLM provider configured. Expense upload and chatting is disabled. Set `${BonvoyageLlmProvider.KEY}` in the application settings to enable."
      )
    }

    if (settings.getOptional(BonvoyageModel.KEY) == null) {
      log.warn(
        "No Bonvoyage model configured. Expense upload and chatting is disabled. Set `${BonvoyageModel.KEY}` in the application settings to enable. The model must be supported by the provider."
      )
    }

    admin = BonvoyageAdminApi(repository, state.email, state.settings, state.providers)
    user = BonvoyageUserApi(repository, state.email, state.providers.image)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") { bonvoyageAdminRoutes(admin) }
    authenticate("user") { bonvoyageUserRoutes(user) }
  }

  override fun RequestValidationConfig.configureRequestValidation() {
    validate<TravelRequestParameters>(TravelRequestParameters::validate)
    validate<TripPropertiesUpdate>(TripPropertiesUpdate::validate)
  }

  override fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    subclass(ExpenseUpload::class, ExpenseUpload.serializer())
    subclass(ExpenseUpdate::class, ExpenseUpdate.serializer())
  }
}

object BonvoyageConfig {
  lateinit var emailSender: String
  lateinit var logo: ImageData
  lateinit var font: PdfFont

  fun init(config: ApplicationConfig) {
    emailSender = config.string("bonvoyage.emailSender")
    logo = ImageDataFactory.create(config.string("bonvoyage.logoPath"))
    font = PdfFontFactory.createFont(config.string("bonvoyage.fontPath"))
  }
}

/** The LLM provider to use for Bonvoyage. */
internal data object BonvoyageLlmProvider {
  const val KEY = "BONVOYAGE_LLM_PROVIDER"
}

/** Which model will be used for Bonvoyage. Has to be compatible with [BonvoyageLlmProvider]. */
internal data object BonvoyageModel {
  const val KEY = "BONVOYAGE_MODEL"
}

/** The maximum amount of tokens to keep in trip chat histories. */
internal data object BonvoyageMaxHistoryTokens {
  const val KEY = "BONVOYAGE_MAX_HISTORY_TOKENS"
  const val DEFAULT = 100_000
}
