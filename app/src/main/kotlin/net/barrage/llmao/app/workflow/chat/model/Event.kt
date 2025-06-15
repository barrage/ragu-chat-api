package net.barrage.llmao.app.workflow.chat.model

import net.barrage.llmao.core.Event
import net.barrage.llmao.core.types.KUUID

data class AgentDeactivated(val agentId: KUUID) : Event
