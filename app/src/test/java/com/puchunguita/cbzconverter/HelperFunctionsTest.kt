package com.puchunguita.cbzconverter

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.ceil

/**
 * Unit tests for helper functions in ConversionFunctions
 * These tests focus on pure functions that don't require complex mocking
 */
class HelperFunctionsTest {

    @Test
    fun `calculateRange should handle edge cases correctly`() {
        // Test various edge cases for the calculateRange function
        
        // Test with zero values
        val (start1, end1) = calculateRange(0, 0, 0)
        assertEquals(0, start1)
        assertEquals(0, end1)
        
        // Test with very large numbers
        val (start3, end3) = calculateRange(1000, 100, 100000)
        assertEquals(100000, start3) // 1000 * 100 = 100000
        assertEquals(100000, end3)   // 1001 * 100 = 100100, but capped at 100000
    }

    @Test
    fun `calculateRange should work with different page sizes`() {
        // Test with different page sizes to ensure flexibility
        
        // Small page size
        val (start1, end1) = calculateRange(5, 10, 100)
        assertEquals(50, start1)
        assertEquals(60, end1)
        
        // Large page size
        val (start2, end2) = calculateRange(2, 1000, 2500)
        assertEquals(2000, start2)
        assertEquals(2500, end2)
        
        // Page size equals total items
        val (start3, end3) = calculateRange(0, 100, 100)
        assertEquals(0, start3)
        assertEquals(100, end3)
    }

    @Test
    fun `calculateRange should handle boundary conditions`() {
        // Test boundary conditions where index * pageSize equals totalItems
        
        val (start1, end1) = calculateRange(2, 50, 100)
        assertEquals(100, start1)
        assertEquals(100, end1)
        
        val (start2, end2) = calculateRange(1, 50, 100)
        assertEquals(50, start2)
        assertEquals(100, end2)
    }

    @Test
    fun `calculateRange should work with single item batches`() {
        // Test with page size of 1 (single item batches)
        
        val (start1, end1) = calculateRange(5, 1, 10)
        assertEquals(5, start1)
        assertEquals(6, end1)
        
        val (start2, end2) = calculateRange(9, 1, 10)
        assertEquals(9, start2)
        assertEquals(10, end2)
    }

    @Test
    fun `calculateRange should handle fractional results correctly`() {
        // Test cases where the calculation would result in fractional values
        // but should be handled correctly by integer arithmetic
        
        val (start1, end1) = calculateRange(1, 3, 7)
        assertEquals(3, start1)
        assertEquals(6, end1)
        
        val (start2, end2) = calculateRange(2, 3, 7)
        assertEquals(6, start2)
        assertEquals(7, end2)
    }

    @Test
    fun `calculateRange should handle very large indices`() {
        // Test with very large index values to ensure no integer overflow
        
        val largeIndex = 1000000
        val pageSize = 100
        val totalItems = 100000000
        
        val (start, end) = calculateRange(largeIndex, pageSize, totalItems)
        assertEquals(largeIndex * pageSize, start)
        assertEquals(totalItems, end) // Should be capped at totalItems
    }

    @Test
    fun `calculateRange should work with batch size calculations`() {
        // Test scenarios that mimic real batch size calculations
        
        val totalImages = 1500
        val batchSize = 300
        val expectedBatches = ceil(totalImages.toDouble() / batchSize).toInt()
        
        // Test first batch
        val (start1, end1) = calculateRange(0, batchSize, totalImages)
        assertEquals(0, start1)
        assertEquals(300, end1)
        
        // Test last batch
        val (start2, end2) = calculateRange(expectedBatches - 1, batchSize, totalImages)
        assertEquals(1200, start2)
        assertEquals(1500, end2)
    }

    @Test
    fun `calculateRange should work with max pages calculations`() {
        // Test scenarios that mimic real max pages calculations
        
        val totalImages = 2500
        val maxPages = 500
        val expectedParts = ceil(totalImages.toDouble() / maxPages).toInt()
        
        // Test first part
        val (start1, end1) = calculateRange(0, maxPages, totalImages)
        assertEquals(0, start1)
        assertEquals(500, end1)
        
        // Test middle part
        val (start2, end2) = calculateRange(2, maxPages, totalImages)
        assertEquals(1000, start2)
        assertEquals(1500, end2)
        
        // Test last part
        val (start3, end3) = calculateRange(expectedParts - 1, maxPages, totalImages)
        assertEquals(2000, start3)
        assertEquals(2500, end3)
    }
} 