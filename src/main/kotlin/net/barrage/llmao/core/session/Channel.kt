package net.barrage.llmao.core.session

import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.error.AppError

interface Channel {
  suspend fun emitChunk(chunk: TokenChunk)

  suspend fun emitError(error: AppError)

  suspend fun emitServer(message: ServerMessage)

  fun close()
}
