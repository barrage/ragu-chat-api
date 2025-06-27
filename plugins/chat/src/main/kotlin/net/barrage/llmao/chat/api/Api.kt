package net.barrage.llmao.chat.api

class Api(val admin: AdminApi, val user: PublicApi)

class AdminApi(val chat: AdminChatService, val agent: AdminAgentService)

class PublicApi(val chat: PublicChatService, val agent: PublicAgentService)
