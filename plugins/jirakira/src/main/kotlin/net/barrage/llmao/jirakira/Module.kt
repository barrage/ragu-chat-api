package net.barrage.llmao.jirakira

import io.ktor.server.application.Application
import net.barrage.llmao.core.Plugins

fun Application.plugin() = Plugins.register(JiraKiraPlugin())
