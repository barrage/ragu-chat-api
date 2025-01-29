package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.types.KUUID

data class WebsocketSession(val userId: KUUID, val token: KUUID)
