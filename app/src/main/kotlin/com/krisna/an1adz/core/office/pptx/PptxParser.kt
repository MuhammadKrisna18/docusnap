package com.krisna.an1adz.core.office.pptx

import com.krisna.an1adz.core.office.docx.TextRun
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PptxParser {

    fun parse(file: File): PptxPresentation {
        return try {
            FileInputStream(file).use { input ->
                parseFromStream(input, file.name)
            }
        } catch (_: Exception) {
            parseLegacyPptOrFallback(file)
        }
    }

    private fun parseFromStream(inputStream: InputStream, fileName: String): PptxPresentation {
        val zipBytes = inputStream.readBytes()
        val mediaMap = mutableMapOf<String, ByteArray>()
        val slideRelsMap = mutableMapOf<String, MutableMap<String, String>>()
        val slideXmlMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.matches("ppt/slides/slide\\d+\\.xml".toRegex()) -> {
                        val slideName = File(name).name
                        slideXmlMap[slideName] = zis.readBytes()
                    }
                    name.matches("ppt/slides/_rels/slide\\d+\\.xml\\.rels".toRegex()) -> {
                        val slideName = File(name).name.removeSuffix(".rels")
                        val rels = mutableMapOf<String, String>()
                        parseSlideRelationships(zis.readBytes(), rels)
                        slideRelsMap[slideName] = rels
                    }
                    name.startsWith("ppt/media/") -> {
                        val mediaKey = name.removePrefix("ppt/")
                        mediaMap[mediaKey] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (slideXmlMap.isEmpty()) {
            return fallbackEmptyPresentation(fileName)
        }

        val sortedSlideNames = slideXmlMap.keys.sortedWith(Comparator { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 0
            val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 0
            numA.compareTo(numB)
        })

        val slides = mutableListOf<PptxSlide>()
        var totalWords = 0

        sortedSlideNames.forEachIndexed { index, slideName ->
            val xmlBytes = slideXmlMap[slideName] ?: return@forEachIndexed
            val rels = slideRelsMap[slideName] ?: emptyMap()
            val slideNumber = index + 1

            val slide = parseSingleSlideXml(
                xmlBytes = xmlBytes,
                slideNumber = slideNumber,
                mediaMap = mediaMap,
                relsMap = rels
            )
            slides.add(slide)

            slide.elements.forEach { el ->
                when (el) {
                    is PptxElement.Title -> {
                        totalWords += el.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    }
                    is PptxElement.Subtitle -> {
                        totalWords += el.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    }
                    is PptxElement.Paragraph -> {
                        el.runs.forEach { r ->
                            totalWords += r.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                        }
                    }
                    else -> Unit
                }
            }
        }

        val presentationTitle = slides.firstOrNull()?.title
            ?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast(".")

        return PptxPresentation(
            fileName = fileName,
            title = presentationTitle,
            slides = slides,
            totalSlides = slides.size,
            totalWords = totalWords
        )
    }

    private fun parseSlideRelationships(xmlBytes: ByteArray, relsMap: MutableMap<String, String>) {
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

    private fun parseSingleSlideXml(
        xmlBytes: ByteArray,
        slideNumber: Int,
        mediaMap: Map<String, ByteArray>,
        relsMap: Map<String, String>
    ): PptxSlide {
        val elements = mutableListOf<PptxElement>()
        var slideTitle: String? = null

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType

        var inShape = false
        var isTitleShape = false
        var isSubtitleShape = false

        var inParagraph = false
        var isBulletParagraph = false
        var inRun = false
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isStrike = false
        var colorHex: String? = null

        val currentParagraphRuns = mutableListOf<TextRun>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "sp" -> {
                            inShape = true
                            isTitleShape = false
                            isSubtitleShape = false
                        }
                        "ph" -> {
                            val type = parser.getAttributeValue(null, "type")?.lowercase()
                            if (type == "title" || type == "ctrtitle") {
                                isTitleShape = true
                            } else if (type == "subtitle" || type == "sub") {
                                isSubtitleShape = true
                            }
                        }
                        "p" -> {
                            inParagraph = true
                            isBulletParagraph = false
                            currentParagraphRuns.clear()
                        }
                        "buChar", "buFont" -> {
                            isBulletParagraph = true
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
                            val bVal = parser.getAttributeValue(null, "b")
                            isBold = bVal == "1" || bVal.equals("true", ignoreCase = true)
                            val iVal = parser.getAttributeValue(null, "i")
                            isItalic = iVal == "1" || iVal.equals("true", ignoreCase = true)
                            val uVal = parser.getAttributeValue(null, "u")
                            isUnderline = uVal != null && uVal != "none"
                            val strikeVal = parser.getAttributeValue(null, "strike")
                            isStrike = strikeVal != null && strikeVal != "noStrike"
                        }
                        "srgbClr" -> {
                            colorHex = parser.getAttributeValue(null, "val")
                        }
                        "blip" -> {
                            val embedId = parser.getAttributeValue(null, "embed")
                            if (embedId != null) {
                                val target = relsMap[embedId]
                                if (target != null) {
                                    val cleanedTarget = target.removePrefix("../").removePrefix("/")
                                    val imageBytes = mediaMap[cleanedTarget] ?: mediaMap["media/${File(target).name}"]
                                    if (imageBytes != null) {
                                        elements.add(PptxElement.Image(bytes = imageBytes, description = target))
                                    }
                                }
                            }
                        }
                        "t" -> {
                            val text = parser.nextText()
                            if (inParagraph && text.isNotEmpty()) {
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
                        "r" -> inRun = false
                        "p" -> {
                            if (inParagraph) {
                                val fullText = currentParagraphRuns.joinToString("") { it.text }.trim()
                                if (fullText.isNotEmpty()) {
                                    if (isTitleShape && slideTitle == null) {
                                        slideTitle = fullText
                                        elements.add(PptxElement.Title(fullText))
                                    } else if (isSubtitleShape) {
                                        elements.add(PptxElement.Subtitle(fullText))
                                    } else {
                                        elements.add(
                                            PptxElement.Paragraph(
                                                runs = currentParagraphRuns.toList(),
                                                isBullet = isBulletParagraph
                                            )
                                        )
                                    }
                                }
                                inParagraph = false
                            }
                        }
                        "sp" -> {
                            inShape = false
                            isTitleShape = false
                            isSubtitleShape = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return PptxSlide(
            slideNumber = slideNumber,
            title = slideTitle ?: "Slide $slideNumber",
            elements = elements
        )
    }

    private fun parseLegacyPptOrFallback(file: File): PptxPresentation {
        val rawBytes = file.readBytes()
        val textBlocks = mutableListOf<String>()

        val stringUtf8 = String(rawBytes, StandardCharsets.UTF_8)
        val validUtf8Tokens = extractReadableTokens(stringUtf8)

        if (validUtf8Tokens.isNotEmpty()) {
            textBlocks.addAll(validUtf8Tokens)
        } else {
            val stringUtf16 = String(rawBytes, StandardCharsets.UTF_16LE)
            val validUtf16Tokens = extractReadableTokens(stringUtf16)
            textBlocks.addAll(validUtf16Tokens)
        }

        val slides = mutableListOf<PptxSlide>()
        val chunkSize = 4
        val chunks = textBlocks.chunked(chunkSize)

        chunks.forEachIndexed { index, chunk ->
            val slideNumber = index + 1
            val elements = mutableListOf<PptxElement>()
            val firstLine = chunk.firstOrNull() ?: "Slide $slideNumber"
            elements.add(PptxElement.Title(firstLine))

            chunk.drop(1).forEach { line ->
                elements.add(
                    PptxElement.Paragraph(
                        runs = listOf(TextRun(line)),
                        isBullet = true
                    )
                )
            }

            slides.add(
                PptxSlide(
                    slideNumber = slideNumber,
                    title = firstLine,
                    elements = elements
                )
            )
        }

        val safeSlides = if (slides.isEmpty()) {
            listOf(
                PptxSlide(
                    slideNumber = 1,
                    title = file.name.substringBeforeLast("."),
                    elements = listOf(PptxElement.Title(file.name.substringBeforeLast(".")))
                )
            )
        } else {
            slides
        }

        var totalWords = 0
        safeSlides.forEach { s ->
            s.elements.forEach { el ->
                if (el is PptxElement.Title) {
                    totalWords += el.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                } else if (el is PptxElement.Paragraph) {
                    el.runs.forEach { r ->
                        totalWords += r.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    }
                }
            }
        }

        return PptxPresentation(
            fileName = file.name,
            title = safeSlides.first().title ?: file.name.substringBeforeLast("."),
            slides = safeSlides,
            totalSlides = safeSlides.size,
            totalWords = totalWords
        )
    }

    private fun extractReadableTokens(rawText: String): List<String> {
        return rawText.split("\n", "\r")
            .map { it.replace("[^\\p{Print}\\p{Space}]".toRegex(), "").trim() }
            .filter { line ->
                line.length >= 4 && line.any { it.isLetter() } && !line.startsWith("<?xml")
            }
    }

    private fun fallbackEmptyPresentation(fileName: String): PptxPresentation {
        return PptxPresentation(
            fileName = fileName,
            title = fileName.substringBeforeLast("."),
            slides = listOf(
                PptxSlide(
                    slideNumber = 1,
                    title = fileName.substringBeforeLast("."),
                    elements = emptyList()
                )
            ),
            totalSlides = 1,
            totalWords = 0
        )
    }
}
