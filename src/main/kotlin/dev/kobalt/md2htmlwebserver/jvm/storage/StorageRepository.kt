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
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import java.nio.file.Path
import kotlin.io.path.*

/** Repository that provides access to storage that will be used to render content. */
class StorageRepository(
    private val path: String,
    private val name: String
) {

    /** Main path object that will be used as root for accessing content. It will be created if it doesn't exist. */
    private val rootPath: Path = Path(path).also { if (it.notExists()) it.createDirectory() }

    fun fromPath(pathString: String): Path? {
        val path = rootPath.resolve(pathString).requireIsLocatedIn(rootPath)
        // Return null if this pathe doesn't exist.
        if (!path.exists()) return null
        // If this is a folder, then it's assumed to be a web page to be displayed.
        if (path.isDirectory()) {
            // Markdown content file to be rendered.
            val markdownPath = path.resolve("index.md")
            // If markdown content file exists, proceed with rendering content. Otherwise, treat it as not found.
            if (!markdownPath.exists()) return null
            // HTML rendered content file from markdown.
            val htmlPath = path.resolve("index.html")
            // JSON properties file for comparing rendered and current markdown content.
            val jsonPath = path.resolve("index.json")
            // Properties containing information for markdown file since last rendering process.
            val properties = jsonPath.takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()).jsonObject }
            // Size of markdown content file.
            val markdownSize = markdownPath.fileSize()
            // Last modified date time of markdown content file.
            val markdownDate = markdownPath.getLastModifiedTime().toInstant().toKotlinInstant()
            // Size of markdown content file since last rendering process.
            val propertiesSize = properties?.get("size")?.jsonPrimitive?.content?.toLongOrNull()
            // Last modified date time of markdown content file since last rendering process.
            val propertiesDate = properties?.get("date")?.jsonPrimitive?.content?.toInstant()
            // Check if current and rendered version of markdown files match.
            val sizeMatches = markdownSize == propertiesSize
            // Check if current and rendered modification dates of markdown files match.
            val dateMatches = markdownDate == propertiesDate
            // Check if HTML should be generated from markdown file.
            // Only one of following conditions needs to apply:
            // - HTML rendered file doesn't exist
            // - JSON properties file doesn't exist
            // - Sizes don't match
            // - Last modified dates don't match
            val shouldGenerateHtml = !htmlPath.exists() || !jsonPath.exists() || !sizeMatches || !dateMatches
            // If any of those conditions were met, an HTML file will be rendered from markdown file.
            if (shouldGenerateHtml) convertMarkdown(markdownPath, htmlPath)
            // Return HTML rendered file.
            return htmlPath
        } else if (path.isRegularFile()) {
            // Check if path name matches existing markdown content file.
            val matchesMarkdownName = path.name == "index.md"
            // Check if path name matches existing HTML rendered content file.
            val matchesHtmlName = path.name == "index.html"
            // Check if path name matches existing JSON properties file.
            val matchesJsonName = path.name == "index.json"
            // Check if path name matches existing HTML template file.
            val matchesTemplateName = path.name == "template.html"
            // Return file if it doesn't match files for rendering. Otherwise, throw an exception.
            if (!matchesMarkdownName && !matchesHtmlName && !matchesJsonName && !matchesTemplateName) {
                return path
            } else {
                throw Exception()
            }
        } else {
            // Any other result should be an exception.
            throw Exception()
        }
    }

    /** Reads markdown content from input path and stores HTML content into output path. */
    private fun convertMarkdown(input: Path, output: Path) {
        val contentText = input.readText().let { content ->
            if (content.contains("[template:dirlist]:.")) {
                val directoryPathList = input.parent.listDirectoryEntries().filter { listPath ->
                    listPath.isDirectory() && listPath.resolve("index.md").exists()
                }.sortedBy { it.name }

                val flavour = CommonMarkFlavourDescriptor()
                val result = directoryPathList.joinToString("") { listPath ->
                    // Markdown content file to be rendered.
                    val markdownPath = listPath.resolve("index.md")
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
        return rootPath.resolve("template.html").requireIsLocatedIn(rootPath).readText()
            .replace("\$name\$", name)
            .replace("\$title\$", title)
            .replace("\$description\$", description)
            .replace("\$content\$", content)
            .let { Jsoup.parse(it).toString() }
    }

}