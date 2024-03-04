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

import dev.kobalt.md2htmlwebserver.jvm.extension.requireIsLocatedIn
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import java.nio.file.Path
import kotlin.io.path.*

/** Repository that provides access to storage that will be used to render content. */
@OptIn(ExperimentalPathApi::class)
class StorageRepository(
    /** Main path object that will be used as root for accessing content. */
    private val rootPath: Path,
    /** Path of HTTP status code pages folder. */
    private val statusPath: Path,
    /** Path of HTML file that is used as template to wrap rendered content from markdown file. */
    private val templatePath: Path,
    /** Name of markdown file that will be used for rendering HTML file. */
    private val markdownName: String,
    /** Name of HTML file that will be rendered from markdown file. */
    private val htmlName: String,
    /** Primary title name of website. */
    private val websiteName: String
) {

    /** Coroutine scope based on I/O dispatcher. */
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /** File system watcher instance for monitoring storage content. */
    private val pathWatcher = KfsDirectoryWatcher(ioScope)

    /** List of all directories and subdirectories from root path. */
    private val rootPathDirectories
        get() = rootPath.walk(PathWalkOption.INCLUDE_DIRECTORIES).filter { it.isDirectory() }

    /** Reloads rendering all markdown pages to HTML. */
    fun reload() {
        rootPathDirectories.forEach { path ->
            val markdownPath = path.resolve(markdownName)
            if (!markdownPath.exists()) return@forEach
            val htmlPath = path.resolve(htmlName)
            convertMarkdown(markdownPath, htmlPath)
        }
    }

    /** Starts watching all directories in root path to monitor changes in markdown files for reloading. */
    fun startWatcher() {
        ioScope.launch {
            // Add all directories for watching.
            rootPathDirectories.forEach { pathWatcher.add(it.pathString) }
            // Capture any change within directories.
            pathWatcher.onEventFlow.collect { event ->
                // Assemble path from event value.
                val path = Path.of(event.targetDirectory).resolve(event.path)
                // Reload all pages if it matches markdown file, otherwise check directory state.
                if (path.isRegularFile() && path.name == markdownName) {
                    reload()
                } else if (path.isDirectory()) {
                    val pathIsWatched = pathWatcher.watchingDirectories.contains(path.pathString)
                    // If directory is created or removed, ensure that it's being monitored properly.
                    when (event.event) {
                        KfsEvent.Create -> if (!pathIsWatched) pathWatcher.add(path.pathString)
                        KfsEvent.Delete -> if (pathIsWatched) pathWatcher.remove(path.pathString)
                        KfsEvent.Modify -> Unit
                    }
                }
            }
        }
    }

    /** Stops watching all directories in root path. */
    fun stopWatcher() {
        ioScope.launch {
            pathWatcher.removeAll()
            pathWatcher.close()
        }
    }

    /** Returns path of rendered HTML file from given path string value. */
    fun fromPath(pathString: String): Path? {
        val path = rootPath.resolve(pathString).requireIsLocatedIn(rootPath)
        // Return null if this path doesn't exist.
        if (!path.exists()) return null
        // If this is a folder, then it's assumed to be a web page to be displayed.
        if (path.isDirectory()) {
            // Markdown content file to be rendered.
            val markdownPath = path.resolve(markdownName)
            // If markdown content file exists, proceed with rendering content. Otherwise, treat it as not found.
            if (!markdownPath.exists()) return null
            // HTML rendered content file from markdown.
            val htmlPath = path.resolve(htmlName)
            // HTML file will be rendered from markdown file if it doesn't exist already.
            if (!htmlPath.exists()) convertMarkdown(markdownPath, htmlPath)
            // Return HTML rendered file.
            return htmlPath
        } else if (path.isRegularFile()) {
            // Check if path name matches existing markdown content file.
            val matchesMarkdownName = path.name == markdownName
            // Check if path name matches existing HTML rendered content file.
            val matchesHtmlName = path.name == htmlName
            // Check if path name matches existing HTML template file.
            val matchesTemplateName = path.isSameFileAs(templatePath)
            // Check if path matches status path.
            val matchesStatusPath = path.startsWith(statusPath)
            // Return file if it doesn't match files for rendering. Otherwise, treat it as non-existent.
            return path.takeIf { !matchesMarkdownName && !matchesHtmlName && !matchesTemplateName && !matchesStatusPath }
        } else {
            // Any other result should be an exception.
            throw Exception()
        }
    }

    /** Returns path of rendered HTML file from given HTTP status code value. */
    fun fromStatus(code: Int): Path {
        val path = statusPath.resolve(code.toString()).requireIsLocatedIn(statusPath)
        // Markdown content file to be rendered.
        val markdownPath = path.resolve(markdownName)
        // If markdown content file exists, proceed with rendering content. Otherwise, throw an exception.
        if (!markdownPath.exists()) throw Exception()
        // HTML rendered content file from markdown.
        val htmlPath = path.resolve(htmlName)
        // HTML file will be rendered from markdown file if it doesn't exist already.
        if (!htmlPath.exists()) convertMarkdown(markdownPath, htmlPath)
        // Return HTML rendered file.
        return htmlPath
    }

    /** Reads markdown content from input path and stores HTML content into output path. */
    private fun convertMarkdown(input: Path, output: Path) {
        val contentText = input.readText().let { content ->
            if (content.contains("[template:dirlist]:.")) {
                val directoryPathList = input.parent.listDirectoryEntries().filter { listPath ->
                    listPath.isDirectory() && listPath.resolve(markdownName).exists()
                }.sortedBy { it.name }

                val flavour = CommonMarkFlavourDescriptor()
                val result = directoryPathList.joinToString("") { listPath ->
                    // Markdown content file to be rendered.
                    val markdownPath = listPath.resolve(markdownName)
                    val text = markdownPath.readText()
                    val parser = MarkdownParser(flavour).buildMarkdownTreeFromString(text)

                    val href = listPath.name
                    val title = runCatching {
                        parser.children.get(0).children.get(4).getTextInNode(text).toString().removePrefix("\"")
                            .removeSuffix("\"")
                    }.getOrNull().orEmpty()
                    val description = runCatching {
                        parser.children.get(2).children.get(4).getTextInNode(text).toString().removePrefix("\"")
                            .removeSuffix("\"")
                    }.getOrNull().orEmpty()
                    "# [$title](./$href/)\n\n$description\n\n"
                }
                content.replace("[template:dirlist]:.", result)
            } else {
                content
            }
        }

        val flavour = CommonMarkFlavourDescriptor()
        val parser = MarkdownParser(flavour).buildMarkdownTreeFromString(contentText)
        val html = HtmlGenerator(contentText, parser, flavour)

        val title = runCatching {
            parser.children.get(0).children.get(4).getTextInNode(contentText).toString().removePrefix("\"")
                .removeSuffix("\"")
        }.getOrNull().orEmpty()
        val description = runCatching {
            parser.children.get(2).children.get(4).getTextInNode(contentText).toString().removePrefix("\"")
                .removeSuffix("\"")
        }.getOrNull().orEmpty()

        val result = getHtml(
            title,
            description,
            html.generateHtml().removePrefix("<body>").removeSuffix("</body>")
        )
        println("Generated")
        output.writeText(result)
    }

    /** Returns fully rendered HTML content, where the content has been put in main article element.*/
    private fun getHtml(
        title: String,
        description: String,
        content: String
    ): String {
        return templatePath.readText()
            .replace("\$name\$", websiteName)
            .replace("\$title\$", title)
            .replace("\$description\$", description)
            .replace("\$content\$", content)
            .let { Jsoup.parse(it).toString() }
    }

}