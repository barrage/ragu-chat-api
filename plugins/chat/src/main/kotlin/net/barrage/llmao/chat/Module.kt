package net.barrage.llmao.chat

import io.ktor.server.application.Application
import net.barrage.llmao.core.Plugins

fun Application.plugin() = Plugins.register(ChatPlugin())
