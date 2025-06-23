import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Event

@Serializable
@SerialName("chat.agent_deactivated")
data class AgentDeactivated(val agentId: KUUID) : Event()
