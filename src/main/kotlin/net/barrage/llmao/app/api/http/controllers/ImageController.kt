package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.storage.AVATARS_PATH
import net.barrage.llmao.core.storage.BlobStorage

fun Route.avatarRoutes(imageStorage: BlobStorage<Image>) {
  get("/${AVATARS_PATH}/{imagePath}") {
    val imagePath = call.parameters["imagePath"]!!

    val image = imageStorage.retrieve("${AVATARS_PATH}/$imagePath")

    if (image == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }

    call.response.header(
      HttpHeaders.ContentDisposition,
      ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, imagePath)
        .toString(),
    )

    call.respondBytes(image.data, ContentType.parse(image.type.contentType()), HttpStatusCode.OK)
  }
}
