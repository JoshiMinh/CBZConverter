package com.joshiminh.cbzconverter.backend

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App state holder and conversion orchestrator.
 *
 * Exposes UI state via StateFlows and provides actions for:
 * - Selecting files/directories (delegates permission checks to PermissionsManager)
 * - Updating configuration overrides
 * - Converting CBZ -> PDF (on a background dispatcher)
 */
class MainViewModel(private val contextHelper: ContextHelper) : ViewModel() {

    companion object {
        private const val NOTHING_PROCESSING = "Nothing Processing"
        private const val NO_FILE_SELECTED = "No file selected"
        const val EMPTY_STRING = ""
        private const val DEFAULT_MAX_NUMBER_OF_PAGES = 10_000
        private const val DEFAULT_BATCH_SIZE = 300
    }

    private val logger = Logger.getLogger(MainViewModel::class.java.name)

    // ---------------------------- UI State ----------------------------

    private val _isCurrentlyConverting = MutableStateFlow(false)
    val isCurrentlyConverting = _isCurrentlyConverting.asStateFlow()

    private val _currentTaskStatus = MutableStateFlow(NOTHING_PROCESSING)
    val currentTaskStatus = _currentTaskStatus.asStateFlow()

    private val _currentSubTaskStatus = MutableStateFlow(NOTHING_PROCESSING)
    val currentSubTaskStatus = _currentSubTaskStatus.asStateFlow()

    private val _maxNumberOfPages = MutableStateFlow(DEFAULT_MAX_NUMBER_OF_PAGES)
    val maxNumberOfPages = _maxNumberOfPages.asStateFlow()

    private val _batchSize = MutableStateFlow(DEFAULT_BATCH_SIZE)
    val batchSize = _batchSize.asStateFlow()

    private val _overrideSortOrderToUseOffset = MutableStateFlow(false)
    val overrideSortOrderToUseOffset = _overrideSortOrderToUseOffset.asStateFlow()

    private val _overrideMergeFiles = MutableStateFlow(false)
    val overrideMergeFiles = _overrideMergeFiles.asStateFlow()

    private val _selectedFileName = MutableStateFlow(NO_FILE_SELECTED)
    val selectedFileName = _selectedFileName.asStateFlow()

    private val _selectedFileUri = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFileUri = _selectedFileUri.asStateFlow()

    private val _overrideFileName = MutableStateFlow(EMPTY_STRING)
    val overrideFileName = _overrideFileName.asStateFlow()

    private val _overrideOutputDirectoryUri = MutableStateFlow<Uri?>(null)
    val overrideOutputDirectoryUri = _overrideOutputDirectoryUri.asStateFlow()

    // --------------------------- Mutators ----------------------------

    fun toggleOverrideSortOrderToUseOffset(newValue: Boolean) {
        _overrideSortOrderToUseOffset.update { newValue }
    }

    fun toggleMergeFilesOverride(newValue: Boolean) {
        _overrideMergeFiles.update { newValue }
    }

    fun updateMaxNumberOfPagesSizeFromUserInput(maxNumberOfPages: String) {
        val parsed = maxNumberOfPages.trim().toIntOrNull()
        if (parsed != null && parsed > 0) {
            _maxNumberOfPages.update { parsed }
            appendTask("Updated maxNumberOfPages size: $parsed")
        } else {
            appendTask("Invalid maxNumberOfPages size: $maxNumberOfPages — reverting to default")
            _maxNumberOfPages.update { DEFAULT_MAX_NUMBER_OF_PAGES }
        }
    }

    fun updateBatchSizeFromUserInput(batchSize: String) {
        val parsed = batchSize.trim().toIntOrNull()
        if (parsed != null && parsed > 0) {
            _batchSize.update { parsed }
            appendTask("Updated batch size: $parsed")
        } else {
            appendTask("Invalid batch size: $batchSize — reverting to default")
            _batchSize.update { DEFAULT_BATCH_SIZE }
        }
    }

    fun updateOverrideFileNameFromUserInput(newOverrideFileName: String) {
        if (newOverrideFileName.isBlank()) {
            appendTask("Invalid overrideFileName: \"$newOverrideFileName\" — reverting to empty")
            _overrideFileName.update { EMPTY_STRING }
        } else {
            _overrideFileName.update { newOverrideFileName.trim() }
            appendTask("Updated overrideFileName: ${_overrideFileName.value}")
        }
    }

    fun updateOverrideOutputPathFromUserInput(newOverrideOutputPath: Uri) {
        _overrideOutputDirectoryUri.update { newOverrideOutputPath }
        appendTask("Updated overrideOutputPath: $newOverrideOutputPath")
    }

    fun updateUpdateSelectedFileUriFromUserInput(newSelectedFileUris: List<Uri>) {
        // Backward-compatible entry point (kept for existing callers)
        updateSelectedFileUrisFromUserInput(newSelectedFileUris)
    }

    fun updateSelectedFileUrisFromUserInput(newSelectedFileUris: List<Uri>) {
        try {
            _selectedFileUri.update { newSelectedFileUris }

            val names = newSelectedFileUris.joinToString(separator = "\n") { it.getFileName() }

            updateSelectedFileNameFromUserInput(names)
            setTask("Updated SelectedFileUri: $newSelectedFileUris")
            setSubTask("Files selected. Ready to Convert")
        } catch (_: Exception) {
            appendTask("Invalid SelectedFileUri: $newSelectedFileUris — reverting to empty")
            _selectedFileUri.update { emptyList() }
        }
    }

    // ------------------------ Conversion Flow ------------------------

    fun convertToPDF(fileUris: List<Uri>) {
        if (_isCurrentlyConverting.value) {
            // Avoid double triggers
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            setConverting(true)

            try {
                val pdfFileNames = getPdfFileNames(fileUris)
                val outputFolder = getOutputFolder()

                setTask("Conversion from CBZ to PDF started")
                setSubTask("")

                val pdfFiles = convertCbzToPdf(
                    fileUri = fileUris,
                    contextHelper = contextHelper,
                    subStepStatusAction = { message ->
                        // Ensure sub-task updates happen on Main
                        viewModelScope.launch(Dispatchers.Main) {
                            appendSubTask(message)
                        }
                    },
                    maxNumberOfPages = _maxNumberOfPages.value,
                    batchSize = _batchSize.value,
                    outputFileNames = pdfFileNames,
                    overrideSortOrderToUseOffset = _overrideSortOrderToUseOffset.value,
                    overrideMergeFiles = _overrideMergeFiles.value,
                    outputDirectory = outputFolder
                )

                handlePdfResult(pdfFiles)
            } catch (e: Exception) {
                showToastAndTask(
                    message = "Conversion failed: ${e.message}",
                    toastLength = Toast.LENGTH_LONG,
                    loggerLevel = Level.WARNING
                )
                logger.warning("Conversion failed stacktrace: ${e.stackTrace.contentToString()}")
            } finally {
                setConverting(false)
            }
        }
    }

    private suspend fun handlePdfResult(pdfFiles: List<File>) {
        if (pdfFiles.isEmpty()) {
            throw IllegalStateException("No PDF files created, CBZ file is invalid or empty")
        }

        val msg = if (pdfFiles.size == 1) {
            "PDF created: ${pdfFiles.first().absolutePath}"
        } else {
            "Multiple PDFs created:\n" + pdfFiles.joinToString(separator = "\n") { it.absolutePath }
        }

        showToastAndTask(message = msg, toastLength = Toast.LENGTH_LONG)
        appendTask("Conversion from CBZ to PDF Completed")
    }

    // ------------------------ Permissions API ------------------------

    fun checkPermissionAndSelectFileAction(
        activity: ComponentActivity,
        filePickerLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>
    ) {
        PermissionsManager.checkPermissionAndSelectFileAction(activity, filePickerLauncher)
    }

    fun checkPermissionAndSelectDirectoryAction(
        activity: ComponentActivity,
        directoryPickerLauncher: ManagedActivityResultLauncher<Uri?, Uri?>
    ) {
        PermissionsManager.checkPermissionAndSelectDirectoryAction(activity, directoryPickerLauncher)
    }

    // --------------------------- Helpers -----------------------------

    private suspend fun setConverting(value: Boolean) {
        withContext(Dispatchers.Main) {
            _isCurrentlyConverting.update { value }
            logger.info(if (value) "Conversion started" else "Conversion ended")
        }
    }

    private fun setTask(message: String) {
        _currentTaskStatus.update { message }
    }

    private fun appendTask(message: String) {
        _currentTaskStatus.update { current -> "$message\n$current" }
    }

    private fun setSubTask(message: String) {
        _currentSubTaskStatus.update { message }
    }

    private suspend fun appendSubTask(message: String) {
        withContext(Dispatchers.Main) {
            _currentSubTaskStatus.update { current -> "$message\n$current" }
            logger.info(message)
        }
    }

    private fun updateSelectedFileNameFromUserInput(newSelectedFileNames: String) {
        if (newSelectedFileNames.isBlank()) {
            appendTask("Invalid selectedFileName: \"$newSelectedFileNames\" — reverting to empty")
            _selectedFileName.update { EMPTY_STRING }
        } else {
            _selectedFileName.update { newSelectedFileNames }
            // Clear override when new files are chosen
            updateOverrideFileNameFromUserInput(EMPTY_STRING)
            appendTask("Updated selectedFileName:\n$newSelectedFileNames")
        }
    }

    private fun getPdfFileNames(filesUri: List<Uri>): List<String> {
        val baseNames = filesUri.map { it.getFileName() }

        val chosenCbzNames = if (_overrideFileName.value.isNotBlank()) {
            if (baseNames.size == 1) {
                listOf("${_overrideFileName.value}.cbz")
            } else {
                List(baseNames.size) { index -> "${_overrideFileName.value}_${index + 1}.cbz" }
            }
        } else {
            baseNames
        }

        return chosenCbzNames.map { it.replace(".cbz", ".pdf", ignoreCase = true) }
    }

    @Suppress("DEPRECATION")
    private fun getOutputFolder(): File {
        var outputFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        overrideOutputDirectoryUri.value?.let { uri ->
            outputFolder = contextHelper.getOutputFolderUri(uri)
        }
        return outputFolder
    }

    private suspend fun showToastAndTask(
        message: String,
        toastLength: Int,
        loggerLevel: Level = Level.INFO
    ) {
        withContext(Dispatchers.Main) {
            appendTask(message)
            logger.log(loggerLevel, message)
            contextHelper.showToast(message, toastLength)
        }
    }

    // Extension uses ContextHelper to resolve a display name for the Uri.
    private fun Uri.getFileName(): String = contextHelper.getFileName(this)
}