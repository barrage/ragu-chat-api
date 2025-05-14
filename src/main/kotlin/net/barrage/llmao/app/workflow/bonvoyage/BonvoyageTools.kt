package net.barrage.llmao.app.workflow.bonvoyage

import net.barrage.llmao.core.llm.ToolDefinition

class BonvoyageToolExecutor(
    private val weatherApi: Unit
) {
    fun tools(): List<ToolDefinition> {
        return listOf()
    }
}
