package com.example.doodhsethu.utils

import com.example.doodhsethu.data.models.FatRangeRow
import org.junit.Test
import org.junit.Assert.*

class FatTableUtilsTest {
    
    @Test
    fun `getPriceForFat should return correct price for fat percentage`() {
        val fatRanges = listOf(
            FatRangeRow(from = 3.0f, to = 4.0f, price = 50.0),
            FatRangeRow(from = 4.0f, to = 5.0f, price = 55.0),
            FatRangeRow(from = 5.0f, to = 6.0f, price = 60.0)
        )
        
        assertEquals(50.0, FatTableUtils.getPriceForFat(3.5, fatRanges), 0.01)
        assertEquals(55.0, FatTableUtils.getPriceForFat(4.5, fatRanges), 0.01)
        assertEquals(60.0, FatTableUtils.getPriceForFat(5.5, fatRanges), 0.01)
        assertEquals(0.0, FatTableUtils.getPriceForFat(2.5, fatRanges), 0.01) // No match
        assertEquals(0.0, FatTableUtils.getPriceForFat(6.5, fatRanges), 0.01) // No match
    }
    
    @Test
    fun `validateFatRange should return true for non-overlapping ranges`() {
        val existingRanges = listOf(
            FatRangeRow(from = 3.0f, to = 4.0f, price = 50.0),
            FatRangeRow(from = 5.0f, to = 6.0f, price = 60.0)
        )
        
        val newRange = FatRangeRow(from = 4.0f, to = 5.0f, price = 55.0)
        assertTrue(FatTableUtils.validateFatRange(newRange, existingRanges))
    }
    
    @Test
    fun `validateFatRange should return false for overlapping ranges`() {
        val existingRanges = listOf(
            FatRangeRow(from = 3.0f, to = 4.0f, price = 50.0),
            FatRangeRow(from = 5.0f, to = 6.0f, price = 60.0)
        )
        
        val overlappingRange = FatRangeRow(from = 3.5f, to = 5.5f, price = 55.0)
        assertFalse(FatTableUtils.validateFatRange(overlappingRange, existingRanges))
    }
    
    @Test
    fun `validateFatRange should exclude current row when editing`() {
        val existingRanges = listOf(
            FatRangeRow(id = 1, from = 3.0f, to = 4.0f, price = 50.0),
            FatRangeRow(id = 2, from = 5.0f, to = 6.0f, price = 60.0)
        )
        
        // Editing row with id = 1, changing its range to overlap with row 2
        val editedRange = FatRangeRow(id = 1, from = 3.0f, to = 5.5f, price = 55.0)
        assertFalse(FatTableUtils.validateFatRange(editedRange, existingRanges, 1))
    }
    
    @Test
    fun `sortFatRanges should sort by from value in ascending order`() {
        val unsortedRanges = listOf(
            FatRangeRow(from = 5.0f, to = 6.0f, price = 60.0),
            FatRangeRow(from = 3.0f, to = 4.0f, price = 50.0),
            FatRangeRow(from = 4.0f, to = 5.0f, price = 55.0)
        )
        
        val sortedRanges = FatTableUtils.sortFatRanges(unsortedRanges)
        
        assertEquals(3.0f, sortedRanges[0].from, 0.01f)
        assertEquals(4.0f, sortedRanges[1].from, 0.01f)
        assertEquals(5.0f, sortedRanges[2].from, 0.01f)
    }
}
