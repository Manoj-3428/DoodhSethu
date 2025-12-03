package com.example.doodhsethu.utils

import android.content.Context
import android.util.Log
import com.example.doodhsethu.data.models.FatRangeRow
import java.io.InputStream

object SimpleExcelParser {
    private const val TAG = "SimpleExcelParser"
    
    /**
     * Simple CSV parser as fallback when POI fails
     * Expected format: 3 columns - From Value, To Value, Price
     */
    fun parseFatTableCSV(inputStream: InputStream): ExcelParseResult {
        return try {
            Log.d(TAG, "Starting CSV file parsing...")
            
            val lines = inputStream.bufferedReader().readLines()
            val fatRows = mutableListOf<FatRangeRow>()
            
            if (lines.isEmpty()) {
                return ExcelParseResult.Error("File is empty")
            }
            
            // Skip header row (first line)
            val dataLines = lines.drop(1).filter { it.trim().isNotEmpty() }
            
            dataLines.forEachIndexed { index, line ->
                val rowIndex = index + 2 // +2 because we skipped header and 0-based index
                
                // Split by comma and clean up
                val columns = line.split(",").map { it.trim() }
                
                // Check if row has exactly 3 columns
                if (columns.size != 3) {
                    return ExcelParseResult.Error(
                        "Row $rowIndex has ${columns.size} columns. Expected exactly 3 columns (From Value, To Value, Price)"
                    )
                }
                
                try {
                    val fromValue = parseNumericValue(columns[0])
                    val toValue = parseNumericValue(columns[1])
                    val price = parseNumericValue(columns[2])
                    
                    // Validate values
                    if (fromValue < 0 || toValue < 0 || price < 0) {
                        return ExcelParseResult.Error(
                            "Row $rowIndex: All values must be positive numbers"
                        )
                    }
                    
                    if (fromValue >= toValue) {
                        return ExcelParseResult.Error(
                            "Row $rowIndex: 'From Value' must be less than 'To Value'"
                        )
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
                    return ExcelParseResult.Error(
                        "Row $rowIndex: Invalid numeric values. Please ensure all values are numbers"
                    )
                }
            }
            
            if (fatRows.isEmpty()) {
                return ExcelParseResult.Error("No valid data found in file")
            }
            
            Log.d(TAG, "Successfully parsed ${fatRows.size} FAT table entries")
            ExcelParseResult.Success(fatRows)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSV file: ${e.message}")
            ExcelParseResult.Error("Error reading file: ${e.message}")
        }
    }
    
    /**
     * Parse string value to double
     */
    private fun parseNumericValue(value: String): Double {
        return value.trim().replace(",", "").toDouble()
    }
}
