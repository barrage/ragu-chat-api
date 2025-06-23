package net.barrage.llmao

import com.infobip.ApiClient
import com.infobip.ApiKey
import com.infobip.BaseUrl
import com.infobip.api.WhatsAppApi
import com.infobip.model.WhatsAppInteractiveBodyContent
import com.infobip.model.WhatsAppInteractiveButtonsActionContent
import com.infobip.model.WhatsAppInteractiveButtonsContent
import com.infobip.model.WhatsAppInteractiveButtonsMessage
import com.infobip.model.WhatsAppInteractiveReplyButtonContent
import com.infobip.model.WhatsAppTextContent
import com.infobip.model.WhatsAppTextMessage
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.PluginConfiguration
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.input.whatsapp.model.InfobipResponse
import net.barrage.llmao.core.input.whatsapp.model.InfobipResult
import net.barrage.llmao.core.input.whatsapp.model.Message
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.string
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.WorkflowAgent

const val HGK_WORKFLOW_ID = "HGK"
private val CHAT_CONTEXT =
  """Ti si AI agent HGK za podršku novim poduzetnicima, startupima i obrtnicima u pokretanju poslovanja.
            | Razvijen si u suradnji s Hrvatskom gospodarskom komorom (HGK) s ciljem da pružiš personaliziranu i sveobuhvatnu podršku svima koji planiraju pokrenuti vlastiti posao u Hrvatskoj — bilo da se radi o startupu, obrtu ili trgovačkom društvu.
            | Radiš s fizičkim osobama koje tek planiraju formalno osnovati poslovni subjekt, pa se ne kategoriziraju unaprijed kao mikro, mali ili srednji poduzetnici.
            | Umjesto toga, nazivaju se "budući poduzetnici", "osnivači", "startupi u nastajanju" ili "pokretači obrta/tvrtki", ovisno o kontekstu.
            | Tvoj cilj je voditi korisnika kroz sve ključne korake potrebne za osnivanje obrta, j.d.o.o.-a, d.o.o.-a ili startup projekta.
            | Pružiti jasne, pravno i operativno točne informacije temeljene na zakonodavstvu RH. Koristiti informacije iz relevantnih javnih izvora (npr. hitro.hr) bez eksplicitnog navođenja izvora.
            | Prikupiti sve relevantne podatke o korisniku tijekom interakcije i omogućiti: automatsku izradu korisničkog računa na platformi digitalnakomora.hr, ili jednostavno preusmjeravanje korisnika s već pripremljenim podacima.
            | Koraci koje pokrivaš: 
            | - Ispitivanje spremnosti korisnika: Kakvu djelatnost želi pokrenuti? Ima li izrađen poslovni plan? Planira li zapošljavanje? Poslovanje isključivo online ili fizička lokacija? 
            | - Odabir pravnog oblika: Usporedba obrta, j.d.o.o.-a, d.o.o.-a, zadruge ili udruge. Prednosti i nedostaci svake opcije.
            | - Priprema dokumentacije: Ime buduće tvrtke, šifra djelatnosti (NKD), adresa, podaci osnivača, kapital itd.
            | - Postupak osnivanja: Opis procesa putem e-Građani portala, javnog bilježnika, FINA-e, uključujući cijene, rokove i administrativne zahtjeve.
            | - Otvaranje računa: Što traže banke i koje opcije postoje za poslovni račun.
            | - Porezne obveze: Razlika između paušalnog oporezivanja, dohotka i dobiti. Kada i kako prijaviti djelatnost Poreznoj upravi.
            | - Računovodstveni i administrativni aspekti: Kada je potrebno angažirati knjigovođu. Vođenje poslovnih knjiga, fiskalizacija i eRačuni.
            | - Zapošljavanje i prava radnika: Kako prijaviti zaposlenike, koji su obavezni doprinosi, minimalni uvjeti ugovora o radu.
            | - Digitalne usluge: Certifikati, ePorezna, e-Građani, poslovni alati i ERP rješenja.
            | - Završni korak – članstvo u Digitalnoj komori HGK: Agent priprema sve podatke na temelju razgovora.
            | Korisniku se nudi: automatska izrada korisničkog računa na digitalnakomora.hr, ili preusmjeravanje na platformu s već pripremljenim podacima.
            | Koristi hrvatski jezik, prilagođen korisnicima bez prethodnog iskustva u poslovanju. Jezik mora biti empatičan, informativan i poticajan. 
            | Ne smiješ referirati izvore poput hitro.hr direktno, ali možeš koristiti njihove informacije.
            | Trenutno pricas s korisnikom koji zeli zapoceti tvrtku."""
    .trimMargin()
private val AGGREGATION_CONTEXT =
  """
    Tvoj zadatak je da izdvojiš sve relevantne informacije iz razgovora s korisnikom i spremiš ih u strukturiranu formu.
    | Zadatak ti je izdvojiti sljedeće informacije:
    | - Stanje ideje korisnika
    | - Veličina tvrtke
    | - Vrsta djelatnosti
    | - Broj zaposlenika
    | - Vrsta trgovine
    | - Lokacija tvrtke
    | - Vrsta poslovanja
    | - Stanje računovodstva
    | - Vrsta bankovnog računa
    | Jedini nacin na koji korisnik moze odgovarati na ova pitanja je preko dugmeta koji se prikazuje korisniku.
    | Budi pristojan i nemoj pozdravljati korisnika.
"""
    .trimMargin()

class HgkPlugin() : Plugin {
  private lateinit var whatsAppApi: WhatsAppApi

  private lateinit var providers: ProviderState

  /** Maps user IDs to their phone numbers for WhatsApp */
  val state: MutableMap<String, HgkState> = mutableMapOf()

  val histories: MutableMap<String, ChatHistory> = mutableMapOf()

  val companies: MutableMap<String, PodaciZaOtvaranjeTvrtke> = mutableMapOf()

  private val log = KtorSimpleLogger("hgk.HgkPlugin")

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    whatsAppApi =
      WhatsAppApi(
        ApiClient.forApiKey(ApiKey.from(config.string("infobip.apiKey")))
          .withBaseUrl(BaseUrl.from(config.string("infobip.endpoint")))
          .build()
      )
    providers = state.providers
  }

  override fun describe(settings: ApplicationSettings): PluginConfiguration {
    return PluginConfiguration(
      id = id(),
      description = "Asistent za obrtanje obrta",
      settings = mapOf(),
    )
  }

  override fun id(): String {
    return HGK_WORKFLOW_ID
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    route("/hgk") {
      post("/webhook") {
        val data = call.receive<InfobipResponse>()
        handleIncomingMessage(data)
        call.respond(HttpStatusCode.OK)
      }
    }
  }

  override fun migrate(config: ApplicationConfig) {}

  private fun agent(userNumber: String): HgkAgent {
    return HgkAgent(
      inferenceProvider = providers.llm["openai"],
      tokenTracker =
        TokenUsageTrackerFactory.newTracker(
          userNumber,
          userNumber,
          HGK_WORKFLOW_ID,
          KUUID.randomUUID(),
        ),
      history =
        histories[userNumber]
          ?: TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = Encoder.tokenizer("gpt-4o-mini")!!,
            maxTokens = 16_000,
          ),
    )
  }

  suspend fun handleIncomingMessage(input: InfobipResponse) {
    for (result in input.results) {
      handleMessage(result)
    }
  }

  private suspend fun handleMessage(result: InfobipResult) {
    if (result.message is Message.Text && (result.message as Message.Text).text == "/reset") {
      log.debug("Resetting state for {}", result.from)
      state.remove(result.from)
      companies.remove(result.from)
      histories.remove(result.from)
      return
    }

    val state = state[result.from]

    if (state == null) {
      sendIntroduction(result.from, result.to)
      this.state[result.from] = HgkState.IntroductionSent
      return
    }

    log.debug(
      "Handling message: (state = {}, from = {}, msg = {})",
      state,
      result.from,
      result.message,
    )

    when (state) {
      is HgkState.IntroductionSent -> {
        handleIntroductionResponse(result)
      }

      is HgkState.Onboarding -> {
        when (result.message) {
          is Message.Text -> {
            when (state.state) {
              is OnboardingState.WorkTypePhase ->
                handleWorkTypeResponse(result.from, result.to, result.message as Message.Text)

              is OnboardingState.LocationPhase ->
                handleLocationResponse(result.from, result.to, result.message as Message.Text)

              else ->
                log.warn(
                  "{} - Unsupported onboarding state ({}) for message {}",
                  state.state,
                  result.message,
                )
            }
          }

          is Message.ButtonReply -> {
            when (state.state) {
              is OnboardingState.IdeaPhase ->
                handleIdeaResponse(result.from, result.to, result.message as Message.ButtonReply)

              is OnboardingState.CompanySizePhase ->
                handleCompanySizeResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.EmployeeAmountPhase ->
                handleEmployeeAmountResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.CommerceTypePhase ->
                handleCommerceTypeResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.BusinessTypePhase ->
                handleBusinessTypeResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.IncomeEstimatePhase ->
                handleIncomeEstimateResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.AccountingStatePhase ->
                handleAccountingStateResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              is OnboardingState.BankAccountPhase ->
                handleBankAccountResponse(
                  result.from,
                  result.to,
                  result.message as Message.ButtonReply,
                )

              else ->
                log.warn(
                  "Unsupported onboarding state ({}) for message {}",
                  state.state,
                  result.message,
                )
            }
          }
        }
      }

      is HgkState.OnboardingComplete -> {
        if (result.message !is Message.Text) {
          log.warn("Unsupported message type: {}", result.message)
          return
        }

        val response =
          agent(result.from)
            .completion(
              CHAT_CONTEXT,
              """Trenutno korisnikovo stanje: ${state.info}
                        | Ako korisnik postavlja pitanje vezano za trenutni status onboarding-a, iskoristi prethodno
                        | navedeno stanje da pristojno odgovoris na njegovo pitanje.
                        | 
                        | ${(result.message as Message.Text).text}
                    """
                .trimMargin(),
            )

        whatsAppApi
          .sendWhatsAppTextMessage(
            WhatsAppTextMessage().apply {
              this.to = result.from
              this.from = result.to
              this.content =
                WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
            }
          )
          .execute()

        addToHistory(result.from, response)
      }
    }
  }

  private suspend fun handleIntroductionResponse(result: InfobipResult) {
    when (result.message) {
      is Message.Text -> {
        // history is loaded with agent
        val agent = agent(result.from)

        val response = agent.completion(CHAT_CONTEXT, (result.message as Message.Text).text)

        whatsAppApi
          .sendWhatsAppTextMessage(
            WhatsAppTextMessage().apply {
              this.to = result.from
              this.from = result.to
              this.content =
                WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
            }
          )
          .execute()

        addToHistory(result.from, response)
      }

      is Message.ButtonReply -> {
        when ((result.message as Message.ButtonReply).id) {
          "START_YES" -> {
            val response =
              agent(result.from)
                .completion(
                  CHAT_CONTEXT,
                  """Korisnik želi započeti onboarding postupak. Trenutni zadatak ti je razumjeti ideju koju korisnik želi pokrenuti.
                                | Korisnik na izbor ima 2 opcije za odabir:
                                | 1 – Imam ideju
                                | 2 – Imam poslovni plan
                                | Napiši kratku poruku u kojoj ga pitaš koju opciju želi odabrati. 
                                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
                                | Nemoj pozdravljati korisnika jer je to već urađeno u prethodnoj poruci.
                            """
                    .trimMargin(),
                )

            val message =
              createInteractiveReplyMessage(
                result.to,
                result.from,
                response.last().content!!.text(),
                listOf("IDEA_1" to "Imam ideju", "IDEA_2" to "Imam poslovni plan"),
              )

            whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()

            addToHistory(result.from, response)

            log.debug("Starting onboarding for {}", result.from)

            state[result.from] = HgkState.Onboarding(OnboardingState.IdeaPhase)
            companies[result.from] =
              PodaciZaOtvaranjeTvrtke(
                stanjeRealizacije = null,
                velicinaTvrke = null,
                struka = null,
                kolicinaZaposlenika = null,
                vrstaTrgovanja = null,
                mjestoPoslovanja = null,
                pausalniObrt = null,
                ocekivaniPrihod = null,
                imaRacunovodju = null,
                imaPoslovniBankovniRacun = null,
              )
          }

          "START_NO" -> {
            val response =
              agent(result.from)
                .completion(
                  CHAT_CONTEXT,
                  "Korisnik je odbio započeti onboarding postupak. Napiši poruku u kojoj mu ukazuješ na to da si spreman pomoći s bilo čime.",
                )

            whatsAppApi
              .sendWhatsAppTextMessage(
                WhatsAppTextMessage().apply {
                  this.to = result.from
                  this.from = result.to
                  this.content =
                    WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
                }
              )
              .execute()

            addToHistory(result.from, response)
          }
        }
      }
    }
  }

  private suspend fun handleIdeaResponse(from: String, to: String, result: Message.ButtonReply) {
    log.debug("Handling idea response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.stanjeRealizacije = result.title

    val response =
      agent(from)
        .completion(
          CHAT_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti veličinu tvrtke koju korisnik želi pokrenuti.
                | Napiši poruku koja će korisniku pitati koju veličinu tvrtke želi pokrenuti.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - Obrt
                | 2 - J.d.o.o
                | 3 - D.o.o
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf(
          "COMPANY_SIZE_1" to "Obrt",
          "COMPANY_SIZE_2" to "J.d.o.o",
          "COMPANY_SIZE_3" to "D.o.o",
        ),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.CompanySizePhase)
  }

  private suspend fun handleCompanySizeResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling company size response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $result.from")
      return
    }

    info.velicinaTvrke = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti vrstu djelatnosti koju korisnik želi pokrenuti, odnosno cime ce se tvrtka baviti.
                | Napiši poruku koja će korisnika pitati koju vrstu djelatnosti želi pokrenuti.
            """
            .trimMargin(),
        )

    whatsAppApi
      .sendWhatsAppTextMessage(
        WhatsAppTextMessage().apply {
          this.to = from
          this.from = to
          this.content =
            WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
        }
      )
      .execute()

    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.WorkTypePhase)
  }

  private suspend fun handleWorkTypeResponse(from: String, to: String, result: Message.Text) {
    log.debug("Handling work type response for {}: {}", from, result.text)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.struka = result.text

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti broj zaposlenika s kojima korisnik želi pokrenuti tvrtku.
                | Napiši poruku koja će korisnika pitati s koliko zaposlenika želi pokrenuti tvrtku.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - Radim sam
                | 2 - 1 zaposlenik
                | 3 - 2+ zaposlenika
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf(
          "EMPLOYEE_AMOUNT_1" to "Radim sam",
          "EMPLOYEE_AMOUNT_2" to "1 zaposlenik",
          "EMPLOYEE_AMOUNT_3" to "2+ zaposlenika",
        ),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.EmployeeAmountPhase)
  }

  private suspend fun handleEmployeeAmountResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling employee amount response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.kolicinaZaposlenika = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti vrstu trgovine koju korisnik želi pokrenuti.
                | Napiši poruku koja će korisnika pitati koju vrstu trgovine želi pokrenuti.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - Samo online
                | 2 - Fizička trgovina
                | 3 - Kombinirano
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf(
          "BUSINESS_TYPE_1" to "Samo online",
          "BUSINESS_TYPE_2" to "Fizička trgovina",
          "BUSINESS_TYPE_3" to "Kombinirano",
        ),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.CommerceTypePhase)
  }

  private suspend fun handleCommerceTypeResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling commerce type response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.vrstaTrgovanja = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti lokaciju tvrtke koju korisnik želi pokrenuti.
                | Napiši poruku koja će korisnika pitati na kojoj lokaciji želi pokrenuti tvrtku, npr. u Zagrebu, Splitu, Rijeci itd.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    whatsAppApi
      .sendWhatsAppTextMessage(
        WhatsAppTextMessage().apply {
          this.to = from
          this.from = to
          this.content =
            WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
        }
      )
      .execute()

    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.LocationPhase)
  }

  private suspend fun handleLocationResponse(from: String, to: String, result: Message.Text) {
    log.debug("Handling location response for {}: {}", from, result.text)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.mjestoPoslovanja = result.text

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti zeli li korisnik poslovati kao paušalni obrt.
                | Napiši poruku koja će korisnika pitati da li želi poslovati kao paušalni obrt.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - Da
                | 2 - Ne
                | 3 - Ne znam, objasni
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf("PAUSHAL_YES" to "Da", "PAUSHAL_NO" to "Ne", "PAUSHAL_WTF" to "Ne znam, objasni"),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.BusinessTypePhase)
  }

  private suspend fun handleBusinessTypeResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling business type response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    if (result.id == "PAUSHAL_WTF") {
      val response =
        agent(from)
          .completion(
            CHAT_CONTEXT,
            """Korisnik je odabrao opciju "Ne znam, objasni" za pitanje da li želi poslovati kao paušalni obrt.
                    | Napiši poruku koja će korisniku objasniti što je paušalni obrt i koje su prednosti i nedostaci paušalnog obrta.
                    | Također korisnik i dalje moze odabrati opciju "Da" ili "Ne" iz prethodne poruke, stoga mu objasni da mora stisnuti dugme "Da" ili "Ne"
                    | iz prethodne poruke.
                    | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
                """
              .trimMargin(),
          )

      whatsAppApi
        .sendWhatsAppTextMessage(
          WhatsAppTextMessage().apply {
            this.to = from
            this.from = to
            this.content =
              WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
          }
        )
        .execute()

      addToHistory(from, response)
      return
    }

    info.pausalniObrt = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci korak je razumjeti procjenu prihoda tvrtke koje korisnik želi pokrenuti.
                | Napiši poruku koja će korisnika pitati za procjenu.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - <10,000 EUR
                | 2 - 10,000-100,000 EUR
                | 3 - >100,000 EUR
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf(
          "INCOME_ESTIMATE_1" to "<10,000 EUR",
          "INCOME_ESTIMATE_2" to "10,000-100,000 EUR",
          "INCOME_ESTIMATE_3" to ">100,000 EUR",
        ),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.IncomeEstimatePhase)
  }

  private suspend fun handleIncomeEstimateResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling income estimate response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.ocekivaniPrihod = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Iduci i predzadnji korak je razumjeti stanje računovodstva koje korisnik želi pokrenuti.
                | Napiši poruku koja će korisnika pitati ima li knjigovođu.
                | Korisnik na izbor ima 3 opcije za odabir:
                | 1 - Imam
                | 2 - Trebam preporuku
                | 3 - Nemam
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Ohrabri korisnika da je skoro gotov.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf(
          "ACCOUNTING_STATE_1" to "Imam",
          "ACCOUNTING_STATE_2" to "Trebam preporuku",
          "ACCOUNTING_STATE_3" to "Nemam",
        ),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.AccountingStatePhase)
  }

  private suspend fun handleAccountingStateResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling accounting state response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.imaRacunovodju = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Zadnji korak je razumjeti ima li korisnik poslovni bankovni račun.
                | Napiši poruku koja će korisnika pitati ima li ga.
                | Korisnik na izbor ima 2 opcije za odabir:
                | 1 - Da
                | 2 - Ne
                | Nemoj spominjati izbore u poruci, korisnik ce ih dobiti u obliku dugmeta koji moze pritisnuti.
                | Poruka je namjenjena za WhatsApp platformu, stoga pazi da nije predugačka.
            """
            .trimMargin(),
        )

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        response.last().content!!.text(),
        listOf("BANK_ACCOUNT_1" to "Da", "BANK_ACCOUNT_2" to "Ne"),
      )

    whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.Onboarding(OnboardingState.BankAccountPhase)
  }

  private suspend fun handleBankAccountResponse(
    from: String,
    to: String,
    result: Message.ButtonReply,
  ) {
    log.debug("Handling bank account response for {}: {}", from, result.title)

    val info = companies[from]

    if (info == null) {
      log.warn("Company info not found for $from")
      return
    }

    info.imaPoslovniBankovniRacun = result.title

    val response =
      agent(from)
        .completion(
          AGGREGATION_CONTEXT,
          """Trenutno korisnikovo stanje: $info
                | Onboarding je uspjesno zavrsen. Prikazi korisniku sve podatke koje si izdvojio i pitaj ga da li su podaci točni.
                | U daljnjem razgovoru budi pristojan i ohrabri korisnika da ce mu poslovna ideja donjet puno uspjeha."""
            .trimMargin(),
        )

    val message =
      WhatsAppTextMessage().apply {
        this.to = from
        this.from = to
        this.content = WhatsAppTextContent().apply { this.text = response.last().content!!.text() }
      }

    whatsAppApi.sendWhatsAppTextMessage(message).execute()
    addToHistory(from, response)
    state[from] = HgkState.OnboardingComplete(info)
  }

  private fun sendIntroduction(from: String, to: String) {
    val text =
      """Pozdrav! 👋 Ja sam digitalni asistent HGK.
                | Tu sam kako bih ti pomogao da pokreneš vlastiti posao — bilo da planiraš otvoriti obrt, tvrtku (j.d.o.o. ili d.o.o.), ili startup. 
                | Želiš li da zajedno prođemo kroz kratak vodič koji te vodi od ideje do registracije i članstva na digitalnakomora.hr?
                      """
        .trimMargin()

    val message =
      createInteractiveReplyMessage(
        to,
        from,
        text,
        listOf("START_YES" to "Da, krenimo", "START_NO" to "Ne, hvala"),
      )

    val messageInfo = whatsAppApi.sendWhatsAppInteractiveButtonsMessage(message).execute()

    log.debug("WhatsApp message sent to: {}, status: {}", to, messageInfo.status.description)
  }

  /**
   * Inverts from and to in order to reply to the original sender. Always pass the original to and
   * from from the InfobipResult.
   */
  private fun createInteractiveReplyMessage(
    from: String,
    to: String,
    text: String,
    buttons: List<Pair<String, String>>,
  ): WhatsAppInteractiveButtonsMessage {
    return WhatsAppInteractiveButtonsMessage().apply {
      this.to = to
      this.from = from
      this.content =
        WhatsAppInteractiveButtonsContent().apply {
          this.body = WhatsAppInteractiveBodyContent().apply { this.text = text }
          this.action =
            WhatsAppInteractiveButtonsActionContent().apply {
              buttons(
                buttons.map { (id, title) ->
                  WhatsAppInteractiveReplyButtonContent().apply {
                    this.id = id
                    this.title = title
                  }
                }
              )
            }
        }
    }
  }

  private fun addToHistory(from: String, messages: List<ChatMessage>) {
    histories.computeIfAbsent(
      from,
      { _ ->
        val history =
          TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = Encoder.tokenizer("gpt-4o-mini")!!,
            maxTokens = 16_000,
          )
        history.add(messages)
        history
      },
    )
  }
}

sealed class HgkState {
  data object IntroductionSent : HgkState()

  data class Onboarding(val state: OnboardingState) : HgkState()

  data class OnboardingComplete(val info: PodaciZaOtvaranjeTvrtke) : HgkState()

  val history: ChatHistory =
    TokenBasedHistory(
      messages = mutableListOf(),
      tokenizer = Encoder.tokenizer("gpt-4o-mini")!!,
      maxTokens = 16_000,
    )
}

sealed class OnboardingState {
  data object IdeaPhase : OnboardingState()

  data object CompanySizePhase : OnboardingState()

  data object WorkTypePhase : OnboardingState()

  data object EmployeeAmountPhase : OnboardingState()

  data object CommerceTypePhase : OnboardingState()

  data object LocationPhase : OnboardingState()

  data object BusinessTypePhase : OnboardingState()

  data object IncomeEstimatePhase : OnboardingState()

  data object AccountingStatePhase : OnboardingState()

  data object BankAccountPhase : OnboardingState()
}

data class PodaciZaOtvaranjeTvrtke(
  var stanjeRealizacije: String?,
  var velicinaTvrke: String?,
  var struka: String?,
  var kolicinaZaposlenika: String?,
  var vrstaTrgovanja: String?,
  var mjestoPoslovanja: String?,
  var pausalniObrt: String?,
  var ocekivaniPrihod: String?,
  var imaRacunovodju: String?,
  var imaPoslovniBankovniRacun: String?,
)

class HgkAgent(
  inferenceProvider: InferenceProvider,
  tokenTracker: TokenUsageTracker,
  history: ChatHistory,
) :
  WorkflowAgent(
    inferenceProvider = inferenceProvider,
    completionParameters = ChatCompletionBaseParameters(model = "gpt-4o-mini"),
    tokenTracker = tokenTracker,
    history = history,
    contextEnrichment = null,
  )
