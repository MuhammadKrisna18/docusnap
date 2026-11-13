package com.krisna.an1adz.core.office.spreadsheet

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SpreadsheetParser {

    fun parse(file: File): SpreadsheetDocument {
        val lowerName = file.name.lowercase()
        return if (lowerName.endsWith(".csv") || lowerName.endsWith(".tsv")) {
            parseDelimitedFile(file)
        } else {
            try {
                FileInputStream(file).use { input ->
                    parseFromStream(input, file.name)
                }
            } catch (_: Exception) {
                parseLegacyXlsOrFallback(file)
            }
        }
    }

    private fun parseFromStream(inputStream: InputStream, fileName: String): SpreadsheetDocument {
        val zipBytes = inputStream.readBytes()
        val sheetXmlMap = mutableMapOf<String, ByteArray>()
        val workbookRelsMap = mutableMapOf<String, String>()
        val sheetMetadataList = mutableListOf<SheetMeta>()
        var sharedStringsXmlBytes: ByteArray? = null
        var workbookXmlBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> {
                        sharedStringsXmlBytes = zis.readBytes()
                    }
                    name == "xl/workbook.xml" -> {
                        workbookXmlBytes = zis.readBytes()
                    }
                    name == "xl/_rels/workbook.xml.rels" -> {
                        parseWorkbookRelationships(zis.readBytes(), workbookRelsMap)
                    }
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        val simpleName = File(name).name
                        sheetXmlMap[simpleName] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        workbookXmlBytes?.let {
            parseWorkbookMetadata(it, sheetMetadataList)
        }

        val sharedStrings = sharedStringsXmlBytes?.let {
            parseSharedStrings(it)
        } ?: emptyList()

        val sheets = mutableListOf<SpreadsheetSheet>()

        if (sheetMetadataList.isNotEmpty()) {
            for (meta in sheetMetadataList) {
                val target = workbookRelsMap[meta.relId] ?: "worksheets/sheet${meta.sheetId}.xml"
                val simpleName = File(target).name
                val xmlBytes = sheetXmlMap[simpleName] ?: sheetXmlMap["sheet${meta.sheetId}.xml"]
                if (xmlBytes != null) {
                    val parsedSheet = parseWorksheetXml(xmlBytes, meta.name, sharedStrings)
                    sheets.add(parsedSheet)
                }
            }
        }

        if (sheets.isEmpty()) {
            val sortedNames = sheetXmlMap.keys.sortedWith(Comparator { a, b ->
                val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 0
                val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 0
                numA.compareTo(numB)
            })
            sortedNames.forEachIndexed { index, sheetFileName ->
                val xmlBytes = sheetXmlMap[sheetFileName] ?: return@forEachIndexed
                val sheetName = "Sheet ${index + 1}"
                sheets.add(parseWorksheetXml(xmlBytes, sheetName, sharedStrings))
            }
        }

        if (sheets.isEmpty()) {
            return fallbackEmptySpreadsheet(fileName)
        }

        val totalRows = sheets.sumOf { it.rowCount }
        val maxCols = sheets.maxOfOrNull { it.maxColumns } ?: 0

        return SpreadsheetDocument(
            fileName = fileName,
            title = fileName.substringBeforeLast("."),
            sheets = sheets,
            totalSheets = sheets.size,
            totalRows = totalRows,
            totalColumns = maxCols
        )
    }

    private data class SheetMeta(val name: String, val sheetId: String, val relId: String)

    private fun parseWorkbookRelationships(xmlBytes: ByteArray, relsMap: MutableMap<String, String>) {
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

    private fun parseWorkbookMetadata(xmlBytes: ByteArray, metadataList: MutableList<SheetMeta>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                    val sheetId = parser.getAttributeValue(null, "sheetId") ?: ""
                    var relId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    if (relId == null) {
                        relId = parser.getAttributeValue(null, "id") ?: ""
                    }
                    metadataList.add(SheetMeta(name, sheetId, relId))
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var inSi = false
            val currentString = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "si") {
                            inSi = true
                            currentString.clear()
                        } else if (inSi && tagName == "t") {
                            currentString.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "si") {
                            inSi = false
                            strings.add(currentString.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return strings
    }

    private fun parseWorksheetXml(
        xmlBytes: ByteArray,
        sheetName: String,
        sharedStrings: List<String>
    ): SpreadsheetSheet {
        val rows = mutableListOf<SpreadsheetRow>()
        var maxColumns = 0

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType

            var inRow = false
            var currentRowIndex = 1
            val currentCells = mutableListOf<SpreadsheetCell>()

            var inCell = false
            var currentCellRef = ""
            var currentCellType = ""
            var currentCellValue = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "row" -> {
                                inRow = true
                                val rVal = parser.getAttributeValue(null, "r")
                                currentRowIndex = rVal?.toIntOrNull() ?: (rows.size + 1)
                                currentCells.clear()
                            }
                            "c" -> {
                                inCell = true
                                currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                currentCellValue = ""
                            }
                            "v" -> {
                                if (inCell) {
                                    currentCellValue = parser.nextText()
                                }
                            }
                            "t" -> {
                                if (inCell) {
                                    currentCellValue = parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "c" -> {
                                inCell = false
                                val colIdx = extractColumnIndex(currentCellRef)
                                if (colIdx + 1 > maxColumns) {
                                    maxColumns = colIdx + 1
                                }

                                val resolvedValue = when (currentCellType) {
                                    "s" -> {
                                        val strIndex = currentCellValue.toIntOrNull() ?: -1
                                        if (strIndex in sharedStrings.indices) {
                                            sharedStrings[strIndex]
                                        } else {
                                            currentCellValue
                                        }
                                    }
                                    "b" -> {
                                        if (currentCellValue == "1") "TRUE" else "FALSE"
                                    }
                                    else -> currentCellValue
                                }

                                val isNumeric = currentCellType != "s" && currentCellType != "b" && currentCellType != "str" && resolvedValue.isNotBlank() && resolvedValue.toDoubleOrNull() != null
                                val isHeader = currentRowIndex == 1

                                currentCells.add(
                                    SpreadsheetCell(
                                        colIndex = colIdx,
                                        cellRef = currentCellRef,
                                        value = resolvedValue,
                                        isHeader = isHeader,
                                        isNumeric = isNumeric
                                    )
                                )
                            }
                            "row" -> {
                                inRow = false
                                if (currentCells.isNotEmpty()) {
                                    val sortedCells = currentCells.sortedBy { it.colIndex }
                                    rows.add(
                                        SpreadsheetRow(
                                            rowIndex = currentRowIndex,
                                            cells = sortedCells
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        return SpreadsheetSheet(
            name = sheetName,
            rows = rows,
            maxColumns = maxColumns,
            rowCount = rows.size
        )
    }

    private fun extractColumnIndex(cellRef: String): Int {
        var colLetters = ""
        for (ch in cellRef) {
            if (ch.isLetter()) {
                colLetters += ch.uppercaseChar()
            } else {
                break
            }
        }
        if (colLetters.isEmpty()) return 0

        var index = 0
        for (ch in colLetters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun parseDelimitedFile(file: File): SpreadsheetDocument {
        val rows = mutableListOf<SpreadsheetRow>()
        var maxCols = 0

        try {
            BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                val delimiter = if (file.name.lowercase().endsWith(".tsv")) "\t" else detectDelimiter(line ?: "")

                var rowIdx = 1
                while (line != null) {
                    val rawTokens = parseCsvLine(line, delimiter)
                    if (rawTokens.size > maxCols) {
                        maxCols = rawTokens.size
                    }

                    val cells = rawTokens.mapIndexed { colIdx, text ->
                        val colLetter = getColumnLetter(colIdx)
                        val isNum = text.toDoubleOrNull() != null
                        SpreadsheetCell(
                            colIndex = colIdx,
                            cellRef = "$colLetter$rowIdx",
                            value = text,
                            isHeader = rowIdx == 1,
                            isNumeric = isNum
                        )
                    }

                    rows.add(SpreadsheetRow(rowIndex = rowIdx, cells = cells))
                    rowIdx++
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {
        }

        val sheet = SpreadsheetSheet(
            name = file.name.substringBeforeLast("."),
            rows = rows,
            maxColumns = maxCols,
            rowCount = rows.size
        )

        return SpreadsheetDocument(
            fileName = file.name,
            title = file.name.substringBeforeLast("."),
            sheets = listOf(sheet),
            totalSheets = 1,
            totalRows = rows.size,
            totalColumns = maxCols
        )
    }

    private fun detectDelimiter(sampleLine: String): String {
        val commaCount = sampleLine.count { it == ',' }
        val semiCount = sampleLine.count { it == ';' }
        val tabCount = sampleLine.count { it == '\t' }
        return when {
            semiCount > commaCount && semiCount > tabCount -> ";"
            tabCount > commaCount && tabCount > semiCount -> "\t"
            else -> ","
        }
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val tokens = mutableListOf<String>()
        val delimChar = delimiter.firstOrNull() ?: ','
        var inQuotes = false
        val current = StringBuilder()

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == delimChar && !inQuotes -> {
                    tokens.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        tokens.add(current.toString().trim())
        return tokens
    }

    private fun parseLegacyXlsOrFallback(file: File): SpreadsheetDocument {
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

        val rows = mutableListOf<SpreadsheetRow>()
        var maxCols = 0

        textBlocks.forEachIndexed { index, line ->
            val rowIdx = index + 1
            val tokens = line.split("\\s{2,}|\t".toRegex()).filter { it.isNotBlank() }
            if (tokens.size > maxCols) {
                maxCols = tokens.size
            }
            val cells = tokens.mapIndexed { colIdx, text ->
                val colLetter = getColumnLetter(colIdx)
                SpreadsheetCell(
                    colIndex = colIdx,
                    cellRef = "$colLetter$rowIdx",
                    value = text,
                    isHeader = rowIdx == 1,
                    isNumeric = text.toDoubleOrNull() != null
                )
            }
            rows.add(SpreadsheetRow(rowIndex = rowIdx, cells = cells))
        }

        val safeRows = if (rows.isEmpty()) {
            listOf(
                SpreadsheetRow(
                    rowIndex = 1,
                    cells = listOf(
                        SpreadsheetCell(
                            colIndex = 0,
                            cellRef = "A1",
                            value = file.name.substringBeforeLast("."),
                            isHeader = true
                        )
                    )
                )
            )
        } else rows

        val sheet = SpreadsheetSheet(
            name = file.name.substringBeforeLast("."),
            rows = safeRows,
            maxColumns = maxCols.coerceAtLeast(1),
            rowCount = safeRows.size
        )

        return SpreadsheetDocument(
            fileName = file.name,
            title = file.name.substringBeforeLast("."),
            sheets = listOf(sheet),
            totalSheets = 1,
            totalRows = safeRows.size,
            totalColumns = maxCols.coerceAtLeast(1)
        )
    }

    private fun extractReadableTokens(rawText: String): List<String> {
        return rawText.split("\n", "\r")
            .map { it.replace("[^\\p{Print}\\p{Space}]".toRegex(), "").trim() }
            .filter { line ->
                line.length >= 3 && line.any { it.isLetterOrDigit() } && !line.startsWith("<?xml")
            }
    }

    private fun getColumnLetter(colIndex: Int): String {
        var n = colIndex
        var result = ""
        while (n >= 0) {
            result = ('A'.code + (n % 26)).toChar() + result
            n = (n / 26) - 1
        }
        return result
    }

    private fun fallbackEmptySpreadsheet(fileName: String): SpreadsheetDocument {
        return SpreadsheetDocument(
            fileName = fileName,
            title = fileName.substringBeforeLast("."),
            sheets = listOf(
                SpreadsheetSheet(
                    name = "Sheet 1",
                    rows = emptyList(),
                    maxColumns = 0,
                    rowCount = 0
                )
            ),
            totalSheets = 1,
            totalRows = 0,
            totalColumns = 0
        )
    }
}
