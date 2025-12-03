package com.example.doodhsethu.utils

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.example.doodhsethu.data.models.Farmer
import java.io.InputStream
import java.util.*
import java.util.zip.ZipInputStream

/**
 * Parser for Excel/CSV files containing farmer data
 * Expected format: name, phone, address (optional)
 * Minimum 2 columns, maximum 3 columns
 */
object FarmerExcelParser {
    private const val TAG = "FarmerExcelParser"
    
    /**
     * Parse farmer data from Excel or CSV file - using the exact working FAT table approach
     */
    fun parseFarmerFile(fileUri: Uri, contentResolver: ContentResolver, existingFarmers: List<Farmer> = emptyList()): FarmerParseResult {
        return try {
            android.util.Log.d(TAG, "Attempting to parse farmer file with URI: $fileUri")
            
            // Use the exact same approach as FAT table ExcelParser
            try {
                android.util.Log.d(TAG, "Trying to parse as XLSX file...")
                val xlsxStream = contentResolver.openInputStream(fileUri)
                if (xlsxStream == null) {
                    return FarmerParseResult.Error("Could not read file")
                }
                val result = parseFarmerXLSX(xlsxStream, existingFarmers)
                xlsxStream.close()
                result
            } catch (e: Exception) {
                android.util.Log.d(TAG, "XLSX parsing failed, trying CSV: ${e.message}")
                
                // Try CSV with a new stream
                try {
                    val csvStream = contentResolver.openInputStream(fileUri)
                    if (csvStream == null) {
                        return FarmerParseResult.Error("Could not read file")
                    }
                    val result = parseFarmerCSV(csvStream, existingFarmers)
                    csvStream.close()
                    result
                } catch (e2: Exception) {
                    android.util.Log.e(TAG, "All parsing methods failed", e2)
                    FarmerParseResult.Error("Could not parse file. Please ensure it's a valid Excel (.xlsx) or CSV file. Note: .xls files are not supported.")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing farmer file: ${e.message}")
            FarmerParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    /**
     * Parse XLSX file for farmer data - using the working FAT table approach
     */
    private fun parseXLSXFile(fileUri: Uri, contentResolver: ContentResolver, existingFarmers: List<Farmer>): FarmerParseResult {
        return try {
            val inputStream = contentResolver.openInputStream(fileUri)
                ?: return FarmerParseResult.Error("Could not open file")
            
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            
            // Look for the shared strings file and worksheet
            var sharedStrings: List<String> = emptyList()
            var worksheetData: String? = null
            
            android.util.Log.d(TAG, "Starting XLSX parsing...")
            
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStringsFromStream(zipStream)
                        android.util.Log.d(TAG, "Found ${sharedStrings.size} shared strings")
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        worksheetData = zipStream.readBytes().toString(Charsets.UTF_8)
                        android.util.Log.d(TAG, "Found worksheet data")
                    }
                }
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            inputStream.close()
            
            if (worksheetData == null) {
                return FarmerParseResult.Error("Could not find worksheet data in Excel file")
            }
            
            // Parse the worksheet XML using the working FAT table approach
            parseWorksheetXMLForFarmers(worksheetData, sharedStrings, existingFarmers)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing XLSX file: ${e.message}")
            return FarmerParseResult.Error("Error parsing Excel file: ${e.message}")
        }
    }
    
    /**
     * Parse CSV file for farmer data
     */
    private fun parseCSVFile(fileUri: Uri, contentResolver: ContentResolver, existingFarmers: List<Farmer>): FarmerParseResult {
        return try {
            val inputStream = contentResolver.openInputStream(fileUri)
                ?: return FarmerParseResult.Error("Could not open file")
            
            val content = inputStream.readBytes().toString(Charsets.UTF_8)
            val lines = content.split("\n")
            val farmers = mutableListOf<Farmer>()
            var rowIndex = 0
            
            android.util.Log.d(TAG, "Starting CSV parsing...")
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue
                
                val columns = parseCSVLine(trimmedLine)
                if (columns.isNotEmpty()) {
                    if (rowIndex == 0) {
                        // Skip header row
                        android.util.Log.d(TAG, "Skipping header row: $columns")
                        rowIndex++
                        continue
                    }
                    
                    val farmer = parseRowToFarmer(columns, rowIndex, existingFarmers, farmers.size)
                    if (farmer != null) {
                        farmers.add(farmer)
                        android.util.Log.d(TAG, "Parsed farmer: $farmer")
                    }
                    rowIndex++
                }
            }
            
            inputStream.close()
            
            if (farmers.isEmpty()) {
                FarmerParseResult.Error("No valid farmer data found in CSV file")
            } else {
                android.util.Log.d(TAG, "Successfully parsed ${farmers.size} farmers from CSV")
                FarmerParseResult.Success(farmers)
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing CSV file: ${e.message}")
            FarmerParseResult.Error("Error parsing CSV file: ${e.message}")
        }
    }
    
    /**
     * Extract row data from XML line
     */
    private fun extractRowData(line: String): List<String> {
        val rowData = mutableListOf<String>()
        
        // Pattern to match cell values in XLSX format
        // This handles both direct values and shared string references
        val cellPattern = "<c[^>]*>.*?<v[^>]*>([^<]*)</v>.*?</c>".toRegex()
        val matches = cellPattern.findAll(line)
        
        for (match in matches) {
            val value = match.groupValues[1]
            rowData.add(value)
        }
        
        // Alternative pattern for cells without explicit value tags
        if (rowData.isEmpty()) {
            val simplePattern = "<v[^>]*>([^<]*)</v>".toRegex()
            val simpleMatches = simplePattern.findAll(line)
            for (match in simpleMatches) {
                val value = match.groupValues[1]
                rowData.add(value)
            }
        }
        
        android.util.Log.d(TAG, "Extracted row data: $rowData from line: ${line.take(100)}...")
        return rowData
    }
    
    /**
     * Parse shared strings from XLSX stream (working FAT table approach)
     */
    private fun parseSharedStringsFromStream(inputStream: java.io.InputStream): List<String> {
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
     * Parse shared strings from XLSX sharedStrings.xml (legacy method)
     */
    private fun parseSharedStrings(content: String): List<String> {
        val sharedStrings = mutableListOf<String>()
        
        // Pattern to match <si><t>text</t></si> structure
        val siPattern = "<si>.*?<t[^>]*>([^<]*)</t>.*?</si>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = siPattern.findAll(content)
        
        for (match in matches) {
            val text = match.groupValues[1]
            sharedStrings.add(text)
        }
        
        android.util.Log.d(TAG, "Parsed shared strings: $sharedStrings")
        return sharedStrings
    }
    
    /**
     * Parse worksheet rows from XLSX worksheet XML
     */
    private fun parseWorksheetRows(content: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        
        // Pattern to match <row>...</row> blocks
        val rowPattern = "<row[^>]*>.*?</row>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val rowMatches = rowPattern.findAll(content)
        
        for (rowMatch in rowMatches) {
            val rowContent = rowMatch.value
            val rowData = parseRowCells(rowContent, sharedStrings)
            if (rowData.isNotEmpty()) {
                rows.add(rowData)
                android.util.Log.d(TAG, "Parsed row: $rowData")
            }
        }
        
        return rows
    }
    
    /**
     * Parse cells from a row XML content
     */
    private fun parseRowCells(rowContent: String, sharedStrings: List<String>): List<String> {
        val cells = mutableListOf<String>()
        
        // Pattern to match <c>...</c> cell blocks
        val cellPattern = "<c[^>]*>.*?</c>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val cellMatches = cellPattern.findAll(rowContent)
        
        for (cellMatch in cellMatches) {
            val cellContent = cellMatch.value
            val cellValue = parseCellValue(cellContent, sharedStrings)
            if (cellValue != null) {
                cells.add(cellValue)
            }
        }
        
        return cells
    }
    
    /**
     * Parse individual cell value from cell XML
     */
    private fun parseCellValue(cellContent: String, sharedStrings: List<String>): String? {
        // Check if it's a shared string reference
        val tPattern = "<t[^>]*>([^<]*)</t>".toRegex()
        val vPattern = "<v[^>]*>([^<]*)</v>".toRegex()
        
        // First try to get direct text value
        val tMatch = tPattern.find(cellContent)
        if (tMatch != null) {
            return tMatch.groupValues[1]
        }
        
        // Then try to get value and check if it's a shared string reference
        val vMatch = vPattern.find(cellContent)
        if (vMatch != null) {
            val value = vMatch.groupValues[1]
            
            // Check if this cell references a shared string
            if (cellContent.contains("t=\"s\"")) {
                val index = value.toIntOrNull()
                if (index != null && index < sharedStrings.size) {
                    return sharedStrings[index]
                }
            } else {
                // Direct value
                return value
            }
        }
        
        return null
    }
    
    /**
     * Parse worksheet XML and extract farmer data using the working FAT table approach
     */
    private fun parseWorksheetXMLForFarmers(xml: String, sharedStrings: List<String>, existingFarmers: List<Farmer>): FarmerParseResult {
        val rows = mutableListOf<List<String>>()
        
        // Extract row data from XML using the working FAT table approach
        val rowPattern = "<row[^>]*>.*?</row>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val cellPattern = "<c[^>]*r=\"([A-Z]+)(\\d+)\"[^>]*>.*?<v>([^<]*)</v>.*?</c>".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        val rowMatches = rowPattern.findAll(xml)
        
        for (rowMatch in rowMatches) {
            val rowXml = rowMatch.value
            val cells = mutableMapOf<Int, String>()
            
            // Simple approach: try to resolve all values as shared strings first, then fallback to direct values
            val cellPattern = "<c[^>]*r=\"([A-Z]+)(\\d+)\"[^>]*>.*?<v>([^<]*)</v>.*?</c>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val cellMatches = cellPattern.findAll(rowXml)
            
            for (cellMatch in cellMatches) {
                val column = cellMatch.groupValues[1]
                val rowNum = cellMatch.groupValues[2].toIntOrNull() ?: continue
                val value = cellMatch.groupValues[3]
                
                // Convert column letter to number (A=1, B=2, etc.)
                val colNum = columnToNumber(column)
                if (colNum in 1..3) { // Only process first 3 columns (name, phone, address)
                    // Try to resolve as shared string first
                    val stringIndex = value.toIntOrNull()
                    val resolvedValue = if (stringIndex != null && stringIndex < sharedStrings.size) {
                        // This is likely a shared string reference
                        sharedStrings[stringIndex]
                    } else {
                        // Direct value
                        value
                    }
                    cells[colNum - 1] = resolvedValue
                    android.util.Log.d(TAG, "Cell $column$rowNum: value=$value, resolved=$resolvedValue")
                }
            }
            
            // Add ALL rows that have ANY data - we'll validate them later
            if (cells.isNotEmpty()) {
                val rowData = listOf(
                    cells[0] ?: "",
                    cells[1] ?: "",
                    cells[2] ?: "" // Address is optional
                )
                rows.add(rowData)
                android.util.Log.d(TAG, "Added row data: name='${rowData[0]}', phone='${rowData[1]}', address='${rowData[2]}'")
            }
        }
        
        // Skip header row and get data rows
        val dataRows = rows.drop(1)
        val validationErrors = mutableListOf<SkippedRowInfo>()
        
        // First pass: Validate ALL rows before processing any
        for ((index, rowData) in dataRows.withIndex()) {
            val rowIndex = index + 2 // Excel rows start from 2 (1 is header)
            
            android.util.Log.d(TAG, "Validating row $rowIndex: name='${rowData.getOrNull(0)}', phone='${rowData.getOrNull(1)}', address='${rowData.getOrNull(2)}'")
            
            // Validate first column (name) - should not be empty and should be text
            if (rowData.isNotEmpty() && rowData[0].isBlank()) {
                android.util.Log.w(TAG, "Row $rowIndex: Name is empty")
                validationErrors.add(SkippedRowInfo(rowIndex, "Name cannot be empty"))
            }
            
            // Check if row has phone but no name (invalid data)
            if (rowData.size >= 2 && rowData[0].isBlank() && rowData[1].isNotBlank()) {
                android.util.Log.w(TAG, "Row $rowIndex: Has phone '${rowData[1]}' but no name")
                validationErrors.add(SkippedRowInfo(rowIndex, "Row has phone number but no name"))
            }
        }
        
        // If ANY validation errors found, reject entire sheet
        if (validationErrors.isNotEmpty()) {
            android.util.Log.w(TAG, "Validation failed with ${validationErrors.size} errors:")
            validationErrors.forEach { error ->
                android.util.Log.w(TAG, "  Row ${error.rowNumber}: ${error.reason}")
            }
            
            val errorMessage = buildString {
                append("File validation failed. ${validationErrors.size} rows have issues:\n")
                validationErrors.take(5).forEach { error ->
                    append("• Row ${error.rowNumber}: ${error.reason}\n")
                }
                if (validationErrors.size > 5) {
                    append("• ... and ${validationErrors.size - 5} more rows with issues")
                }
                append("\nPlease fix all issues and try again.")
            }
            return FarmerParseResult.Error(errorMessage)
        }
        
        android.util.Log.d(TAG, "All rows passed validation, proceeding with import")
        
        // Second pass: All rows are valid, now parse them
        val farmers = mutableListOf<Farmer>()
        for ((index, rowData) in dataRows.withIndex()) {
            val rowIndex = index + 2
            val farmer = parseRowToFarmer(rowData, rowIndex, existingFarmers, farmers.size)
            if (farmer != null) {
                farmers.add(farmer)
                android.util.Log.d(TAG, "Parsed farmer: $farmer")
            }
        }
        
        if (farmers.isEmpty()) {
            return FarmerParseResult.Error("No valid farmer data found in Excel file. Please check the file format and ensure it has name and phone columns.")
        }
        
        android.util.Log.d(TAG, "Successfully parsed ${farmers.size} farmers from Excel")
        return FarmerParseResult.Success(farmers, dataRows.size, emptyList())
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
     * Parse CSV line handling quotes and commas
     */
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (i in line.indices) {
            val char = line[i]
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        
        return result
    }
    
    /**
     * Parse row data to Farmer object
     */
    private fun parseRowToFarmer(rowData: List<String>, rowIndex: Int, existingFarmers: List<Farmer>, currentBatchIndex: Int): Farmer? {
        try {
            // Validate column count (2-3 columns: name, phone, address)
            if (rowData.size < 2 || rowData.size > 3) {
                android.util.Log.w(TAG, "Row $rowIndex: Invalid column count ${rowData.size}. Expected 2-3 columns.")
                return null
            }
            
            val name = rowData[0].trim()
            val phone = rowData[1].trim()
            val address = if (rowData.size > 2) rowData[2].trim() else ""
            
            // Validate first column (name) - should not be empty and should be text
            if (name.isBlank()) {
                android.util.Log.w(TAG, "Row $rowIndex: Name cannot be empty")
                return null
            }
            
            // Generate unique ID following the same pattern as AddFarmerScreen
            val farmerId = generateSequentialFarmerId(existingFarmers, currentBatchIndex)
            
            return Farmer(
                id = farmerId,
                name = name,
                phone = phone,
                address = address,
                photoUrl = "",
                addedBy = "bulk_import", // Mark as bulk imported
                createdAt = Date(),
                updatedAt = Date(),
                synced = false,
                totalAmount = 0.0,
                pendingAmount = 0.0,
                billingCycles = ""
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing row $rowIndex: ${e.message}")
            return null
        }
    }
    
    /**
     * Validate phone number format - no validation, accept any value
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        // No validation - accept any phone number
        return true
    }
    
    /**
     * Generate sequential farmer ID following the same pattern as AddFarmerScreen
     * Starts from 101 and increments by 1, starting from the last existing farmer ID + 1
     */
    private fun generateSequentialFarmerId(existingFarmers: List<Farmer>, currentBatchIndex: Int): String {
        // Get existing IDs and find the maximum
        val existingIds = existingFarmers.mapNotNull { it.id.toIntOrNull() }
        val maxExistingId = existingIds.maxOrNull() ?: 100 // Default to 100 if no existing farmers
        
        // Start from maxExistingId + 1 + currentBatchIndex
        val nextId = maxExistingId + 1 + currentBatchIndex
        android.util.Log.d(TAG, "Generated farmer ID: $nextId (max existing: $maxExistingId, batch index: $currentBatchIndex)")
        return nextId.toString()
    }
    
    /**
     * Parse XLSX file for farmers - exact copy of working FAT table approach
     */
    private fun parseFarmerXLSX(inputStream: InputStream, existingFarmers: List<Farmer>): FarmerParseResult {
        return try {
            android.util.Log.d(TAG, "Starting XLSX parsing...")
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            
            // Look for the shared strings file and worksheet
            var sharedStrings: List<String> = emptyList()
            var worksheetData: String? = null
            
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStringsFromStream(zipStream)
                        android.util.Log.d(TAG, "Found ${sharedStrings.size} shared strings")
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        worksheetData = zipStream.readBytes().toString(Charsets.UTF_8)
                        android.util.Log.d(TAG, "Found worksheet data")
                    }
                }
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            
            if (worksheetData == null) {
                return FarmerParseResult.Error("Could not find worksheet data in Excel file")
            }
            
            // Parse the worksheet XML using the exact FAT table approach
            parseWorksheetXMLForFarmers(worksheetData, sharedStrings, existingFarmers)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing XLSX: ${e.message}")
            throw e
        }
    }
    
    /**
     * Parse CSV file for farmers - exact copy of working FAT table approach
     */
    private fun parseFarmerCSV(inputStream: InputStream, existingFarmers: List<Farmer>): FarmerParseResult {
        return try {
            android.util.Log.d(TAG, "Starting CSV file parsing...")
            
            val lines = inputStream.bufferedReader().readLines()
            
            if (lines.isEmpty()) {
                return FarmerParseResult.Error("File is empty")
            }
            
            // Skip header row (first line)
            val dataLines = lines.drop(1).filter { it.trim().isNotEmpty() }
            val validationErrors = mutableListOf<SkippedRowInfo>()
            
            // First pass: Validate ALL rows before processing any
            dataLines.forEachIndexed { index, line ->
                val rowIndex = index + 2 // +2 because we skipped header and 0-based index
                
                // Split by comma and clean up
                val columns = line.split(",").map { it.trim() }
                
                // Check if row has at least 2 columns (name and phone)
                if (columns.size < 2) {
                    validationErrors.add(SkippedRowInfo(rowIndex, "Row has ${columns.size} columns. Expected at least 2 columns"))
                    return@forEachIndexed
                }
                
                val name = columns[0]
                val phone = columns[1]
                val address = if (columns.size > 2) columns[2] else ""
                
                android.util.Log.d(TAG, "Validating CSV row $rowIndex: name='$name', phone='$phone', address='$address'")
                
                // Validate first column (name) - should not be empty and should be text
                if (name.isBlank()) {
                    android.util.Log.w(TAG, "CSV Row $rowIndex: Name is empty")
                    validationErrors.add(SkippedRowInfo(rowIndex, "Name cannot be empty"))
                }
                
                // Check if row has phone but no name (invalid data)
                if (name.isBlank() && phone.isNotBlank()) {
                    android.util.Log.w(TAG, "CSV Row $rowIndex: Has phone '$phone' but no name")
                    validationErrors.add(SkippedRowInfo(rowIndex, "Row has phone number but no name"))
                }
            }
            
            // If ANY validation errors found, reject entire sheet
            if (validationErrors.isNotEmpty()) {
                android.util.Log.w(TAG, "CSV Validation failed with ${validationErrors.size} errors:")
                validationErrors.forEach { error ->
                    android.util.Log.w(TAG, "  CSV Row ${error.rowNumber}: ${error.reason}")
                }
                
                val errorMessage = buildString {
                    append("File validation failed. ${validationErrors.size} rows have issues:\n")
                    validationErrors.take(5).forEach { error ->
                        append("• Row ${error.rowNumber}: ${error.reason}\n")
                    }
                    if (validationErrors.size > 5) {
                        append("• ... and ${validationErrors.size - 5} more rows with issues")
                    }
                    append("\nPlease fix all issues and try again.")
                }
                return FarmerParseResult.Error(errorMessage)
            }
            
            android.util.Log.d(TAG, "All CSV rows passed validation, proceeding with import")
            
            // Second pass: All rows are valid, now parse them
            val farmers = mutableListOf<Farmer>()
            dataLines.forEachIndexed { index, line ->
                val rowIndex = index + 2
                val columns = line.split(",").map { it.trim() }
                val name = columns[0]
                val phone = columns[1]
                val address = if (columns.size > 2) columns[2] else ""
                
                val farmer = parseRowToFarmer(listOf(name, phone, address), rowIndex, existingFarmers, farmers.size)
                if (farmer != null) {
                    farmers.add(farmer)
                }
                android.util.Log.d(TAG, "Parsed farmer: $farmer")
            }
            
            android.util.Log.d(TAG, "Successfully parsed ${farmers.size} farmers from CSV")
            FarmerParseResult.Success(farmers, dataLines.size, emptyList())
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing CSV file: ${e.message}")
            FarmerParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    /**
     * Check file type and provide helpful message
     */
    fun checkFileType(fileUri: Uri): String? {
        val fileName = fileUri.toString().lowercase()
        
        // For content URIs (Android file picker), we can't determine file type from URI alone
        // Return null to allow the file to be processed and let the parser handle validation
        if (fileName.startsWith("content://")) {
            android.util.Log.d(TAG, "Content URI detected, allowing file processing: $fileName")
            return null
        }
        
        return when {
            fileName.endsWith(".xlsx") -> null // Supported
            fileName.endsWith(".xls") -> "Please convert Excel file (.xls) to .xlsx format or save as CSV"
            fileName.endsWith(".csv") -> null // Supported
            else -> "Please select a supported file (.xlsx, .csv)"
        }
    }
}

/**
 * Result of farmer file parsing
 */
sealed class FarmerParseResult {
    data class Success(
        val farmers: List<Farmer>,
        val totalRowsProcessed: Int = 0,
        val skippedRows: List<SkippedRowInfo> = emptyList()
    ) : FarmerParseResult()
    data class Error(val message: String) : FarmerParseResult()
}

data class SkippedRowInfo(
    val rowNumber: Int,
    val reason: String
)
