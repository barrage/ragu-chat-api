package net.barrage.llmao.services

import net.barrage.llmao.models.SessionData
import net.barrage.llmao.repositories.SessionRepository
import net.barrage.llmao.serializers.KUUID

class SessionService {
  private val sessionRepository = SessionRepository()

  fun store(sessionId: KUUID, userId: KUUID) {
    sessionRepository.create(sessionId, userId)
  }

  fun get(sessionId: KUUID): SessionData? {
    return sessionRepository.get(sessionId)
  }

  fun extend(id: KUUID) {
    sessionRepository.extend(id)
  }

  fun expire(id: KUUID) {
    sessionRepository.expire(id)
  }
}
