package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.storage.ImageStorage

fun Route.imageRoutes(imageStorage: ImageStorage) {
  get("/avatars/{imageName}") {
    val imageName = call.parameters["imageName"]
    val image = imageName?.let { imageStorage.retrieve(it) }

    if (image == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }

    call.response.header(
      HttpHeaders.ContentDisposition,
      ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, image.name)
        .toString(),
    )

    call.respondBytes(image.data, ContentType.parse(image.contentType), HttpStatusCode.OK)
  }
}
