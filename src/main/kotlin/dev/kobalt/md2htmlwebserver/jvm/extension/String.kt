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

package dev.kobalt.md2htmlwebserver.jvm.extension

import kotlinx.datetime.Instant

/** Returns instant date time value from string if it matches its default pattern. Otherwise, returns null. */
fun String.toInstantOrNull(): Instant? {
    return runCatching { Instant.parse(this) }.onFailure { it.printStackTrace() }.getOrNull()
}