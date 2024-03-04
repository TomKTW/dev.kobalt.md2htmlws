/*
 * dev.kobalt.md2htmlwebserver
 * Copyright (C) 2024 Tom.K
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.kobalt.md2htmlwebserver.jvm

import dev.kobalt.md2htmlwebserver.jvm.extension.ifLet
import dev.kobalt.md2htmlwebserver.jvm.storage.StoragePlugin
import dev.kobalt.md2htmlwebserver.jvm.storage.storage
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.readText

suspend fun main(args: Array<String>) {
    // Apply timezone of runtime to UTC.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    // Parse given arguments.
    val parser = ArgParser(
        programName = "server"
    )
    val list = (0..9).map {
        parser.option(
            type = ArgType.Int,
            fullName = "port$it",
            shortName = "pt$it",
            description = "Port to host the server at"
        )
    }

    val serverPort by parser.option(
        type = ArgType.Int,
        fullName = "port",
        shortName = "pt",
        description = "Port to host the server at"
    )
    val serverHost by parser.option(
        type = ArgType.String,
        fullName = "host",
        shortName = "ht",
        description = "Host value (127.0.0.1 for private, 0.0.0.0 for public access)"
    )
    parser.parse(args)

    Json.parseToJsonElement(Path("./config.json").readText()).jsonArray.mapNotNull { element ->
        ifLet(
            element.jsonObject["port"]?.jsonPrimitive?.intOrNull,
            element.jsonObject["host"]?.jsonPrimitive?.contentOrNull,
            element.jsonObject["path"]?.jsonPrimitive?.contentOrNull,
            element.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        ) { port, host, path, name ->
            GlobalScope.async(
                context = Dispatchers.IO + NonCancellable,
                start = CoroutineStart.LAZY
            ) {
                embeddedServer(CIO, port, host) {
                    install(ForwardedHeaders)
                    install(DefaultHeaders)
                    install(CallLogging)
                    install(Compression)
                    install(StoragePlugin) {
                        this.path = path
                        this.name = name
                    }
                    install(IgnoreTrailingSlash)
                    install(CachingHeaders) {
                        options { _, _ -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 0)) }
                    }
                    install(StatusPages) {
                        exception<Throwable> { call, cause -> cause.printStackTrace() }
                    }
                    install(Routing) {
                        route("{path...}") {
                            get {
                                call.parameters.getAll("path")?.joinToString("/")?.let { path ->
                                    application.storage.fromPath(path)?.toFile()?.let {
                                        call.respondFile(it)
                                    }
                                } ?: run {
                                    application.storage.fromStatus(HttpStatusCode.NotFound.value)?.toFile()?.let {
                                        call.respond(HttpStatusCode.NotFound, LocalFileContent(it))
                                    }
                                }
                            }
                        }
                    }
                }.also {
                    Runtime.getRuntime().addShutdownHook(thread(start = false) {
                        it.stop(0, 10, TimeUnit.SECONDS)
                    })
                }.also {
                    it.start(true)
                }
            }
        }
    }.awaitAll()
}