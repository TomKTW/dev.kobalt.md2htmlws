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
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    // Apply timezone of runtime to UTC.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    // Parse given arguments.
    val parser = ArgParser(
        programName = "server"
    )
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
    // Prepare server if port and host are defined.
    ifLet(serverPort, serverHost) { port, host ->
        embeddedServer(CIO, port, host) {
            install(ForwardedHeaders)
            install(DefaultHeaders)
            install(CallLogging)
            install(Compression)
            install(StoragePlugin)
            install(IgnoreTrailingSlash)
            install(CachingHeaders) {
                options { _, _ -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600)) }
            }
            install(StatusPages) {
                exception<Throwable> { call, cause -> cause.printStackTrace() }
            }
            install(Routing) {
                route("{path...}") {
                    get {
                        call.parameters.getAll("path")?.joinToString("/")?.let { path ->
                            application.storage.fromPath(path)?.toFile()?.let { call.respondFile(it) }
                        } ?: call.respond(HttpStatusCode.NotFound)
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