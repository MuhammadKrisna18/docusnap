package com.krisna.an1adz.core.office.spreadsheet

data class SpreadsheetDocument(
    val fileName: String,
    val title: String,
    val sheets: List<SpreadsheetSheet>,
    val totalSheets: Int,
    val totalRows: Int,
    val totalColumns: Int
)

data class SpreadsheetSheet(
    val name: String,
    val rows: List<SpreadsheetRow>,
    val maxColumns: Int,
    val rowCount: Int
)

data class SpreadsheetRow(
    val rowIndex: Int,
    val cells: List<SpreadsheetCell>
)

data class SpreadsheetCell(
    val colIndex: Int,
    val cellRef: String,
    val value: String,
    val isHeader: Boolean = false,
    val isNumeric: Boolean = false
)
