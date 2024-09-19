package net.barrage.llmao.weaviate

import io.ktor.server.config.*
import net.barrage.llmao.models.WeaviateConfig

object WeaviteLoader {
    lateinit var weaver: Weaver

    fun init(config: ApplicationConfig) {
        weaver = Weaver(
            WeaviateConfig(
                config.property("weaviate.host").getString(),
                config.property("weaviate.scheme").getString()
            )
        )
    }
}