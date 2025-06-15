package net.barrage.llmao

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ConfigLoader
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.workflow.chat.WHATSAPP_FEATURE_FLAG
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KOffsetDateTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.wiremock.extension.jwt.JwtExtensionFactory

@TestInstance(
  TestInstance.Lifecycle.PER_CLASS // Needed to avoid static methods in companion object
)
open class IntegrationTest(
  /** If `true`, initialize the weaviate container and the weaviate client. */
  private val useWeaviate: Boolean = false,

  /** If `true`, start a wiremock container for all external APIs. */
  private val useWiremock: Boolean = true,

  /** If `true`, initialize the minio container and the minio client. */
  private val useMinio: Boolean = false,

  /**
   * If given and `useWiremock` is `true`, an existing wiremock container will be used instead,
   * located on the URL. Useful for recording responses from test suites. Ideally this would be an
   * environment variable, but we do not live in an ideal world.
   */
  private val wiremockUrlOverride: String? = null,
  enableWhatsApp: Boolean = false,
) {
  val postgres: TestPostgres = TestPostgres()
  private var minio: TestMinio? = null
  var weaviate: TestWeaviate? = null
  var wiremock: WireMockServer? = null

  private var cfg = ConfigLoader.load("application.example.conf")

  init {
    // Postgres
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "db.url" to postgres.container.jdbcUrl,
          "db.user" to postgres.container.username,
          "db.password" to postgres.container.password,
          "db.r2dbcHost" to postgres.container.host,
          "db.r2dbcPort" to postgres.container.getMappedPort(5432).toString(),
          "db.r2dbcDatabase" to postgres.container.databaseName,
          "db.runMigrations" to "false", // We migrate manually on PG container initialization
          "oauth.apple.clientSecret" to generateP8PrivateKey(),
        )
      )

    // Feature adapters
    cfg = cfg.mergeWith(MapApplicationConfig(WHATSAPP_FEATURE_FLAG to enableWhatsApp.toString()))

    val now = Instant.now().epochSecond
    val max = KOffsetDateTime.MAX.toEpochSecond()
    cfg = cfg.mergeWith(MapApplicationConfig("jwt.leeway" to (max - now).toString()))
  }

  /**
   * The main test execution function that uses configuration obtained from the test containers as
   * the application environment and provides a client with JSON serialization enabled.
   */
  fun test(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
    environment { config = cfg }
    val client = createClient {
      install(ContentNegotiation) { json(json = Json { ignoreUnknownKeys = true }) }
    }
    try {
      block(client)
    } catch (e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }

  /** Main execution function for websocket tests that exposes a websocket client. */
  fun wsTest(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
    environment { config = cfg }
    block(
      createClient {
        install(WebSockets) {}
        install(ContentNegotiation) { json(json = Json { ignoreUnknownKeys = true }) }
      }
    )
  }

  @BeforeAll
  fun beforeAll() {
    if (useWeaviate) {
      loadWeaviate()
    }

    if (useWiremock) {
      loadWiremock()
    }

    if (useMinio) {
      loadMinio()
    }
  }

  @AfterAll
  fun stopContainersAfterTests() {
    postgres.container.stop()
    weaviate?.container?.stop()
  }

  private fun loadWeaviate() {
    weaviate = TestWeaviate()
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "weaviate.host" to weaviate!!.container.httpHostAddress,
          "weaviate.scheme" to "http",
        )
      )
  }

  private fun loadWiremock() {
    if (wiremockUrlOverride != null) {
      cfg =
        cfg.mergeWith(
          MapApplicationConfig(
            "jirakira.endpoint" to wiremockUrlOverride,

            // We are not overriding the API key since we need real ones.

            // Has to match the URL from the OpenAI SDK.
            "llm.openai.endpoint" to "$wiremockUrlOverride/v1/",
            "embeddings.openai.endpoint" to "$wiremockUrlOverride/v1/",

            // Has to match the URL from the Azure OpenAI SDK.
            "embeddings.azure.endpoint" to "$wiremockUrlOverride/openai/deployments",
            "vault.endpoint" to wiremockUrlOverride,

            // Has to match the URL from the Infobip SDK.
            "infobip.endpoint" to wiremockUrlOverride,
            "jwt.issuer" to wiremockUrlOverride,
            "jwt.jwksEndpoint" to "$wiremockUrlOverride/jwks/",
          )
        )
      return
    }

    /**
     * Creates Wiremock server locally.
     *
     * The `resources/wiremock/mappings` directory contains the definition for responses we mock.
     *
     * Each response will have a `bodyFileName` in the response designating the body for it. When
     * the server is started it will copy `resources/wiremock/__files` directory to the
     * `/home/wiremock/__files` directory onto the server, matching the directory structure. The
     * `bodyFileName` must be equal to the path from the `__files` directory.
     */
    wiremock =
      WireMockServer(
        WireMockConfiguration.options()
          .dynamicPort()
          .extensions(JwtExtensionFactory())
          .usingFilesUnderClasspath("wiremock")
      )

    wiremock!!.start()
    val url = wiremock!!.baseUrl()

    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          // The JWT issuer should be the same as in the hardcoded tokens,
          // in our case that's the authentik instance we originally got the tokens from
          "jwt.issuer" to "https://authentik.barrage.dev/application/o/ragu/",
          "jwt.jwksEndpoint" to "$url/$AUTH_WM/application/o/ragu/jwks/",
          "llm.openai.endpoint" to "$url/$OPENAI_WM/v1/",
          "llm.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.openai.endpoint" to "$url/$OPENAI_WM/v1/",
          "embeddings.openai.apiKey" to "super-duper-secret-openai-api-key",
          "embeddings.azure.endpoint" to "$url/$AZURE_WM/openai/deployments",
          "embeddings.azure.apiKey" to "super-duper-secret-azure-api-key",
          "embeddings.fembed.endpoint" to "$url/$FEMBED_WM",
          "infobip.endpoint" to url,
          "infobip.apiKey" to "super-duper-secret-infobip-api-key",
          "llm.ollama.endpoint" to "$url/$OLLAMA_WM",
          "jirakira.endpoint" to "$url/$JIRA_WM",
        )
      )
  }

  private fun loadMinio() {
    minio = TestMinio()

    // Minio
    cfg =
      cfg.mergeWith(
        MapApplicationConfig(
          "minio.endpoint" to minio!!.container.s3URL,
          "minio.accessKey" to "testMinio",
          "minio.secretKey" to "testMinio",
          "minio.bucket" to "test",
        )
      )
  }
}

/** Has to be verifiable with the Wiremock auth sever public key. */
fun adminAccessToken(): String = "access_token=$VALID_ADMIN_ACCESS_TOKEN"

fun userAccessToken(): String = "access_token=$VALID_USER_ACCESS_TOKEN"

// Wiremock URL discriminators

const val AUTH_WM = "__auth"
const val OPENAI_WM = "__openai"
const val AZURE_WM = "__azure"
const val FEMBED_WM = "__fembed"
const val OLLAMA_WM = "__ollama"
const val JIRA_WM = "__jira"

// Wiremock response triggers

/** Prompt configured to make wiremock return a completion response without a stream. */
const val COMPLETIONS_COMPLETION_PROMPT = "v1_chat_completions_completion"

/** Prompt configured to make wiremock return the default stream response. */
const val COMPLETIONS_STREAM_PROMPT = "v1_chat_completions_stream"

/**
 * Instruction configured to make wiremock return the default title response. This should be used in
 * agent instructions as the title instruction
 */
const val COMPLETIONS_TITLE_PROMPT = "v1_chat_completions_title"

/**
 * Prompt configured to make wiremock return a stream response with additional whitespace for
 * testing purposes.
 */
const val COMPLETIONS_STREAM_WHITESPACE_PROMPT = "v1_chat_completions_whitespace_stream"

/** Prompt configured to make wiremock return a stream response with a long response. */
const val COMPLETIONS_STREAM_LONG_PROMPT = "v1_chat_completions_long_stream"

/** Prompt configured to make wiremock return an error for downstream LLM APIs. */
const val COMPLETIONS_ERROR_PROMPT = "v1_chat_completions_error"

// Wiremock message content

/** Returned on [COMPLETIONS_COMPLETION_PROMPT]. */
const val COMPLETIONS_RESPONSE = "v1_chat_completions_completion_response"

/** Wiremock response for a title response. */
const val COMPLETIONS_TITLE_RESPONSE = "v1_chat_completions_title_response"

/** Returned on [COMPLETIONS_STREAM_PROMPT]. */
const val COMPLETIONS_STREAM_RESPONSE = "v1_chat_completions_stream_response"

/** Returned on [COMPLETIONS_STREAM_WHITESPACE_PROMPT]. */
const val COMPLETIONS_STREAM_WHITESPACE_RESPONSE =
  "v1_chat_completions_stream_response\nwith whitespace"

private fun generateP8PrivateKey(): String {
  val keyPairGen = KeyPairGenerator.getInstance("EC")
  val ecSpec = ECGenParameterSpec("secp256r1")
  keyPairGen.initialize(ecSpec)
  val keyPair = keyPairGen.generateKeyPair()

  val privateKey: PrivateKey = keyPair.private

  val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(privateKey.encoded)

  return Base64.getEncoder().encodeToString(pkcs8EncodedKeySpec.encoded)
}

val ADMIN_USER =
  User(
    "b4982d27-a238-43c3-819c-cb63784a4cbc",
    "Biblius Glorius Maximus of the Tainted Realms of Forbidden Knowledge",
    "josip.benkodakovic@barrage.net",
    listOf("admin"),
  )

val USER_USER =
  User(
    "b303cc1f-348b-4eef-bebe-1b950e7e7106",
    "Matej Landeka",
    "matej.landeka@barrage.net",
    listOf("user"),
  )

const val VALID_ADMIN_ACCESS_TOKEN =
  "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQyOWI3YWVhN2Y0MzBmNzljYzJkMmE1YzI2MjhmZTMyIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2F1dGhlbnRpay5iYXJyYWdlLmRldi9hcHBsaWNhdGlvbi9vL3JhZ3UvIiwic3ViIjoiYjQ5ODJkMjctYTIzOC00M2MzLTgxOWMtY2I2Mzc4NGE0Y2JjIiwiYXVkIjoiSE5BTnBabFM4VzdzRjluQUE2dVo2NVlFUGExdHlmV214dlQxckpBUCIsImV4cCI6MTc0MTU5MzgxNywiaWF0IjoxNzQxNTkzNTE3LCJhdXRoX3RpbWUiOjE3NDE1OTM0NzMsImFjciI6ImdvYXV0aGVudGlrLmlvL3Byb3ZpZGVycy9vYXV0aDIvZGVmYXVsdCIsInNpZCI6IjMxZWY4N2Y1Mzk3YjY2MzBlYmI4ZDE4MDE2OGQwNDRjYTg0YjI4ODAwMDNiODI0MzdiNGJlY2FhZTc1MjE4OWUiLCJhdmF0YXIiOiJodHRwczovL3l0My5nb29nbGV1c2VyY29udGVudC5jb20veXRjL0FJZHJvX2tLb3pGN3JSM3AzMUNlOHpJQlk2RXA2Q0lWYUtTc3N6R1NoNExLeTVwdmhCWT1zMTYwLWMtay1jMHgwMGZmZmZmZi1uby1yaiIsImVtYWlsIjoiam9zaXAuYmVua29kYWtvdmljQGJhcnJhZ2UubmV0IiwiZW1haWxfdmVyaWZpZWQiOnRydWUsImVudGl0bGVtZW50cyI6WyJhZG1pbiJdLCJyb2xlcyI6WyJhZG1pbiJdLCJuYW1lIjoiQmlibGl1cyBHbG9yaXVzIE1heGltdXMgb2YgdGhlIFRhaW50ZWQgUmVhbG1zIG9mIEZvcmJpZGRlbiBLbm93bGVkZ2UiLCJnaXZlbl9uYW1lIjoiQmlibGl1cyBHbG9yaXVzIE1heGltdXMgb2YgdGhlIFRhaW50ZWQgUmVhbG1zIG9mIEZvcmJpZGRlbiBLbm93bGVkZ2UiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJqZWJlbmtvIiwibmlja25hbWUiOiJqZWJlbmtvIiwiZ3JvdXBzIjpbImF1dGhlbnRpa19hZG1pbnMiLCJyYWd1X2FkbWlucyJdLCJhenAiOiJITkFOcFpsUzhXN3NGOW5BQTZ1WjY1WUVQYTF0eWZXbXh2VDFySkFQIiwidWlkIjoia3JlZXI5b2d6ekJDekc5aVJFQkVuYm9DZTZ6YlRDQ096UFUzUTRwUiJ9.aaCF9JggVPQs716pOveM-bJQ7BHfsWzVmCRDpo2f3ekf0J70OzllY_Oj61jil9G4wBUwGXyCtmvWVkntvEKHrwcEVGczdV_hbsI7YgeXes_FPM2h_8wrmxy3zwPpm-rVGUhXesCyS6XvvJP3gUNZbY_l7bnhLpj6o8KePGa5XY406AeRrF5cxdfX0vEk6jRTibG_yxtMjhed0Cp3gVfCPIWOgaskNZZ3exsDQ0UcDMX14cnOchgIUpDj-nffvVhngGS2s5srwmKFNzlTzzoBDEPhk5all4Q-HfJzjeKS5l5-JlctgHYVTx0bMnY1uJSsRiq55CtNfTBmQe1EZUPuH3UPJGmig4fd0bUPAAcaJqnwF51XufRnnWm1wUD-i0ZsPM6V2_ovjdoG9DYSHJ7OqRfvaCE7kw35shu_UfdDoz2FE0_MM-h2wuRdlzw-VDK3Zewerp_NgMv0ssS5lO3pQw5wRZB6wtOBaXniJHGaMIf2BxgY0ALjiyWaRId8r5dYegipOP4thCoYqPFLu5supAkKePhhq0iym-qBZKcW5jfNVYjnEj5c9_P47hAmddDh11xj-MYAJh7WgGVTLu-klJtPyw9UUae7Pju-YFDK1YIuNsCdIbwPpLHGYhn8dBcQoRnKUsDdGfmLDmp_IUIISKQTnUHACS9IiPvNl4Xo7to"

const val VALID_USER_ACCESS_TOKEN =
  "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQyOWI3YWVhN2Y0MzBmNzljYzJkMmE1YzI2MjhmZTMyIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2F1dGhlbnRpay5iYXJyYWdlLmRldi9hcHBsaWNhdGlvbi9vL3JhZ3UvIiwic3ViIjoiYjMwM2NjMWYtMzQ4Yi00ZWVmLWJlYmUtMWI5NTBlN2U3MTA2IiwiYXVkIjoiSE5BTnBabFM4VzdzRjluQUE2dVo2NVlFUGExdHlmV214dlQxckpBUCIsImV4cCI6MTc0MTYwNDk0OCwiaWF0IjoxNzQxNjA0NjQ4LCJhdXRoX3RpbWUiOjE3NDE2MDQwMDgsImFjciI6ImdvYXV0aGVudGlrLmlvL3Byb3ZpZGVycy9vYXV0aDIvZGVmYXVsdCIsInNpZCI6IjVmMTAzZDY1MGU0NTdmODdlM2I5ZWFiYmIwZmQwZGRjZjU4ODJjZjhjYTExZmY2ZDkyNGJhZGRmY2QzNmM3YjQiLCJhdmF0YXIiOiJodHRwczovL2xoMy5nb29nbGV1c2VyY29udGVudC5jb20vYS9BQ2c4b2NJajlJYmhoZ210MzI4Qk80TmkxazVfMXdyMnNnRzB0b1JYVHVkR3RWZm0wUy1mNlVnPXM5Ni1jIiwiZW1haWwiOiJtYXRlai5sYW5kZWthQGJhcnJhZ2UubmV0IiwiZW1haWxfdmVyaWZpZWQiOnRydWUsImVudGl0bGVtZW50cyI6WyJ1c2VyIl0sInJvbGVzIjpbInVzZXIiXSwibmFtZSI6Ik1hdGVqIExhbmRla2EiLCJnaXZlbl9uYW1lIjoiTWF0ZWogTGFuZGVrYSIsInByZWZlcnJlZF91c2VybmFtZSI6Im1hdGVqLmxhbmRla2FAYmFycmFnZS5uZXQiLCJuaWNrbmFtZSI6Im1hdGVqLmxhbmRla2FAYmFycmFnZS5uZXQiLCJncm91cHMiOlsicmFndV91c2VycyJdLCJhenAiOiJITkFOcFpsUzhXN3NGOW5BQTZ1WjY1WUVQYTF0eWZXbXh2VDFySkFQIiwidWlkIjoiRXJWTlBYZVlBMkNPZ0VybGJXWEExa3BWUU1temhaYkYweWNhM0RoRCJ9.FfIWiDDJf6w6V8L__mZVXipq1OPm8Kbml3jlAv31NVwxZChlt8LAk9-ep5MzZXWpBy6fO2bZh0dSRzxyYeFyLdhPkd8nBN45WUJ2FxS_DELl-uaA8C56FY64Pdmiyq_CqQZnBNSaWMOItyog9kKhwE1tRo1Sz7Xg_3uzhAtDBbF2OoZQ0_iG90XmnyzBfUMLryS5ixK5AwhCjjFnYXVQufDVcX7kHR3VDhJmlQmAck7E_FoTRaOY8vtjBjHocRBjK7unlxldSVjX-W_tBwrExXlr_79hYTsLAQVjTqoH30W-nPY0YYa1ZXJcBhQP6NHwh43ZWyiixHdXfsbxQ0k5W8_cvrcXobhAtjYpEbdk1dE0l_tcTe6z6BMvTAupX8fpqiYAHijd0pbLH6lTyb09Y_M66fLOic30_gwRX5oL3XnVvoFB9IswrZAqROJNzb1DrAU40RhVn8-gr25hjnB1VM8Opbs1E8GWEt1Ru3CxHz7bKWIOPzmmeHESoi8a3FneDSyjKMlhTVS3CVOnrvKv2fF7OVq2_TA0f6vcz7r2E57o83ffXANiQ5koWM1C6_6w7EfDHcC-u-4adwPPwQ32XG8nduGhDJmy_6hGTMY8bU6IjKmXBL48l50uBWoFaSqt__m3zDrzst4k_QNQ8lj_5zcQHgTGPb_RxQY66LE055s"
