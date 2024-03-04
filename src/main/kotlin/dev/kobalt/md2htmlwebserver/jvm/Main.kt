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
    val configPath by parser.option(
        type = ArgType.String,
        fullName = "configPath",
        shortName = "conf",
        description = "Path of configuration JSON file"
    )
    parser.parse(args)
    val mainScope = CoroutineScope(Dispatchers.Main)
    configPath?.let { Json.parseToJsonElement(Path(it).readText()).jsonArray }?.mapNotNull { element ->
        ifLet(
            element.jsonObject["port"]?.jsonPrimitive?.intOrNull,
            element.jsonObject["host"]?.jsonPrimitive?.contentOrNull,
            element.jsonObject["path"]?.jsonPrimitive?.contentOrNull,
            element.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        ) { port, host, path, name ->
            mainScope.async(
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
                        exception<Throwable> { call, cause ->
                            cause.printStackTrace()
                            runCatching {
                                call.application.storage.fromStatus(HttpStatusCode.InternalServerError.value).toFile()
                                    .let {
                                        call.respond(HttpStatusCode.InternalServerError, LocalFileContent(it))
                                    }
                            }.onFailure {
                                call.respond(HttpStatusCode.InternalServerError, "")
                            }
                        }
                    }
                    install(Routing) {
                        route("{path...}") {
                            get {
                                call.parameters.getAll("path")?.joinToString("/")?.let { path ->
                                    application.storage.fromPath(path)?.toFile()?.let {
                                        call.respondFile(it)
                                    }
                                } ?: run {
                                    application.storage.fromStatus(HttpStatusCode.NotFound.value).toFile().let {
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
    }?.awaitAll()
}