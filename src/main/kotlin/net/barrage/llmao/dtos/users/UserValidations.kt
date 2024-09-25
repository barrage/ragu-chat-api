package net.barrage.llmao.dtos.users

fun validateEmail(email: String): String? {
  return when {
    email.isBlank() -> "email must not be empty"
    !email.matches(Regex("^[\\w\\-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) ->
      "email must be a valid email address"
    else -> null
  }
}

fun validateNotEmpty(param: String, fieldName: String): String? {
  return when {
    param.isBlank() -> "$fieldName must not be empty"
    else -> null
  }
}
