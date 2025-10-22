package com.joshiminh.cbzconverter.backend

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.LinkedHashSet
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
data class SelectedFileInfo(val displayName: String, val parentName: String?)

class MainViewModel(private val contextHelper: ContextHelper) : ViewModel() {

    class Factory(private val contextHelper: ContextHelper) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(contextHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val NOTHING_PROCESSING = "Idle"
        private const val NO_FILE_SELECTED = "No file"
        const val EMPTY_STRING = ""
        private const val DEFAULT_MAX_NUMBER_OF_PAGES = 1_000
        private const val DEFAULT_BATCH_SIZE = 200
        private const val PREF_MIHON_DIR = "mihon_directory"
        private const val PREF_EXPORT_DIR = "export_directory"
    }

    private val logger = Logger.getLogger(MainViewModel::class.java.name)

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

    private val _overrideMergeFiles = MutableStateFlow(false)
    val overrideMergeFiles = _overrideMergeFiles.asStateFlow()

    private val _selectedFileName = MutableStateFlow(NO_FILE_SELECTED)
    val selectedFileName = _selectedFileName.asStateFlow()

    private val _selectedFileUri = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFileUri = _selectedFileUri.asStateFlow()

    private val _canMergeSelection = MutableStateFlow(true)
    val canMergeSelection = _canMergeSelection.asStateFlow()

    private val _overrideFileName = MutableStateFlow(EMPTY_STRING)
    val overrideFileName = _overrideFileName.asStateFlow()

    private val _overrideOutputDirectoryUri = MutableStateFlow<Uri?>(null)
    val overrideOutputDirectoryUri = _overrideOutputDirectoryUri.asStateFlow()

    private val _hasWritableOutputDirectory = MutableStateFlow(false)
    val hasWritableOutputDirectory = _hasWritableOutputDirectory.asStateFlow()

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

    private val fileNameCache = mutableMapOf<Uri, String>()
    private val parentNameCache = mutableMapOf<Uri, String?>()
    private val parentUriCache = mutableMapOf<Uri, Uri?>()
    private val cbzParentName = mutableMapOf<Uri, String>()

    init {
        val preferences = contextHelper.getPreferences()
        preferences.getString(PREF_MIHON_DIR, null)?.let {
            _mihonDirectoryUri.value = Uri.parse(it)
        }
        preferences.getString(PREF_EXPORT_DIR, null)?.let {
            _overrideOutputDirectoryUri.value = Uri.parse(it)
        }
        refreshOutputDirectoryAvailability()
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
        _mihonMangaEntries.value = emptyList()
        _mihonLoadProgress.value = 0f
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
        val contentResolver = contextHelper.getContext().contentResolver
        val preferences = contextHelper.getPreferences()

        try {
            contentResolver.takePersistableUriPermission(
                newOverrideOutputPath,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            preferences.edit().putString(PREF_EXPORT_DIR, newOverrideOutputPath.toString()).apply()
            _overrideOutputDirectoryUri.update { newOverrideOutputPath }
            appendTask("Output folder: set")
        } catch (e: Exception) {
            logger.warning("Failed to persist output directory permission: ${e.message}")
            preferences.edit().remove(PREF_EXPORT_DIR).apply()
            _overrideOutputDirectoryUri.update { null }
            appendTask("Failed to save output folder. Using default downloads directory.")
        }

        refreshOutputDirectoryAvailability()
    }

    fun updateUpdateSelectedFileUriFromUserInput(newSelectedFileUris: List<Uri>) {
        // Backward-compatible entry point (kept for existing callers)
        updateSelectedFileUrisFromUserInput(newSelectedFileUris)
    }

    fun updateSelectedFileUrisFromUserInput(newSelectedFileUris: List<Uri>) {
        try {
            val ordered = LinkedHashSet(newSelectedFileUris).toList()

            _selectedFileUri.update { ordered }

            cacheMetadataFor(ordered)

            _canMergeSelection.value = haveSameParent(ordered)

            val names = ordered.joinToString(separator = "\n") { it.displayName() }

            updateSelectedFileNameFromUserInput(names)
            setTask("Selected ${ordered.size} file(s)")
            setSubTask("Ready to convert")
        } catch (_: Exception) {
            appendTask("File selection failed. Cleared")
            _selectedFileUri.update { emptyList() }
            _canMergeSelection.value = true
        }
    }

    fun setFileSelection(uri: Uri, isSelected: Boolean) {
        setFilesSelection(listOf(uri), isSelected)
    }

    fun setFilesSelection(uris: Collection<Uri>, isSelected: Boolean) {
        if (uris.isEmpty()) return

        val current = LinkedHashSet(_selectedFileUri.value)
        var changed = false

        if (isSelected) {
            uris.forEach { uri ->
                if (current.add(uri)) changed = true
            }
        } else {
            uris.forEach { uri ->
                if (current.remove(uri)) changed = true
            }
        }

        if (changed) {
            updateSelectedFileUrisFromUserInput(current.toList())
        }
    }

    fun toggleFileSelection(uri: Uri) {
        val isSelected = !_selectedFileUri.value.contains(uri)
        setFileSelection(uri, isSelected)
    }

    fun moveSelectedFile(fromIndex: Int, toIndex: Int) {
        val current = _selectedFileUri.value
        if (current.isEmpty() || fromIndex !in current.indices) return

        val destination = toIndex.coerceIn(0, current.lastIndex)
        if (fromIndex == destination) return

        val reordered = current.toMutableList().also { list ->
            val item = list.removeAt(fromIndex)
            list.add(destination, item)
        }

        updateSelectedFileUrisFromUserInput(reordered)
    }

    fun areSelectedFilesFromSameParent(): Boolean = _canMergeSelection.value

    fun getSelectedFileInfo(uri: Uri): SelectedFileInfo {
        ensureParentMetadata(uri)
        val name = uri.displayName()
        val parent = parentNameCache[uri] ?: cbzParentName[uri]
        return SelectedFileInfo(name, parent)
    }

    private fun haveSameParent(uris: List<Uri>): Boolean {
        if (uris.size <= 1) return true

        val expectedName = cbzParentName[uris.first()]
        if (expectedName != null) {
            val mismatch = uris.any { uri ->
                val name = cbzParentName[uri] ?: run {
                    ensureParentMetadata(uri)
                    cbzParentName[uri]
                }
                name != null && name != expectedName
            }
            if (mismatch) return false
            val unknownCount = uris.count { cbzParentName[it] == null }
            if (unknownCount == 0) {
                return true
            }
        }

        val expectedParent = resolveParentUri(uris.first())
        return uris.all { uri ->
            val name = cbzParentName[uri]
            if (expectedName != null && name != null) {
                name == expectedName
            } else {
                resolveParentUri(uri) == expectedParent
            }
        }
    }

    private fun resolveParentUri(uri: Uri): Uri? {
        if (!parentUriCache.containsKey(uri)) {
            ensureParentMetadata(uri)
        }
        return parentUriCache[uri]
    }

    private fun ensureParentMetadata(uri: Uri) {
        val needsParent = !parentUriCache.containsKey(uri) || !parentNameCache.containsKey(uri) || !cbzParentName.containsKey(uri)
        val needsName = !fileNameCache.containsKey(uri)
        if (!needsParent && !needsName) return

        val document = DocumentFile.fromSingleUri(contextHelper.getContext(), uri)

        if (needsName) {
            val displayName = document?.name ?: contextHelper.getFileName(uri)
            fileNameCache[uri] = displayName
        }

        if (needsParent) {
            val parent = document?.parentFile
            val parentUri = parent?.uri
            val parentName = parent?.name
            parentUriCache[uri] = parentUri
            parentNameCache[uri] = parentName
            if (parentName != null) {
                cbzParentName.putIfAbsent(uri, parentName)
            }
        }
    }

    private fun cacheMetadataFor(uris: Collection<Uri>) {
        uris.forEach { uri -> ensureParentMetadata(uri) }
    }

    /**
     * Reload the Mihon manga list from the previously selected directory. Exposed
     * publicly so the UI can trigger a refresh when the screen is revisited.
     */
    fun refreshMihonManga() {
        val rootUri = _mihonDirectoryUri.value ?: return
        if (_isLoadingMihonManga.value) return

        _isLoadingMihonManga.value = true
        _mihonLoadProgress.value = 0f

        viewModelScope.launch(Dispatchers.IO) {
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
                cbzParentName.clear()
                parentNameCache.clear()
                parentUriCache.clear()

                mangaDirs.forEachIndexed { index, manga ->
                    val mangaName = manga.name ?: "Unknown"
                    val cbzFiles = manga.listFiles()
                        .filter { !it.isDirectory && it.name?.endsWith(".cbz", true) == true }
                        .map { file ->
                            val displayName = file.name ?: "Unknown"
                            cbzParentName[file.uri] = mangaName
                            parentNameCache[file.uri] = mangaName
                            parentUriCache[file.uri] = manga.uri
                            fileNameCache[file.uri] = displayName
                            MihonCbzFile(displayName, file.uri)
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

    fun convertToPDF(fileUris: List<Uri>, useParentDirectoryName: Boolean = false) {
        if (_isCurrentlyConverting.value) {
            return
        }

        refreshOutputDirectoryAvailability()

        val outputFolder = getOutputFolder()
        if (outputFolder == null) {
            viewModelScope.launch {
                showToastAndTask(
                    message = "Select an output directory before exporting.",
                    toastLength = Toast.LENGTH_LONG,
                    loggerLevel = Level.WARNING
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            setConverting(true)

            try {
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
                        viewModelScope.launch(Dispatchers.Main) {
                            appendSubTask(message)
                        }
                    },
                    maxNumberOfPages = _maxNumberOfPages.value,
                    batchSize = _batchSize.value,
                    outputFileNames = pdfFileNames,
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

    private suspend fun handlePdfResult(pdfFiles: List<DocumentFile>) {
        if (pdfFiles.isEmpty()) {
            throw IllegalStateException("No PDFs created")
        }

        val msg = if (pdfFiles.size == 1) {
            val saved = pdfFiles.first()
            "Saved: ${saved.name ?: saved.uri}"
        } else {
            "Saved: ${pdfFiles.size} PDFs"
        }

        showToastAndTask(message = msg, toastLength = Toast.LENGTH_LONG)
        appendTask("Completed")
    }

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
        val baseNames = filesUri.map { it.displayName() }
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
            if (_overrideMergeFiles.value && filesUri.isNotEmpty()) {
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

    private fun resolveFileNameConflicts(names: List<String>, outputFolder: DocumentFile): List<String> {
        val existing = outputFolder.listFiles()
            .mapNotNull { it.name }
            .toMutableSet()
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

    private fun getOutputFolder(): DocumentFile? {
        overrideOutputDirectoryUri.value?.let { uri ->
            contextHelper.getDocumentTree(uri)?.let { directory ->
                if (directory.isWritableDirectory()) {
                    return directory
                }
            }
        }

        val downloads = contextHelper.getDefaultDownloadsTree()
        if (downloads.isWritableDirectory()) {
            return downloads
        }

        return null
    }

    private fun refreshOutputDirectoryAvailability() {
        val overrideAvailable = overrideOutputDirectoryUri.value?.let { uri ->
            contextHelper.getDocumentTree(uri).isWritableDirectory()
        } ?: false

        val defaultAvailable = contextHelper.getDefaultDownloadsTree().isWritableDirectory()

        _hasWritableOutputDirectory.value = overrideAvailable || defaultAvailable
    }

    private fun DocumentFile?.isWritableDirectory(): Boolean =
        this?.let { it.exists() && it.isDirectory && it.canWrite() } ?: false

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

    private fun Uri.displayName(): String =
        fileNameCache[this] ?: contextHelper.getFileName(this).also { resolved ->
            fileNameCache[this] = resolved
        }
}