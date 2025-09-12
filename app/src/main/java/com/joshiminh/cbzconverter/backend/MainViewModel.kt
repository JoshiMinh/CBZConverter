package com.joshiminh.cbzconverter.backend

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
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

data class MihonCbzFile(val name: String, val uri: Uri)
data class MihonMangaEntry(val name: String, val files: List<MihonCbzFile>)

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
        private const val NOTHING_PROCESSING = "Idle"
        private const val NO_FILE_SELECTED = "No file"
        const val EMPTY_STRING = ""
        private const val DEFAULT_MAX_NUMBER_OF_PAGES = 10_000
        // Lowered default to ensure large merges (200+ pages) use batch processing
        // to avoid running out of memory during conversion.
        private const val DEFAULT_BATCH_SIZE = 200
        private const val PREF_MIHON_DIR = "mihon_directory"
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

    private val _compressOutputPdf = MutableStateFlow(false)
    val compressOutputPdf = _compressOutputPdf.asStateFlow()

    private val _autoNameWithChapters = MutableStateFlow(false)
    val autoNameWithChapters = _autoNameWithChapters.asStateFlow()

    private val _mihonDirectoryUri = MutableStateFlow<Uri?>(null)
    val mihonDirectoryUri = _mihonDirectoryUri.asStateFlow()

    private val _mihonMangaEntries = MutableStateFlow<List<MihonMangaEntry>>(emptyList())
    val mihonMangaEntries = _mihonMangaEntries.asStateFlow()

    private val _isLoadingMihonManga = MutableStateFlow(false)
    val isLoadingMihonManga = _isLoadingMihonManga.asStateFlow()

    private val _mihonLoadProgress = MutableStateFlow(0f)
    val mihonLoadProgress = _mihonLoadProgress.asStateFlow()

    private val cbzParentName = mutableMapOf<Uri, String>()

    init {
        contextHelper.getPreferences().getString(PREF_MIHON_DIR, null)?.let {
            _mihonDirectoryUri.value = Uri.parse(it)
            refreshMihonManga()
        }
    }

    // --------------------------- Mutators ----------------------------

    fun toggleOverrideSortOrderToUseOffset(newValue: Boolean) {
        _overrideSortOrderToUseOffset.update { newValue }
    }

    fun toggleMergeFilesOverride(newValue: Boolean) {
        _overrideMergeFiles.update { newValue }
    }

    fun toggleCompressOutputPdf(newValue: Boolean) {
        _compressOutputPdf.update { newValue }
    }

    fun toggleAutoNameWithChapters(newValue: Boolean) {
        _autoNameWithChapters.update { newValue }
    }

    fun updateMihonDirectoryUri(newUri: Uri) {
        _mihonDirectoryUri.update { newUri }

        // Persist read/write access so the directory remains available across app launches
        runCatching {
            contextHelper.getContext().contentResolver.takePersistableUriPermission(
                newUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { e ->
            logger.warning("Failed to persist Mihon directory permission: ${e.message}")
        }

        contextHelper.getPreferences().edit().putString(PREF_MIHON_DIR, newUri.toString()).apply()
        updateSelectedFileUrisFromUserInput(emptyList())
        refreshMihonManga()
    }

    fun updateMaxNumberOfPagesSizeFromUserInput(maxNumberOfPages: String) {
        val parsed = maxNumberOfPages.trim().toIntOrNull()
        if (parsed != null && parsed > 0) {
            _maxNumberOfPages.update { parsed }
            appendTask("Max pages: $parsed")
        } else {
            appendTask("Max pages invalid ($maxNumberOfPages). Using $DEFAULT_MAX_NUMBER_OF_PAGES")
            _maxNumberOfPages.update { DEFAULT_MAX_NUMBER_OF_PAGES }
        }
    }

    fun updateBatchSizeFromUserInput(batchSize: String) {
        val parsed = batchSize.trim().toIntOrNull()
        if (parsed != null && parsed > 0) {
            _batchSize.update { parsed }
            appendTask("Batch size: $parsed")
        } else {
            appendTask("Batch size invalid ($batchSize). Using $DEFAULT_BATCH_SIZE")
            _batchSize.update { DEFAULT_BATCH_SIZE }
        }
    }

    fun updateOverrideFileNameFromUserInput(newOverrideFileName: String) {
        if (newOverrideFileName.isBlank()) {
            _overrideFileName.update { EMPTY_STRING }
            appendTask("Output name: default")
        } else {
            _overrideFileName.update { newOverrideFileName.trim() }
            appendTask("Output name: ${_overrideFileName.value}")
        }
    }

    fun updateOverrideOutputPathFromUserInput(newOverrideOutputPath: Uri) {
        _overrideOutputDirectoryUri.update { newOverrideOutputPath }
        appendTask("Output folder: set")
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
            setTask("Selected ${newSelectedFileUris.size} file(s)")
            setSubTask("Ready to convert")
            if (!areSelectedFilesFromSameParent()) {
                _overrideMergeFiles.update { false }
            }
        } catch (_: Exception) {
            appendTask("File selection failed. Cleared")
            _selectedFileUri.update { emptyList() }
        }
    }

    fun toggleFileSelection(uri: Uri) {
        val current = _selectedFileUri.value.toMutableList()
        if (current.contains(uri)) current.remove(uri) else current.add(uri)
        updateSelectedFileUrisFromUserInput(current)
    }

    fun areSelectedFilesFromSameParent(): Boolean {
        val ctx = contextHelper.getContext()
        val selected = _selectedFileUri.value
        if (selected.size <= 1) return true
        val firstParent = DocumentFile.fromSingleUri(ctx, selected.first())?.parentFile?.uri
        return selected.all {
            DocumentFile.fromSingleUri(ctx, it)?.parentFile?.uri == firstParent
        }
    }

    /**
     * Reload the Mihon manga list from the previously selected directory. Exposed
     * publicly so the UI can trigger a refresh when the screen is revisited.
     */
    fun refreshMihonManga() {
        val rootUri = _mihonDirectoryUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMihonManga.value = true
            _mihonLoadProgress.value = 0f
            try {
                val ctx = contextHelper.getContext()
                val root = DocumentFile.fromTreeUri(ctx, rootUri) ?: return@launch
                val downloads = root.findFile("downloads") ?: return@launch

                val extensionDirs = downloads.listFiles().filter { it.isDirectory }
                val mangaDirs = extensionDirs.flatMap { ext ->
                    ext.listFiles().filter { it.isDirectory }
                }
                val total = mangaDirs.size.takeIf { it > 0 } ?: 1
                val result = mutableListOf<MihonMangaEntry>()
                cbzParentName.clear() // reset before rebuilding

                mangaDirs.forEachIndexed { index, manga ->
                    val mangaName = manga.name ?: "Unknown"
                    val cbzFiles = manga.listFiles()
                        .filter { !it.isDirectory && it.name?.endsWith(".cbz", true) == true }
                        .map { file ->
                            // remember parent (manga) name for this CBZ
                            cbzParentName[file.uri] = mangaName
                            MihonCbzFile(file.name ?: "Unknown", file.uri)
                        }

                    if (cbzFiles.isNotEmpty()) {
                        result.add(MihonMangaEntry(mangaName, cbzFiles))
                    }
                    _mihonLoadProgress.value = (index + 1) / total.toFloat()
                }
                _mihonMangaEntries.value = result.sortedBy { it.name.lowercase() }
            } finally {
                _isLoadingMihonManga.value = false
            }
        }
    }

    // ------------------------ Conversion Flow ------------------------

    fun convertToPDF(fileUris: List<Uri>, useParentDirectoryName: Boolean = false) {
        if (_overrideMergeFiles.value && !areSelectedFilesFromSameParent()) {
            appendTask("Merge disabled: files from different manga")
            contextHelper.showToast("Cannot merge files from different manga", Toast.LENGTH_LONG)
            _overrideMergeFiles.update { false }
        }

        if (_isCurrentlyConverting.value) {
            // Avoid double triggers
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            setConverting(true)

            try {
                val outputFolder = getOutputFolder()
                val pdfFileNames = resolveFileNameConflicts(
                    getPdfFileNames(fileUris, useParentDirectoryName),
                    outputFolder
                )

                setTask("Converting...")
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
                    compressOutputPdf = _compressOutputPdf.value,
                    outputDirectory = outputFolder
                )

                handlePdfResult(pdfFiles)
            } catch (e: Exception) {
                showToastAndTask(
                    message = "Failed: ${e.message ?: "Unknown error"}",
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
            throw IllegalStateException("No PDFs created")
        }

        val msg = if (pdfFiles.size == 1) {
            "Saved: ${pdfFiles.first().absolutePath}"
        } else {
            "Saved: ${pdfFiles.size} PDFs"
        }

        showToastAndTask(message = msg, toastLength = Toast.LENGTH_LONG)
        appendTask("Completed")
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
            appendTask("Selected: none")
            _selectedFileName.update { EMPTY_STRING }
        } else {
            _selectedFileName.update { newSelectedFileNames }
            // Clear override when new files are chosen
            updateOverrideFileNameFromUserInput(EMPTY_STRING)
            appendTask("Selected: updated")
        }
    }

    private fun extractChapterNumber(name: String): String? {
        val match = Regex("(\\d+(?:[.,]\\d+)?)(?!.*\\d)").find(name)
        return match?.value
    }

    private fun getPdfFileNames(filesUri: List<Uri>, useParentDirectoryName: Boolean): List<String> {
        val ctx = contextHelper.getContext()
        val baseNames = filesUri.map { it.getFileName() }
        val baseNamesNoExt = baseNames.map { it.substringBeforeLast('.', it) }

        // Resolve placeholders by falling back to parent directory names
        val adjustedBaseNamesNoExt = baseNamesNoExt.toMutableList()
        if (!useParentDirectoryName) {
            filesUri.forEachIndexed { index, uri ->
                val initialParent = DocumentFile.fromSingleUri(ctx, uri)?.parentFile?.name
                    ?: uri.pathSegments.dropLast(1).lastOrNull()

                val resolvedParent =
                    initialParent?.takeUnless { isPlaceholderName(it) }
                        ?: run {
                            val base = baseNamesNoExt[index]
                            if (!isPlaceholderName(base)) base else "Unknown"
                        }

                adjustedBaseNamesNoExt[index] = resolvedParent
            }

            return when {
                _overrideFileName.value.isNotBlank() && _overrideMergeFiles.value -> {
                    mutableListOf("${_overrideFileName.value}.pdf").apply {
                        if (filesUri.size > 1) {
                            addAll(adjustedBaseNamesNoExt.drop(1).map { "$it.pdf" })
                        }
                    }
                }
                _overrideFileName.value.isNotBlank() -> {
                    if (filesUri.size == 1) {
                        listOf("${_overrideFileName.value}.pdf")
                    } else {
                        List(filesUri.size) { index -> "${_overrideFileName.value}_${index + 1}.pdf" }
                    }
                }
                else -> {
                    adjustedBaseNamesNoExt.map { "$it.pdf" }
                }
            }
        }

        val mangaNames = filesUri.mapIndexed { index, uri ->
            // 1) Prefer the Mihon-sourced parent name if we have it
            cbzParentName[uri]
            // 2) Else try to resolve parent directory name from SAF
                ?: run {
                    val initialParent = DocumentFile.fromSingleUri(ctx, uri)?.parentFile?.name
                        ?: uri.pathSegments.dropLast(1).lastOrNull()
                    initialParent
                }
                // 3) Else fall back to base name (only if not placeholder), else "Unknown"
                ?: run {
                    val base = baseNamesNoExt[index]
                    if (!isPlaceholderName(base)) base else "Unknown"
                }
        }

        val chapters = if (_autoNameWithChapters.value) {
            baseNamesNoExt.map { extractChapterNumber(it) }
        } else {
            List(baseNamesNoExt.size) { null }
        }

        val defaultNames = filesUri.mapIndexed { index, _ ->
            val mangaName = mangaNames[index]
            val chapter = chapters[index]
            val suffix = when {
                chapter != null -> "_${chapter}"
                filesUri.size == 1 -> ""
                else -> "_${index + 1}"
            }
            "$mangaName$suffix.pdf"
        }.toMutableList().apply {
            if (_overrideMergeFiles.value && areSelectedFilesFromSameParent()) {
                val base = mangaNames.first()
                if (_autoNameWithChapters.value) {
                    val chapterPairs = chapters.mapIndexedNotNull { _, ch ->
                        val numeric = ch?.replace(',', '.')?.toDoubleOrNull()
                        if (numeric != null) numeric to ch else null
                    }
                    if (chapterPairs.isNotEmpty()) {
                        val minPair = chapterPairs.minByOrNull { it.first }!!
                        val maxPair = chapterPairs.maxByOrNull { it.first }!!
                        val rangeSuffix = if (minPair == maxPair) {
                            "_${minPair.second}"
                        } else {
                            "_${minPair.second}-${maxPair.second}"
                        }
                        this[0] = "$base$rangeSuffix.pdf"
                    } else {
                        this[0] = "$base.pdf"
                    }
                } else {
                    this[0] = "$base.pdf"
                }
            }
        }

        return when {
            _overrideFileName.value.isNotBlank() && _overrideMergeFiles.value -> {
                mutableListOf("${_overrideFileName.value}.pdf").apply {
                    if (filesUri.size > 1) addAll(defaultNames.drop(1))
                }
            }
            _overrideFileName.value.isNotBlank() -> {
                if (filesUri.size == 1) {
                    listOf("${_overrideFileName.value}.pdf")
                } else {
                    List(filesUri.size) { index -> "${_overrideFileName.value}_${index + 1}.pdf" }
                }
            }
            else -> defaultNames
        }
    }

    private fun isPlaceholderName(name: String): Boolean {
        var base = name.lowercase().substringBeforeLast('.')
        base = base
            .replace(Regex("\\s*\\(\\d+\\)$"), "")
            .replace(Regex("[-_\\s]*\\d+$"), "")
            .trim()
        val placeholders = listOf("unknown", "document", "file", "download", "content")
        return placeholders.contains(base)
    }

    private fun resolveFileNameConflicts(names: List<String>, outputFolder: File): List<String> {
        val existing = outputFolder.list()?.toMutableSet() ?: mutableSetOf()
        return names.map { base ->
            var candidate = base
            val dotIndex = candidate.lastIndexOf('.')
            val namePart = if (dotIndex != -1) candidate.substring(0, dotIndex) else candidate
            val extension = if (dotIndex != -1) candidate.substring(dotIndex) else ""
            var version = 1
            while (existing.contains(candidate)) {
                candidate = "$namePart $version$extension"
                version++
            }
            existing.add(candidate)
            candidate
        }
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