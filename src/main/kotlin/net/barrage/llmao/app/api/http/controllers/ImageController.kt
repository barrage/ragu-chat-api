package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.storage.ImageStorage

fun Route.imageRoutes(imageStorage: ImageStorage) {
  get("/avatars/{imagePath}") {
    val imagePath = call.parameters["imagePath"]
    val image = imagePath?.let { imageStorage.retrieve(it) }

    if (image == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }

    call.response.header(
      HttpHeaders.ContentDisposition,
      ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, image.name)
        .toString(),
    )

    call.respondBytes(image.data, ContentType.parse(image.type.toContentType()), HttpStatusCode.OK)
  }
}
