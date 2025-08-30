package com.example.doodhsethu.utils

import com.example.doodhsethu.data.models.FatRangeRow

object FatTableUtils {
    
    /**
     * Get price for a given fat percentage from a list of fat ranges
     * @param fatPercentage The fat percentage to get price for
     * @param fatRanges List of fat ranges to search in
     * @return The price per liter for the given fat percentage, or 0.0 if no matching range
     */
    fun getPriceForFat(fatPercentage: Double, fatRanges: List<FatRangeRow>): Double {
        // Convert fatPercentage to Float and round to 3 decimal places for consistent comparison
        val roundedFatPercentage = (fatPercentage * 1000).toInt() / 1000.0
        
        android.util.Log.d("FatTableUtils", "Looking for price for fat: $fatPercentage (rounded: $roundedFatPercentage)")
        android.util.Log.d("FatTableUtils", "Available ranges: ${fatRanges.map { "${it.from}-${it.to} = ₹${it.price}" }}")
        
        val matchingRow = fatRanges.find { 
            val roundedFrom = (it.from * 1000).toInt() / 1000.0
            val roundedTo = (it.to * 1000).toInt() / 1000.0
            val matches = roundedFatPercentage >= roundedFrom && roundedFatPercentage <= roundedTo
            android.util.Log.d("FatTableUtils", "Checking range ${it.from}-${it.to} (rounded: $roundedFrom-$roundedTo): $matches")
            matches
        }
        
        val result = matchingRow?.price ?: 0.0
        android.util.Log.d("FatTableUtils", "Found price: $result for fat: $fatPercentage")
        return result
    }
    
    /**
     * Validate if a fat range overlaps with existing ranges
     * @param newRow The new fat range to validate
     * @param existingRows List of existing fat ranges
     * @param excludeId ID of the row being edited (to exclude from validation)
     * @return true if no overlap, false if overlap exists
     */
    fun validateFatRange(newRow: FatRangeRow, existingRows: List<FatRangeRow>, excludeId: Int? = null): Boolean {
        for (existingRow in existingRows) {
            // Skip the row being edited
            if (excludeId != null && existingRow.id == excludeId) {
                continue
            }
            
            // Check for overlap: new range overlaps with existing range
            val overlaps = !(newRow.to <= existingRow.from || newRow.from >= existingRow.to)
            
            if (overlaps) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Sort fat ranges by from value in ascending order
     * @param fatRanges List of fat ranges to sort
     * @return Sorted list of fat ranges
     */
    fun sortFatRanges(fatRanges: List<FatRangeRow>): List<FatRangeRow> {
        return fatRanges.sortedBy { it.from }
    }
}
