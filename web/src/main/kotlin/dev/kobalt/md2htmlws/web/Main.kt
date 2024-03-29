/*
 * dev.kobalt.md2htmlws
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

package dev.kobalt.md2htmlws.web

import dev.kobalt.md2htmlws.web.storage.StorageConfigEntity
import dev.kobalt.md2htmlws.web.storage.StoragePlugin
import dev.kobalt.md2htmlws.web.storage.storage
import dev.kobalt.md2htmlws.web.storage.toStorageConfigEntity
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

suspend fun main(args: Array<String>) {
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
    val nginxConfigPath by parser.option(
        type = ArgType.String,
        fullName = "nginxConfigPath",
        shortName = "nxcf",
        description = "Path of generated nginx configuration file"
    )
    parser.parse(args)
    //  Configure and start servers from configuration file.
    val mainScope = CoroutineScope(Dispatchers.Main)
    val configList = configPath?.let {
        Json.parseToJsonElement(Path(it).readText()).jsonArray
    }?.mapNotNull {
        it.jsonObject.toStorageConfigEntity()
    }.orEmpty()
    // Generate nginx configuration if path was set for it.
    nginxConfigPath?.let { path ->
        Path(path).writeText(configList.joinToString("\n\n") { it.toNginxConfig() })
    }
    // Prepare and start servers.
    configList.map { config ->
        mainScope.async(
            context = Dispatchers.IO + NonCancellable,
            start = CoroutineStart.LAZY
        ) {
            setupServer(config).also {
                Runtime.getRuntime().addShutdownHook(thread(start = false) {
                    it.stop(0, 10, TimeUnit.SECONDS)
                })
            }.also {
                it.start(true)
            }
        }
    }.awaitAll()
}

/** Returns an instance of server with configuration from given entity .*/
fun setupServer(config: StorageConfigEntity) = embeddedServer(CIO, config.port, config.host) {
    install(ForwardedHeaders)
    install(DefaultHeaders)
    install(CallLogging)
    install(Compression)
    install(StoragePlugin) {
        this.path = config.path
        this.name = config.title
    }
    install(IgnoreTrailingSlash)
    install(CachingHeaders) {
        options { _, _ -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 60)) }
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