package net.barrage.llmao.app.auth.carnet

import kotlinx.serialization.Serializable

@Serializable class CarnetUserData(val sub: String, val email: String)
