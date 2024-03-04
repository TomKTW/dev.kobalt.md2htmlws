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

import java.nio.file.Path

/** Returns true if the given path is parent to this path. */
fun Path.isLocatedIn(parent: Path) = normalize().startsWith(parent.normalize())

/** Throws an exception if the path is not located in parent path. */
fun Path.requireIsLocatedIn(parent: Path) = takeIf { isLocatedIn(parent) }
    ?: throw Exception("File ${this.normalize()} is not located in ${parent.normalize()}.")

/** Returns resolved path. Exception is thrown if the path is not located in current path as parent. */
fun Path.resolveAndRequireIsLocatedInCurrentPath(path: String) = this.resolve(path).requireIsLocatedIn(this)