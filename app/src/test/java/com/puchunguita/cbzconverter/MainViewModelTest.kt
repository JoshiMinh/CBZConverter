package com.puchunguita.cbzconverter

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations


/**
 * Unit tests for MainViewModel
 * Tests the batch size functionality and other configuration options
 */
class MainViewModelTest {

    @Mock
    private lateinit var mockContextHelper: ContextHelper

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel = MainViewModel(mockContextHelper)
    }

    @Test
    fun `batchSize should have default value of 300`() {
        // Given: Default MainViewModel
        
        // When: Get the batch size
        val batchSize = viewModel.batchSize.value
        
        // Then: Should be default value
        assertEquals(300, batchSize)
    }

    @Test
    fun `updateBatchSizeFromUserInput should update batch size with valid input`() {
        // Given: Valid batch size input
        val newBatchSize = "150"
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Batch size should be updated
        assertEquals(150, viewModel.batchSize.value)
    }

    @Test
    fun `updateBatchSizeFromUserInput should handle zero input`() {
        // Given: Zero batch size input
        val newBatchSize = "0"
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Should revert to default value
        assertEquals(300, viewModel.batchSize.value)
    }

    @Test
    fun `updateBatchSizeFromUserInput should handle negative input`() {
        // Given: Negative batch size input
        val newBatchSize = "-50"
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Should revert to default value
        assertEquals(300, viewModel.batchSize.value)
    }

    @Test
    fun `updateBatchSizeFromUserInput should handle invalid input`() {
        // Given: Invalid batch size input
        val newBatchSize = "invalid"
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Should revert to default value
        assertEquals(300, viewModel.batchSize.value)
    }

    @Test
    fun `updateBatchSizeFromUserInput should handle empty input`() {
        // Given: Empty batch size input
        val newBatchSize = ""
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Should revert to default value
        assertEquals(300, viewModel.batchSize.value)
    }

    @Test
    fun `updateBatchSizeFromUserInput should handle whitespace input`() {
        // Given: Whitespace batch size input
        val newBatchSize = "  200  "
        
        // When: Update batch size
        viewModel.updateBatchSizeFromUserInput(newBatchSize)
        
        // Then: Should trim and use the value
        assertEquals(200, viewModel.batchSize.value)
    }

    @Test
    fun `maxNumberOfPages should have default value of 10000`() {
        // Given: Default MainViewModel
        
        // When: Get the max number of pages
        val maxPages = viewModel.maxNumberOfPages.value
        
        // Then: Should be default value
        assertEquals(10000, maxPages)
    }

    @Test
    fun `updateMaxNumberOfPagesSizeFromUserInput should update max pages with valid input`() {
        // Given: Valid max pages input
        val newMaxPages = "500"
        
        // When: Update max pages
        viewModel.updateMaxNumberOfPagesSizeFromUserInput(newMaxPages)
        
        // Then: Max pages should be updated
        assertEquals(500, viewModel.maxNumberOfPages.value)
    }

    @Test
    fun `updateMaxNumberOfPagesSizeFromUserInput should handle invalid input`() {
        // Given: Invalid max pages input
        val newMaxPages = "invalid"
        
        // When: Update max pages
        viewModel.updateMaxNumberOfPagesSizeFromUserInput(newMaxPages)
        
        // Then: Should revert to default value
        assertEquals(10000, viewModel.maxNumberOfPages.value)
    }

    @Test
    fun `overrideSortOrderToUseOffset should toggle correctly`() {
        // Given: Initial state
        assertFalse(viewModel.overrideSortOrderToUseOffset.value)
        
        // When: Toggle to true
        viewModel.toggleOverrideSortOrderToUseOffset(true)
        
        // Then: Should be true
        assertTrue(viewModel.overrideSortOrderToUseOffset.value)
        
        // When: Toggle to false
        viewModel.toggleOverrideSortOrderToUseOffset(false)
        
        // Then: Should be false
        assertFalse(viewModel.overrideSortOrderToUseOffset.value)
    }

    @Test
    fun `overrideMergeFiles should toggle correctly`() {
        // Given: Initial state
        assertFalse(viewModel.overrideMergeFiles.value)
        
        // When: Toggle to true
        viewModel.toggleMergeFilesOverride(true)
        
        // Then: Should be true
        assertTrue(viewModel.overrideMergeFiles.value)
        
        // When: Toggle to false
        viewModel.toggleMergeFilesOverride(false)
        
        // Then: Should be false
        assertFalse(viewModel.overrideMergeFiles.value)
    }

    @Test
    fun `updateOverrideFileNameFromUserInput should update file name with valid input`() {
        // Given: Valid file name input
        val newFileName = "test_file"
        
        // When: Update file name
        viewModel.updateOverrideFileNameFromUserInput(newFileName)
        
        // Then: File name should be updated
        assertEquals("test_file", viewModel.overrideFileName.value)
    }

    @Test
    fun `updateOverrideFileNameFromUserInput should handle empty input`() {
        // Given: Empty file name input
        val newFileName = ""
        
        // When: Update file name
        viewModel.updateOverrideFileNameFromUserInput(newFileName)
        
        // Then: Should revert to empty string
        assertEquals("", viewModel.overrideFileName.value)
    }

    @Test
    fun `updateOverrideFileNameFromUserInput should handle blank input`() {
        // Given: Blank file name input
        val newFileName = "   "
        
        // When: Update file name
        viewModel.updateOverrideFileNameFromUserInput(newFileName)
        
        // Then: Should revert to empty string
        assertEquals("", viewModel.overrideFileName.value)
    }

    @Test
    fun `isCurrentlyConverting should start as false`() {
        // Given: Default MainViewModel
        // Then: Conversion status should start as false
        assertFalse(viewModel.isCurrentlyConverting.value)
    }

    @Test
    fun `currentTaskStatus should start with default message`() {
        // Given: Default MainViewModel
        // Then: Task status should have default message as "Nothing Processing"
        assertEquals("Nothing Processing", viewModel.currentTaskStatus.value)
    }

    @Test
    fun `currentSubTaskStatus should start with default message`() {
        // Given: Default MainViewModel
        // Then: Sub task status should have default message as "Nothing Processing"
        assertEquals("Nothing Processing", viewModel.currentSubTaskStatus.value)
    }

    @Test
    fun `selectedFileName should start with default message`() {
        // Given: Default MainViewModel
        // Then: Selected file name should have default message as "No file selected"
        assertEquals("No file selected", viewModel.selectedFileName.value)
    }

    @Test
    fun `selectedFileUri should start as empty list`() {
        // Given: Default MainViewModel
        // Then: Selected file URIs should be empty
        assertTrue(viewModel.selectedFileUri.value.isEmpty())
    }

    @Test
    fun `overrideOutputDirectoryUri should start as null`() {
        // Given: Default MainViewModel
        // Then: Override output directory URI should be null
        assertNull(viewModel.overrideOutputDirectoryUri.value)
    }
} 