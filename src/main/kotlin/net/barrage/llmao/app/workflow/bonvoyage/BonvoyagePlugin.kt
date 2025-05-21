package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.administration.settings.SettingKey
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

    val settings = state.settings.getAllWithDefaults()

    if (settings.getOptional(SettingKey.BONVOYAGE_LLM_PROVIDER) == null) {
      log.warn(
        "No Bonvoyage LLM provider configured. Expense upload and chatting is disabled. Set `BONVOYAGE_LLM_PROVIDER` in the application settings to enable."
      )
    }

    if (settings.getOptional(SettingKey.BONVOYAGE_MODEL) == null) {
      log.warn(
        "No Bonvoyage model configured. Expense upload and chatting is disabled. Set `BONVOYAGE_MODEL` in the application settings to enable. The model must be supported by the provider."
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
    validate<TravelRequest>(TravelRequest::validate)
    validate<BonvoyageTripPropertiesUpdate>(BonvoyageTripPropertiesUpdate::validate)
  }

  override fun PolymorphicModuleBuilder<WorkflowOutput>.configureOutputSerialization() {
    subclass(ExpenseUpload::class, ExpenseUpload.serializer())
    subclass(ExpenseUpdate::class, ExpenseUpdate.serializer())
  }
}

object BonvoyageConfig {
  lateinit var emailSender: String
  lateinit var logoPath: String
  lateinit var fontPath: String

  fun init(config: ApplicationConfig) {
    emailSender = config.string("bonvoyage.emailSender")
    logoPath = config.string("bonvoyage.logoPath")
    fontPath = config.string("bonvoyage.fontPath")
  }
}
