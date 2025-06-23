package net.barrage.llmao.app.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.blob.ATTACHMENTS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image

fun Route.administrationBlobRoutes(imageStorage: BlobStorage<Image>) {
  get("/admin/attachments/images/{path}", getImageAttachment()) {
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

private fun getImageAttachment(): RouteConfig.() -> Unit = {
  tags("admin/attachments/images")
  description = "Retrieve attachment"
  request {
    pathParameter<String>("path") {
      description = "Attachment path"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e.jpeg" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "Image retrieved successfully"
        body<ByteArray> {}
      }
    HttpStatusCode.NotFound to
      {
        description = "Image not found"
        body<AppError> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving attachment"
        body<AppError> {}
      }
  }
}
