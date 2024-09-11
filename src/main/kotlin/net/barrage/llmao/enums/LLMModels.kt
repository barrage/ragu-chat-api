package net.barrage.llmao.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LLMModels(val model: String, val azureModel: String) {
    @SerialName("gpt-3.5-turbo")
    GPT35TURBO("gpt-3.5-turbo", "gpt-35-turbo"),

    @SerialName("gpt-4")
    GPT4("gpt-4", "gpt-4");
}
