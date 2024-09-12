package net.barrage.llmao.weaviate

import io.ktor.server.config.*
import net.barrage.llmao.models.WeaviateConfig

fun loadWeaviate(config: ApplicationConfig): Weaver {
    val weaviateConfig = WeaviateConfig(
        host = config.property("weaviate.host").getString(),
        scheme = config.property("weaviate.scheme").getString()
    )
    return Weaver(weaviateConfig)
}