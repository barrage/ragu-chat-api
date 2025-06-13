package net.barrage.llmao.core.http

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthKeyLocation
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.core.data.TypeData
import io.github.smiley4.schemakenerator.core.data.TypeName
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.analyzeTypeUsingReflection
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.data.EnumConstType
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.withTitle
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.swagger.v3.oas.models.media.Schema
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.types.KUUID

fun Route.openApiRoutes() {
    route("openapi.json") { openApi() }
    route("swagger-ui") { swaggerUI("/openapi.json") }
}

fun Application.configureOpenApi() {
    install(OpenApi) {
        info {
            title = "Ragu API"
            version = "latest"
            description = "Ragu API"
        }
        server {
            url = "http://localhost:42069"
            description = "Local Server"
        }
        server {
            url = "https://llmao-kotlin-api-development.barrage.dev"
            description = "Development Server"
        }
        security {
            securityScheme("jwt") {
                name = "access_token"
                type = AuthType.OAUTH2
                location = AuthKeyLocation.COOKIE
            }
            defaultSecuritySchemeNames = listOf("jwt")
        }
        schemas {
            generator = { type ->
                type
                    .collectSubTypes(10)
                    .analyzeTypeUsingReflection {
                        enumConstType = EnumConstType.TO_STRING
                        custom(KUUID::class) { id ->
                            TypeData(
                                id = id,
                                identifyingName = TypeName("KUUID", short = "KUUID"),
                                descriptiveName = TypeName("A UUID", short = "UUID"),
                            )
                        }
                        custom(PropertyUpdate::class) { id ->
                            TypeData(
                                id = id,
                                identifyingName = TypeName(
                                    "PropertyUpdate",
                                    short = "PropertyUpdate"
                                ),
                                descriptiveName =
                                    TypeName(
                                        "A primitive number or string with update semantics. Undefined means leave as is, null means remove, value means update.",
                                        short = "Always deserialized as the primitive it wraps.",
                                    ),
                            )
                        }
                    }
                    .generateSwaggerSchema()
                    .handleCoreAnnotations()
                    .withTitle(TitleType.SIMPLE)
                    .compileReferencingRoot()
            }

            schema(
                "KUUID",
                schema =
                    Schema<KUUID>().apply {
                        type = "string"
                        format = "uuid"
                    },
            )

            schema(
                "PropertyUpdate",
                schema =
                    Schema<PropertyUpdate<*>>().apply {
                        types = setOf("string", "number")
                        description =
                            """
              Represents update semantics for nullable properties:
              - If the field is omitted (undefined), the property remains unchanged
              - If the field is null, the property will be removed
              - If the field has a value, the property will be updated to that value
            """
                                .trimIndent()
                    },
            )
        }
    }
}

/** Utility for generating OpenAPI spec for query param pagination. */
fun RequestConfig.queryPaginationSort() {
    queryParameter<Int>("page") {
        description = "Page number for pagination"
        required = false
        example("default") { value = 1 }
    }
    queryParameter<Int>("perPage") {
        description = "Number of items per page"
        required = false
        example("default") { value = 10 }
    }
    queryParameter<String>("sortBy") {
        description = "Sort by field"
        required = false
        example("default") { value = "name" }
    }
    queryParameter<String>("sortOrder") {
        description = "Sort order (asc or desc)"
        required = false
        example("default") { value = "asc" }
    }
}

fun RequestConfig.queryPagination() {
    queryParameter<Int>("page") {
        description = "Page number for pagination"
        required = false
        example("default") { value = 1 }
    }
    queryParameter<Int>("perPage") {
        description = "Number of items per page"
        required = false
        example("default") { value = 10 }
    }
}

fun RequestConfig.queryListChatsFilters() {
    queryParameter<KUUID>("userId") {
        description = "Filter by user ID"
        required = false
    }
    queryParameter<KUUID>("agentId") {
        description = "Filter by agent ID"
        required = false
    }
    queryParameter<String>("title") {
        description = "Filter by chat title"
        required = false
    }
}

fun RequestConfig.queryListAgentsFilters() {
    queryParameter<String>("name") {
        description = "Filter by name"
        required = false
    }
    queryParameter<Boolean>("active") {
        description = "Filter by active status"
        required = false
    }
}
