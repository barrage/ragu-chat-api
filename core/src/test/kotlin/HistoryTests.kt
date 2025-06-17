import com.knuddels.jtokkit.Encodings
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.TokenBasedHistory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// 10 tokens
const val TEN_TOKENS = "foo bar qux fux lux jbgxd"

// 8 tokens
const val MESSAGE_1 = "Pee is stored in the balls."

// 6 tokens
const val RESPONSE_1 = "It indubitably is."

// 57 tokens
const val MESSAGE_2 =
  "I returned and saw under the sun, that the race is not to the swift, nor the battle to the strong, neither yet bread to the wise, nor yet riches to men of understanding, nor yet favour to men of skill; but time and chance happeneth to them all."

// 42 tokens
const val RESPONSE_2 =
  "Objective consideration of contemporary phenomena compels the conclusion that success or failure in competitive activities exhibits no tendency to be commensurate with innate capacity, but that a considerable element of the unpredictable must invariably be taken into account."

class HistoryTests {
  val tokenizer = Encodings.newDefaultEncodingRegistry().getEncodingForModel("gpt-4o").get()

  @Test
  fun trimsOldestMessagePairWhenNewMessagesExceedTokenLimit() {
    val history =
      TokenBasedHistory(messages = mutableListOf(), tokenizer = tokenizer, maxTokens = 30)

    val input =
      mutableListOf(
        ChatMessage.user(MESSAGE_1),
        ChatMessage.assistant(RESPONSE_1, finishReason = FinishReason.Stop),
      )
    history.add(input)
    assertEquals(input, history.messages())

    val input2 =
      mutableListOf(
        ChatMessage.user(MESSAGE_2),
        ChatMessage.assistant(RESPONSE_2, finishReason = FinishReason.Stop),
      )
    history.add(input2)
    assertEquals(input2, history.messages())

    history.add(input)
    assertEquals(input, history.messages())

    history.add(input)
    assertEquals(input + input, history.messages())
  }

  @Test
  fun ensuresUserMessageWillAlwaysBeTheFirstMessage() {
    // 105 configured to fit MESSAGE_2, RESPONSE_2, and RESPONSE_1
    // Asserts that even when all messages fit into the maxTokens boundary, the user message
    // (MESSAGE_2) will always be kept as the first one and all previous messages will be
    // removed.
    val history =
      TokenBasedHistory(messages = mutableListOf(), tokenizer = tokenizer, maxTokens = 105)

    val input =
      mutableListOf(
        ChatMessage.user(MESSAGE_1),
        ChatMessage.toolResult(TEN_TOKENS, null),
        ChatMessage.assistant(RESPONSE_1, finishReason = FinishReason.Stop),
      )
    history.add(input)
    assertEquals(input, history.messages())

    val input2 =
      mutableListOf(
        ChatMessage.user(MESSAGE_2),
        ChatMessage.assistant(RESPONSE_2, finishReason = FinishReason.Stop),
      )
    history.add(input2)
    assertEquals(input2, history.messages())
  }
}
