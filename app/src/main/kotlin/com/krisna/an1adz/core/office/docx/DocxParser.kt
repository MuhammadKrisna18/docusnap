package com.krisna.an1adz.core.office.docx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DocxParser {

    fun parse(file: File): DocxDocument {
        return try {
            FileInputStream(file).use { input ->
                parseFromStream(input, file.name)
            }
        } catch (_: Exception) {
            parseLegacyDocOrFallback(file)
        }
    }

    private fun parseFromStream(inputStream: InputStream, fileName: String): DocxDocument {
        val zipBytes = inputStream.readBytes()
        val mediaMap = mutableMapOf<String, ByteArray>()
        val relsMap = mutableMapOf<String, String>()
        var documentXmlBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "word/document.xml" -> {
                        documentXmlBytes = zis.readBytes()
                    }
                    name == "word/_rels/document.xml.rels" -> {
                        parseRelationships(zis.readBytes(), relsMap)
                    }
                    name.startsWith("word/media/") -> {
                        val mediaKey = name.removePrefix("word/")
                        mediaMap[mediaKey] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val safeXmlBytes = documentXmlBytes ?: return fallbackEmptyDocument(fileName)

        return parseDocumentXml(
            xmlBytes = safeXmlBytes,
            mediaMap = mediaMap,
            relsMap = relsMap,
            fileName = fileName
        )
    }

    private fun parseRelationships(xmlBytes: ByteArray, relsMap: MutableMap<String, String>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        relsMap[id] = target
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseDocumentXml(
        xmlBytes: ByteArray,
        mediaMap: Map<String, ByteArray>,
        relsMap: Map<String, String>,
        fileName: String
    ): DocxDocument {
        val elements = mutableListOf<DocxElement>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType

        var inParagraph = false
        var inTable = false
        var inTableRow = false
        var inTableCell = false
        var inRun = false
        var inRunProperties = false

        var currentPStyle: String? = null
        var currentAlignment = DocxAlignment.LEFT
        var isCurrentBullet = false

        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isStrike = false
        var colorHex: String? = null

        val currentParagraphRuns = mutableListOf<TextRun>()
        val currentTableRowCells = mutableListOf<TableCell>()
        val currentTableRows = mutableListOf<TableRow>()
        val currentTableCellText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "p" -> {
                            if (!inTable) {
                                inParagraph = true
                                currentParagraphRuns.clear()
                                currentPStyle = null
                                currentAlignment = DocxAlignment.LEFT
                                isCurrentBullet = false
                            }
                        }
                        "pStyle" -> {
                            currentPStyle = parser.getAttributeValue(null, "val")
                        }
                        "jc" -> {
                            val alignVal = parser.getAttributeValue(null, "val")
                            currentAlignment = when (alignVal?.lowercase()) {
                                "center" -> DocxAlignment.CENTER
                                "right" -> DocxAlignment.RIGHT
                                "both" -> DocxAlignment.JUSTIFY
                                else -> DocxAlignment.LEFT
                            }
                        }
                        "numPr" -> {
                            isCurrentBullet = true
                        }
                        "tbl" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "tr" -> {
                            inTableRow = true
                            currentTableRowCells.clear()
                        }
                        "tc" -> {
                            inTableCell = true
                            currentTableCellText.clear()
                        }
                        "r" -> {
                            inRun = true
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrike = false
                            colorHex = null
                        }
                        "rPr" -> {
                            inRunProperties = true
                        }
                        "b" -> {
                            if (inRunProperties) {
                                val bVal = parser.getAttributeValue(null, "val")
                                isBold = bVal == null || bVal == "1" || bVal.equals("true", ignoreCase = true)
                            }
                        }
                        "i" -> {
                            if (inRunProperties) {
                                val iVal = parser.getAttributeValue(null, "val")
                                isItalic = iVal == null || iVal == "1" || iVal.equals("true", ignoreCase = true)
                            }
                        }
                        "u" -> {
                            if (inRunProperties) {
                                val uVal = parser.getAttributeValue(null, "val")
                                isUnderline = uVal == null || uVal != "none"
                            }
                        }
                        "strike" -> {
                            if (inRunProperties) {
                                val strikeVal = parser.getAttributeValue(null, "val")
                                isStrike = strikeVal == null || strikeVal == "1" || strikeVal.equals("true", ignoreCase = true)
                            }
                        }
                        "color" -> {
                            if (inRunProperties) {
                                colorHex = parser.getAttributeValue(null, "val")
                            }
                        }
                        "blip" -> {
                            val embedId = parser.getAttributeValue(null, "embed")
                            if (embedId != null) {
                                val target = relsMap[embedId]
                                if (target != null) {
                                    val cleanedTarget = target.removePrefix("/")
                                    val imageBytes = mediaMap[cleanedTarget] ?: mediaMap["media/${File(target).name}"]
                                    if (imageBytes != null) {
                                        elements.add(DocxElement.Image(bytes = imageBytes, description = target))
                                    }
                                }
                            }
                        }
                        "t" -> {
                            val text = parser.nextText()
                            if (inTableCell) {
                                if (currentTableCellText.isNotEmpty()) {
                                    currentTableCellText.append(" ")
                                }
                                currentTableCellText.append(text)
                            } else if (inParagraph) {
                                currentParagraphRuns.add(
                                    TextRun(
                                        text = text,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        isUnderline = isUnderline,
                                        isStrike = isStrike,
                                        colorHex = colorHex
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "rPr" -> inRunProperties = false
                        "r" -> inRun = false
                        "p" -> {
                            if (!inTable && inParagraph) {
                                val fullParagraphText = currentParagraphRuns.joinToString("") { it.text }.trim()
                                if (fullParagraphText.isNotEmpty()) {
                                    val headingLevel = detectHeadingLevel(currentPStyle)
                                    if (headingLevel > 0) {
                                        elements.add(
                                            DocxElement.Heading(
                                                text = fullParagraphText,
                                                level = headingLevel
                                            )
                                        )
                                    } else {
                                        elements.add(
                                            DocxElement.Paragraph(
                                                runs = currentParagraphRuns.toList(),
                                                alignment = currentAlignment,
                                                isBullet = isCurrentBullet
                                            )
                                        )
                                    }
                                }
                                inParagraph = false
                            }
                        }
                        "tc" -> {
                            inTableCell = false
                            currentTableRowCells.add(TableCell(currentTableCellText.toString().trim()))
                        }
                        "tr" -> {
                            inTableRow = false
                            if (currentTableRowCells.isNotEmpty()) {
                                currentTableRows.add(TableRow(currentTableRowCells.toList()))
                            }
                        }
                        "tbl" -> {
                            inTable = false
                            if (currentTableRows.isNotEmpty()) {
                                elements.add(DocxElement.Table(currentTableRows.toList()))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        var totalWords = 0
        var totalChars = 0
        var totalParagraphs = 0

        elements.forEach { element ->
            when (element) {
                is DocxElement.Heading -> {
                    totalParagraphs++
                    totalChars += element.text.length
                    totalWords += element.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                }
                is DocxElement.Paragraph -> {
                    totalParagraphs++
                    element.runs.forEach { run ->
                        totalChars += run.text.length
                        totalWords += run.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    }
                }
                is DocxElement.Table -> {
                    element.rows.forEach { row ->
                        row.cells.forEach { cell ->
                            totalChars += cell.text.length
                            totalWords += cell.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                        }
                    }
                }
                else -> Unit
            }
        }

        val detectedTitle = elements.firstNotNullOfOrNull { element ->
            when (element) {
                is DocxElement.Heading -> element.text
                is DocxElement.Paragraph -> {
                    val txt = element.runs.joinToString("") { it.text }.trim()
                    txt.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } ?: fileName.substringBeforeLast(".")

        return DocxDocument(
            fileName = fileName,
            title = detectedTitle,
            elements = elements,
            wordCount = totalWords,
            paragraphCount = totalParagraphs,
            charCount = totalChars
        )
    }

    private fun detectHeadingLevel(style: String?): Int {
        if (style == null) return 0
        val lower = style.lowercase()
        return when {
            lower == "title" -> 1
            lower == "subtitle" -> 2
            lower.contains("heading1") || lower == "heading 1" || lower == "1" -> 1
            lower.contains("heading2") || lower == "heading 2" || lower == "2" -> 2
            lower.contains("heading3") || lower == "heading 3" || lower == "3" -> 3
            lower.contains("heading4") || lower == "heading 4" || lower == "4" -> 4
            lower.contains("heading") -> 2
            else -> 0
        }
    }

    private fun parseLegacyDocOrFallback(file: File): DocxDocument {
        val rawBytes = file.readBytes()
        val extractedLines = mutableListOf<String>()

        val stringUtf8 = String(rawBytes, StandardCharsets.UTF_8)
        val validUtf8Tokens = extractReadableTokens(stringUtf8)

        if (validUtf8Tokens.isNotEmpty()) {
            extractedLines.addAll(validUtf8Tokens)
        } else {
            val stringUtf16 = String(rawBytes, StandardCharsets.UTF_16LE)
            val validUtf16Tokens = extractReadableTokens(stringUtf16)
            extractedLines.addAll(validUtf16Tokens)
        }

        val elements = mutableListOf<DocxElement>()
        extractedLines.forEachIndexed { index, line ->
            if (index == 0 && line.length < 100) {
                elements.add(DocxElement.Heading(text = line, level = 1))
            } else {
                elements.add(
                    DocxElement.Paragraph(
                        runs = listOf(TextRun(text = line)),
                        alignment = DocxAlignment.LEFT,
                        isBullet = line.startsWith("-") || line.startsWith("*")
                    )
                )
            }
        }

        var wordCount = 0
        var charCount = 0
        elements.forEach { el ->
            if (el is DocxElement.Heading) {
                charCount += el.text.length
                wordCount += el.text.split("\\s+".toRegex()).count { it.isNotBlank() }
            } else if (el is DocxElement.Paragraph) {
                el.runs.forEach { r ->
                    charCount += r.text.length
                    wordCount += r.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                }
            }
        }

        return DocxDocument(
            fileName = file.name,
            title = elements.firstOrNull()?.let {
                if (it is DocxElement.Heading) it.text else file.name.substringBeforeLast(".")
            } ?: file.name.substringBeforeLast("."),
            elements = elements,
            wordCount = wordCount,
            paragraphCount = elements.size,
            charCount = charCount
        )
    }

    private fun extractReadableTokens(rawText: String): List<String> {
        return rawText.split("\n", "\r")
            .map { it.replace("[^\\p{Print}\\p{Space}]".toRegex(), "").trim() }
            .filter { line ->
                line.length >= 4 && line.any { it.isLetter() } && !line.startsWith("<?xml")
            }
    }

    private fun fallbackEmptyDocument(fileName: String): DocxDocument {
        return DocxDocument(
            fileName = fileName,
            title = fileName.substringBeforeLast("."),
            elements = emptyList(),
            wordCount = 0,
            paragraphCount = 0,
            charCount = 0
        )
    }
}
