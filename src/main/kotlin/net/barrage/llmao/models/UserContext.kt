package net.barrage.llmao.models

import io.ktor.util.*

data class UserContext(val currentUser: User)

/** Key to use for obtaining users from requests validated by the session middleware. */
val RequestUser = AttributeKey<User>("User")
