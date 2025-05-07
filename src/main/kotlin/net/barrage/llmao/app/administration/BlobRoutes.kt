package net.barrage.llmao.app.administration

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image

fun Route.administrationBlobRouter(imageStorage: BlobStorage<Image>) {
  get("/admin/attachments/images/{path}") {
    val imagePath = call.parameters["path"]!!

    val image = imageStorage.retrieve("${ATTACHMENTS_PATH}/$imagePath")

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
