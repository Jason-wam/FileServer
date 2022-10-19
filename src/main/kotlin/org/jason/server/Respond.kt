package org.jason.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import org.json.JSONObject
import java.io.File

suspend fun ApplicationCall.respondJson(block: (JSONObject.() -> Unit)? = null) {
    val obj = JSONObject()
    block?.invoke(obj)
    respondText(obj.toString(2), ContentType.Application.Json, HttpStatusCode.OK)
}

suspend fun ApplicationCall.respondStream(file: File){
    val stream = file.inputStream()
    val contentType = ContentType.defaultForFilePath(file.absolutePath)

}