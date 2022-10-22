package org.jason.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.json.JSONObject

suspend fun ApplicationCall.respondJson(block: (JSONObject.() -> Unit)? = null) {
    val obj = JSONObject()
    block?.invoke(obj)
    respondText(obj.toString(2), ContentType.Application.Json, HttpStatusCode.OK)
}
