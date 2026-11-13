package com.krisna.an1adz.core.office.pptx

import com.krisna.an1adz.core.office.docx.TextRun

data class PptxPresentation(
    val fileName: String,
    val title: String,
    val slides: List<PptxSlide>,
    val totalSlides: Int,
    val totalWords: Int
)

data class PptxSlide(
    val slideNumber: Int,
    val title: String?,
    val elements: List<PptxElement>,
    val notes: String? = null
)

sealed interface PptxElement {
    data class Title(
        val text: String
    ) : PptxElement

    data class Subtitle(
        val text: String
    ) : PptxElement

    data class Paragraph(
        val runs: List<TextRun>,
        val isBullet: Boolean = false
    ) : PptxElement

    data class Image(
        val bytes: ByteArray,
        val description: String = ""
    ) : PptxElement {
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
}
