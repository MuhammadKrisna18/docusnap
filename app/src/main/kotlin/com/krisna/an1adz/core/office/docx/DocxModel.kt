package com.krisna.an1adz.core.office.docx

data class DocxDocument(
    val fileName: String,
    val title: String,
    val elements: List<DocxElement>,
    val wordCount: Int,
    val paragraphCount: Int,
    val charCount: Int
)

sealed interface DocxElement {
    data class Heading(
        val text: String,
        val level: Int
    ) : DocxElement

    data class Paragraph(
        val runs: List<TextRun>,
        val alignment: DocxAlignment = DocxAlignment.LEFT,
        val isBullet: Boolean = false
    ) : DocxElement

    data class Table(
        val rows: List<TableRow>
    ) : DocxElement

    data class Image(
        val bytes: ByteArray,
        val description: String = ""
    ) : DocxElement {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Image
            return bytes.contentEquals(other.bytes) && description == other.description
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + description.hashCode()
            return result
        }
    }

    data object Divider : DocxElement
}

data class TextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val colorHex: String? = null
)

data class TableRow(
    val cells: List<TableCell>
)

data class TableCell(
    val text: String
)

enum class DocxAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}
