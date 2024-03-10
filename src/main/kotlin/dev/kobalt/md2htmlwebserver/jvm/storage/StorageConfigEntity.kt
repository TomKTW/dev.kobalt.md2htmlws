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

package dev.kobalt.md2htmlwebserver.jvm.storage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Represents an entity containing HTTP server configuration.*/
data class StorageConfigEntity(
    /** Port where server would be running on. */
    val port: Int,
    /** Host where server would be running on. */
    val host: String,
    /** Path of directory that contains resources for hosting content. */
    val path: String,
    /** Server name of the server. */
    val name: String,
    /** Title of the website. */
    val title: String
) {

    /** Returns string containing a template of NGINX configuration that can be used. */
    fun toNginxConfig() = """
    |server {
    |        listen 80;
    |        listen 443 ssl;
    |        server_name ${name};
    |        location / {
    |                proxy_pass http://localhost:${port}/;
    |                proxy_set_header Host ${'$'}http_host;
    |                proxy_set_header X-Real-IP ${'$'}remote_addr;
    |                proxy_set_header X-Forwarded-For ${'$'}proxy_add_x_forwarded_for;
    |                server_tokens off;
    |        }
    |        ssl_certificate /etc/letsencrypt/live/${name}/fullchain.pem;
    |        ssl_certificate_key /etc/letsencrypt/live/${name}/privkey.pem;
    |        ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
    |        include /etc/letsencrypt/options-ssl-nginx.conf;
    |}
    """.trimMargin()

}

/** Returns storage configuration entity object from JSON object. Variables in JSON are replaced with their values. */
fun JsonObject.toStorageConfigEntity() = runCatching {
    StorageConfigEntity(
        port = this["port"]?.jsonPrimitive?.intOrNull!!,
        host = this["host"]?.jsonPrimitive?.contentOrNull!!,
        path = this["path"]?.jsonPrimitive?.contentOrNull!!.replace(
            "\$name\$",
            this["name"]?.jsonPrimitive?.contentOrNull!!
        ),
        name = this["name"]?.jsonPrimitive?.contentOrNull!!,
        title = this["title"]?.jsonPrimitive?.contentOrNull!!,
    )
}.getOrNull()