package net.barrage.llmao.app.api.http.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.application
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.string

fun Route.thirdPartyRoutes() {
  val config = application.environment.config

  get("/apple-app-site-association") {
    val appleAppSiteAssociation =
      AppleAppSiteAssociation(
        applinks =
          AppLinks(
            details =
              listOf(
                AppDetail(
                  appID = config.property("apple.appID").getString(),
                  paths = listOf("/oauthredirect"),
                ),
                AppDetail(
                  appID = config.property("multiplatform.ios.appID").getString(),
                  paths = listOf("/oauthredirect"),
                ),
              )
          )
      )

    val jsonString = Json.encodeToString(appleAppSiteAssociation)
    call.respond(HttpStatusCode.OK, jsonString)
  }

  get("/.well-known/assetlinks.json") {
    val assetLinks =
      listOf(
        AssetLink(
          relation = listOf("delegate_permission/common.handle_all_urls"),
          target =
            AssetLinkTarget(
              namespace = config.string("android.namespace"),
              packageName = config.string("android.packageName"),
              sha256CertFingerprints = config.property("android.sha256CertFingerprints").getList(),
            ),
        )
      )

    call.respond(HttpStatusCode.OK, Json.encodeToString(assetLinks))
  }
}
