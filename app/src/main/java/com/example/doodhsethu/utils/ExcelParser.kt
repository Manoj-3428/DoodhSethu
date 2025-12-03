package com.example.doodhsethu.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.doodhsethu.data.models.FatRangeRow
import java.io.InputStream
import java.util.zip.ZipInputStream

object ExcelParser {
    private const val TAG = "ExcelParser"
    
    /**
     * Parse Excel or CSV file and extract FAT table data
     * Expected format: 3 columns - From Value, To Value, Price
     */
    fun parseFatTableExcel(inputStream: InputStream): ExcelParseResult {
        return try {
            Log.d(TAG, "Attempting to parse file...")
            
            // Try to read as Excel first (XLSX is a ZIP file)
            try {
                Log.d(TAG, "Trying to parse as XLSX file...")
                parseXLSXFile(inputStream)
            } catch (e: Exception) {
                Log.d(TAG, "XLSX parsing failed, trying CSV: ${e.message}")
                
                // For ContentResolver streams, we can't reset, so we need to get a new stream
                // Let's try CSV parsing directly with the same stream
                try {
                    SimpleExcelParser.parseFatTableCSV(inputStream)
                } catch (e2: Exception) {
                    Log.e(TAG, "All parsing methods failed", e2)
                    ExcelParseResult.Error("Could not parse file. Please ensure it's a valid Excel (.xlsx) or CSV file. Note: .xls files are not supported.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing file: ${e.message}")
            ExcelParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    /**
     * Parse file with URI and ContentResolver (handles stream creation)
     */
    fun parseFatTableFile(fileUri: Uri, contentResolver: ContentResolver): ExcelParseResult {
        return try {
            Log.d(TAG, "Attempting to parse file with URI...")
            
            // Try XLSX first
            try {
                Log.d(TAG, "Trying to parse as XLSX file...")
                val xlsxStream = contentResolver.openInputStream(fileUri)
                if (xlsxStream == null) {
                    return ExcelParseResult.Error("Could not read file")
                }
                val result = parseXLSXFile(xlsxStream)
                xlsxStream.close()
                result
            } catch (e: Exception) {
                Log.d(TAG, "XLSX parsing failed, trying CSV: ${e.message}")
                
                // Try CSV with a new stream
                try {
                    val csvStream = contentResolver.openInputStream(fileUri)
                    if (csvStream == null) {
                        return ExcelParseResult.Error("Could not read file")
                    }
                    val result = SimpleExcelParser.parseFatTableCSV(csvStream)
                    csvStream.close()
                    result
                } catch (e2: Exception) {
                    Log.e(TAG, "All parsing methods failed", e2)
                    ExcelParseResult.Error("Could not parse file. Please ensure it's a valid Excel (.xlsx) or CSV file. Note: .xls files are not supported.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing file: ${e.message}")
            ExcelParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    /**
     * Parse XLSX file (which is essentially a ZIP file with XML)
     * This is a simplified parser for basic XLSX files
     */
    private fun parseXLSXFile(inputStream: InputStream): ExcelParseResult {
        return try {
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            
            // Look for the shared strings file and worksheet
            var sharedStrings: List<String> = emptyList()
            var worksheetData: String? = null
            
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStrings(zipStream)
                        Log.d(TAG, "Found ${sharedStrings.size} shared strings")
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        worksheetData = zipStream.readBytes().toString(Charsets.UTF_8)
                        Log.d(TAG, "Found worksheet data")
                    }
                }
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            
            if (worksheetData == null) {
                return ExcelParseResult.Error("Could not find worksheet data in Excel file")
            }
            
            // Parse the worksheet XML
            parseWorksheetXML(worksheetData, sharedStrings)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XLSX: ${e.message}")
            throw e
        }
    }
    
    /**
     * Parse shared strings from XLSX
     */
    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val xml = inputStream.readBytes().toString(Charsets.UTF_8)
        val strings = mutableListOf<String>()
        
        // Simple XML parsing for shared strings
        val stringPattern = "<t[^>]*>([^<]*)</t>".toRegex()
        val matches = stringPattern.findAll(xml)
        
        for (match in matches) {
            val value = match.groupValues[1]
            if (value.isNotBlank()) {
                strings.add(value)
            }
        }
        
        return strings
    }
    
    /**
     * Parse worksheet XML and extract data
     */
    private fun parseWorksheetXML(xml: String, sharedStrings: List<String>): ExcelParseResult {
        val fatRows = mutableListOf<FatRangeRow>()
        val rows = mutableListOf<List<String>>()
        
        // Extract row data from XML
        val rowPattern = "<row[^>]*>.*?</row>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val cellPattern = "<c[^>]*r=\"([A-Z]+)(\\d+)\"[^>]*>.*?<v>([^<]*)</v>.*?</c>".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        val rowMatches = rowPattern.findAll(xml)
        
        for (rowMatch in rowMatches) {
            val rowXml = rowMatch.value
            val cells = mutableMapOf<Int, String>()
            
            val cellMatches = cellPattern.findAll(rowXml)
            for (cellMatch in cellMatches) {
                val column = cellMatch.groupValues[1]
                val rowNum = cellMatch.groupValues[2].toIntOrNull() ?: continue
                val value = cellMatch.groupValues[3]
                
                // Convert column letter to number (A=1, B=2, etc.)
                val colNum = columnToNumber(column)
                if (colNum in 1..3) { // Only process first 3 columns
                    cells[colNum - 1] = value
                }
            }
            
            // Only add rows that have all 3 columns
            if (cells.size == 3 && cells.keys.sorted() == listOf(0, 1, 2)) {
                val rowData = listOf(cells[0] ?: "", cells[1] ?: "", cells[2] ?: "")
                rows.add(rowData)
            }
        }
        
        // Skip header row and process data
        val dataRows = rows.drop(1).filter { it.all { cell -> cell.isNotBlank() } }
        
        for ((index, rowData) in dataRows.withIndex()) {
            try {
                val fromValue = parseNumericValue(rowData[0])
                val toValue = parseNumericValue(rowData[1])
                val price = parseNumericValue(rowData[2])
                
                // Validate values
                if (fromValue < 0 || toValue < 0 || price < 0) {
                    return ExcelParseResult.Error("Row ${index + 2}: All values must be positive numbers")
                }
                
                if (fromValue >= toValue) {
                    return ExcelParseResult.Error("Row ${index + 2}: 'From Value' must be less than 'To Value'")
                }
                
                val fatRow = FatRangeRow(
                    from = fromValue.toFloat(),
                    to = toValue.toFloat(),
                    price = price,
                    isSynced = false
                )
                
                fatRows.add(fatRow)
                Log.d(TAG, "Parsed row: $fatRow")
                
            } catch (e: NumberFormatException) {
                return ExcelParseResult.Error("Row ${index + 2}: Invalid numeric values. Please ensure all values are numbers")
            }
        }
        
        if (fatRows.isEmpty()) {
            return ExcelParseResult.Error("No valid data found in Excel file")
        }
        
        Log.d(TAG, "Successfully parsed ${fatRows.size} FAT table entries from Excel")
        return ExcelParseResult.Success(fatRows)
    }
    
    /**
     * Convert Excel column letter to number (A=1, B=2, etc.)
     */
    private fun columnToNumber(column: String): Int {
        var result = 0
        for (char in column) {
            result = result * 26 + (char - 'A' + 1)
        }
        return result
    }
    
    /**
     * Parse string value to double
     */
    private fun parseNumericValue(value: String): Double {
        return value.trim().replace(",", "").toDouble()
    }
    
    /**
     * Check if file is supported format
     * Note: This method is primarily for Excel detection. CSV validation is handled in the UI.
     */
    fun checkFileType(fileUri: Uri): String? {
        // For document URIs, we can't reliably get the filename from the URI
        // So we'll let the UI handle the validation and just return null for supported formats
        // The actual file type will be determined during parsing
        return null // Let the UI handle validation
    }
}

/**
 * Result of Excel parsing
 */
sealed class ExcelParseResult {
    data class Success(val fatRows: List<FatRangeRow>) : ExcelParseResult()
    data class Error(val message: String) : ExcelParseResult()
}
