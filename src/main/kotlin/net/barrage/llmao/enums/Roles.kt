package net.barrage.llmao.enums

enum class Roles(val role: String) {
    ADMIN("admin"),
    USER("user");

    companion object {
        fun fromText(text: String): Roles? {
            return entries.find { it.role.equals(text, ignoreCase = true) }
        }
    }
}