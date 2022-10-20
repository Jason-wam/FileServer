import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.netty.handler.codec.http.HttpResponseStatus
import org.jason.server.respondJson
import org.jason.server.utils.MediaType
import org.jason.server.utils.NetworkUtil
import org.jason.server.utils.ThreadPool
import org.jason.server.utils.toMd5String
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val runDir = System.getProperty("user.dir")
    val propFile = File(runDir, "config.properties")
    if (propFile.exists().not()) {
        ClassLoader.getSystemResourceAsStream("config.properties")?.use {
            propFile.outputStream().use { out ->
                it.copyTo(out)
            }
        }
    }

    val properties = Properties().apply {
        propFile.inputStream().use {
            load(it)
        }
    }

    val ipv4 = NetworkUtil.getLocalIPAddress()
    val pin = properties.getProperty("server.pin", "")
    val port = properties.getProperty("server.port", "8080").toInt()
    LoggerFactory.getLogger("Server").info("port: $port ..")

    val ffmpeg = properties.getProperty("ffmpeg", "")
    if (ffmpeg.isNotBlank()) {
        LoggerFactory.getLogger("Server").info("ffmpeg: $ffmpeg")
    }

    val list = ArrayList<File>()
    properties.getProperty("server.path").split(",").forEach {
        if (it.isNotBlank()) {
            val file = File(it)
            if (file.exists()) {
                list.add(File(it))
                LoggerFactory.getLogger("Server").info("file: $it")
            } else {
                LoggerFactory.getLogger("Server").error("file: $it not found!")
            }
        }
    }

    if (ffmpeg.isNotBlank()) {
        thread {
            fun File.getAllFiles(): List<File> {
                return ArrayList<File>().apply {
                    listFiles()?.forEach {
                        if (it.isDirectory) {
                            addAll(it.getAllFiles())
                        } else {
                            if (MediaType.isVideo(it)) {
                                add(it)
                            }
                        }
                    }
                }
            }

            ThreadPool.instance("thumbnail").setThreads(5)
            list.forEach {
                if (it.isDirectory) {
                    it.getAllFiles().forEach { file ->
                        ThreadPool.instance("thumbnail").addTask {
                            createThumbnail(ffmpeg, file)
                        }
                    }
                } else {
                    createThumbnail(ffmpeg, it)
                }
            }
        }

    }

    LoggerFactory.getLogger("Server").info("Server address: http://$ipv4:$port/")

    embeddedServer(Netty, port, "0.0.0.0") {
        install(PartialContent)
        install(Compression) {
            default()
            excludeContentType(ContentType.Video.Any)
        }

        routing {
            get("/") {
                LoggerFactory.getLogger("Root").info("***GET***")
                LoggerFactory.getLogger("Root").info(context.request.uri)
                LoggerFactory.getLogger("Root").info(context.request.userAgent())

                if (pin.isNotBlank()) {
                    if (call.parameters["pin"] != pin) {
                        call.respondJson {
                            put("code", HttpResponseStatus.BAD_REQUEST.code())
                            put("message", "Pin invalid!")
                        }
                        return@get
                    }
                }

                call.respondJson {
                    put("code", HttpResponseStatus.OK.code())
                    put("path", "/")
                    put("list", JSONArray().apply {
                        list.forEach { file ->
                            put(JSONObject().apply {
                                put("name", file.absolutePath)
                                put("length", file.length())
                                put("isDirectory", file.isDirectory)
                                put("lastModified", file.lastModified())
                                put("absolutePath", file.absolutePath)
                                put("files", file.listFiles()?.size ?: 0)
                                if (file.isDirectory) {
                                    put("url", "http://$ipv4:$port/children?path=" + URLEncoder.encode(file.absolutePath, "utf-8"))
                                } else {
                                    put("url", "http://$ipv4:$port/file?path=" + URLEncoder.encode(file.absolutePath, "utf-8"))
                                    if (MediaType.isVideo(file)) {
                                        createThumbnail(ffmpeg, file)?.also {
                                            put("thumbnail", "http://$ipv4:$port/file?path=" + URLEncoder.encode(it.absolutePath, "utf-8"))
                                        }
                                    }
                                }
                            })
                        }
                    })
                }
            }

            get("/children") {
                LoggerFactory.getLogger("Children").info("***GET***")
                LoggerFactory.getLogger("Children").info(context.request.uri)
                LoggerFactory.getLogger("Children").info(context.request.userAgent())
                if (pin.isNotBlank()) {
                    if (call.parameters["pin"] != pin) {
                        call.respondJson {
                            put("code", HttpResponseStatus.BAD_REQUEST.code())
                            put("message", "Pin invalid!")
                        }
                        return@get
                    }
                }

                val path = call.parameters["path"]
                if (path.isNullOrBlank()) {
                    LoggerFactory.getLogger("Children").error("Path can't be null!")
                    call.respondJson {
                        put("code", HttpResponseStatus.BAD_REQUEST.code())
                        put("message", "Path can't be null!")
                    }
                    return@get
                }

                val file = File(path)
                if (file.isDirectory.not()) {
                    LoggerFactory.getLogger("Children").error("${file.absolutePath} is not directory!")
                    call.respondJson {
                        put("code", HttpResponseStatus.BAD_REQUEST.code())
                        put("message", "This file is not directory!")
                    }
                    return@get
                }

                if (file.exists().not()) {
                    LoggerFactory.getLogger("Children").error("${file.absolutePath} not found!")
                    call.respondJson {
                        put("code", HttpResponseStatus.NOT_FOUND.code())
                        put("message", "File Not Found!")
                    }
                    return@get
                }

                val showHidden = (call.parameters["showHidden"] ?: "false").toBoolean()
                call.respondJson {
                    put("code", HttpResponseStatus.OK.code())
                    put("path", path)
                    put("list", JSONArray().apply {
                        file.listFiles()?.filter {
                            if (it.isHidden) {
                                showHidden
                            } else {
                                true
                            }
                        }?.sortedByDescending {
                            it.isDirectory
                        }?.forEach {
                            put(JSONObject().apply {
                                put("name", it.name)
                                put("length", it.length())
                                put("isDirectory", it.isDirectory)
                                put("lastModified", it.lastModified())
                                put("absolutePath", it.absolutePath)
                                put("files", it.listFiles()?.size ?: 0)
                                if (it.isDirectory) {
                                    put("url", "http://$ipv4:$port/children?path=" + URLEncoder.encode(it.absolutePath, "utf-8"))
                                } else {
                                    put("url", "http://$ipv4:$port/file?path=" + URLEncoder.encode(it.absolutePath, "utf-8"))
                                    if (MediaType.isVideo(it)) {
                                        put("thumbnail", "http://$ipv4:$port/file?path=" + URLEncoder.encode(it.absolutePath, "utf-8"))
                                        createThumbnail(ffmpeg, it)?.also {
                                            put("thumbnail", "http://$ipv4:$port/file?path=" + URLEncoder.encode(it.absolutePath, "utf-8"))
                                        }
                                    }
                                }
                            })
                        }
                    })
                }
            }

            get("/file") {
                LoggerFactory.getLogger("File").info("***GET***")
                LoggerFactory.getLogger("File").info(context.request.uri)
                LoggerFactory.getLogger("File").info(context.request.userAgent())

                if (pin.isNotBlank()) {
                    if (call.parameters["pin"] != pin) {
                        call.respond(HttpStatusCode.BadRequest, "Pin invalid!")
                        return@get
                    }
                }

                val path = call.parameters["path"]
                if (path.isNullOrBlank()) {
                    LoggerFactory.getLogger("File").error("Path can't be null!")
                    call.respond(HttpStatusCode.BadRequest, "Path can't be null!")
                    return@get
                }

                val file = File(path)
                if (file.isDirectory) {
                    LoggerFactory.getLogger("File").error("${file.absolutePath} is directory!")
                    call.respond(HttpStatusCode.BadRequest, "This file is directory!")
                    return@get
                }

                if (file.exists().not()) {
                    LoggerFactory.getLogger("File").error("${file.absolutePath} not found!")
                    call.respond(HttpStatusCode.NotFound, "File not found!")
                    return@get
                }
                call.response.header("Content-Disposition", "inline;filename=${URLEncoder.encode(file.name, "utf-8")}")
                call.respondFile(file)
            }

            post("/upload") {
                LoggerFactory.getLogger("Upload").info("***UPLOAD***")
                LoggerFactory.getLogger("Upload").info(context.request.uri)
                LoggerFactory.getLogger("Upload").info(context.request.userAgent())

                if (pin.isNotBlank()) {
                    if (call.parameters["pin"] != pin) {
                        call.respondJson {
                            put("code", HttpResponseStatus.BAD_REQUEST.code())
                            put("message", "Pin invalid!")
                        }
                        return@post
                    }
                }

                val path = call.parameters["path"]
                if (path.isNullOrBlank()) {
                    LoggerFactory.getLogger("Upload").error("Path can't be null!")
                    call.respondJson {
                        put("code", HttpResponseStatus.BAD_REQUEST.code())
                        put("message", "Path can't be null!")
                    }
                    return@post
                }

                val file = File(path)
                if (file.isDirectory.not()) {
                    LoggerFactory.getLogger("Upload").error("${file.absolutePath} is not directory!")
                    call.respondJson {
                        put("code", HttpResponseStatus.BAD_REQUEST.code())
                        put("message", "This file is not directory!")
                    }
                    return@post
                }

                val outFiles = ArrayList<File>()
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName ?: part.name ?: "${System.currentTimeMillis()}.bin"
                        val fileName = File(file, name)
                        fileName.outputStream().use { out ->
                            part.streamProvider().use { input ->
                                input.copyTo(out)
                            }
                        }
                        outFiles.add(fileName)
                        LoggerFactory.getLogger("Upload").info("file: ${fileName.absolutePath}")
                    }
                }
                call.respondJson {
                    put("code", HttpResponseStatus.OK.code())
                    put("message", "${outFiles.size} files uploaded!")
                    put("list", JSONArray(outFiles))
                }
            }
        }
    }.start(true)
}

private fun createThumbnail(ffmpeg: String, file: File): File? {
    if (ffmpeg.isNotBlank()) {
        val runDir = System.getProperty("user.dir")
        val cacheDir = File(runDir, "cache")
        if (cacheDir.exists().not()) {
            cacheDir.mkdirs()
        }
        val fileName = File(cacheDir, file.absolutePath.toMd5String() + ".jpg")
        if (fileName.exists()) {
            return fileName
        }
        val params = ArrayList<String>()
        params.add(ffmpeg)
        params.add("-i \"${file.absolutePath}\"")
        params.add("-f image2")
        params.add("-an")
        params.add("-y")
        params.add("\"${fileName.absolutePath}\"")

        val command = params.joinToString(" ")
        val process = Runtime.getRuntime().exec(command)

        var line: String?
        val error = StringBuilder()
        val reader = process.errorStream.bufferedReader()
        while (reader.readLine().also { line = it } != null) {
            error.appendLine(line)
        }

        if (process.waitFor() != 0) {
            process.destroy() //0表示正常结束，1：非正常结束
        } else {
            process.destroy()
        }

        if (fileName.exists().not()) {
            LoggerFactory.getLogger("FFmpeg").error("Create thumbnail failed: ${fileName.absolutePath} , trace = $error")
        }
        return fileName
    } else {
        return null
    }
}