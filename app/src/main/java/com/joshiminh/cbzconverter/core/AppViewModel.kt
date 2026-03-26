package com.joshiminh.cbzconverter.core

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashSet
import java.util.logging.Level
import java.util.logging.Logger

class MainViewModel(private val contextHelper: ContextHelper) : ViewModel() {
    class Factory(private val contextHelper: ContextHelper) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(contextHelper) as T
    }

    companion object {
        private const val IDLE = "Idle"
        private const val NO_FILE = "No file"
        private const val PREF_MIHON_DIR = "mihon_directory"
        private const val PREF_EXPORT_DIR = "export_directory"
    }

    private val logger = Logger.getLogger(MainViewModel::class.java.name)
    private val repository = MihonMangaRepository(contextHelper)

    private val _isCurrentlyConverting = MutableStateFlow(false)
    val isCurrentlyConverting = _isCurrentlyConverting.asStateFlow()

    private val _currentTaskStatus = MutableStateFlow(IDLE)
    val currentTaskStatus = _currentTaskStatus.asStateFlow()

    private val _currentSubTaskStatus = MutableStateFlow(IDLE)
    val currentSubTaskStatus = _currentSubTaskStatus.asStateFlow()

    private val _maxNumberOfPages = MutableStateFlow(1_000)
    val maxNumberOfPages = _maxNumberOfPages.asStateFlow()

    private val _batchSize = MutableStateFlow(200)
    val batchSize = _batchSize.asStateFlow()

    private val _overrideMergeFiles = MutableStateFlow(false)
    val overrideMergeFiles = _overrideMergeFiles.asStateFlow()

    private val _selectedFileName = MutableStateFlow(NO_FILE)
    val selectedFileName = _selectedFileName.asStateFlow()

    private val _selectedFileUri = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFileUri = _selectedFileUri.asStateFlow()

    private val _canMergeSelection = MutableStateFlow(true)
    val canMergeSelection = _canMergeSelection.asStateFlow()

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

    private val namingStrategy = PdfNamingStrategy(
        contextHelper.getContext(), fileNameCache, cbzParentName, contextHelper
    )

    init {
        val preferences = contextHelper.getPreferences()
        preferences.getString(PREF_MIHON_DIR, null)?.let { _mihonDirectoryUri.value = Uri.parse(it) }
        preferences.getString(PREF_EXPORT_DIR, null)?.let { _overrideOutputDirectoryUri.value = Uri.parse(it) }
            ?: run { _overrideOutputDirectoryUri.value = contextHelper.getDefaultDownloadsTree()?.uri }
        refreshOutputDirectoryAvailability()
    }

    fun toggleMergeFilesOverride(v: Boolean) { _overrideMergeFiles.update { v } }
    fun toggleCompressOutputPdf(v: Boolean) { _compressOutputPdf.update { v } }
    fun toggleAutoNameWithChapters(v: Boolean) { _autoNameWithChapters.update { v } }

    fun updateMihonDirectoryUri(newUri: Uri) {
        _mihonDirectoryUri.update { newUri }
        repository.persistMihonPermission(newUri)
        _mihonMangaEntries.value = emptyList()
        _mihonLoadProgress.value = 0f
        updateSelectedFileUrisFromUserInput(emptyList())
        refreshMihonManga()
    }

    fun updateMaxNumberOfPagesSizeFromUserInput(max: String) {
        val p = max.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1_000
        _maxNumberOfPages.update { p }
        appendTask("Max pages: $p")
    }

    fun updateBatchSizeFromUserInput(size: String) {
        val s = size.trim().toIntOrNull()?.coerceAtLeast(1) ?: 200
        _batchSize.update { s }
        appendTask("Batch size: $s")
    }

    fun updateOverrideOutputPathFromUserInput(uri: Uri) {
        try {
            contextHelper.getContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            contextHelper.getPreferences().edit().putString(PREF_EXPORT_DIR, uri.toString()).apply()
            _overrideOutputDirectoryUri.update { uri }
            appendTask("Output folder: set")
        } catch (e: Exception) {
            _overrideOutputDirectoryUri.update { null }
            appendTask("Failed to save output folder.")
        }
        refreshOutputDirectoryAvailability()
    }

    fun updateSelectedFileUrisFromUserInput(uris: List<Uri>) {
        try {
            val ordered = LinkedHashSet(uris).toList()
            _selectedFileUri.update { ordered }
            ordered.forEach { ensureParentMetadata(it) }
            _canMergeSelection.value = haveSameParent(ordered)
            _selectedFileName.update { ordered.joinToString("\n") { it.displayName() } }
            setTask("Selected ${ordered.size} file(s)")
            setSubTask("Ready to convert")
        } catch (_: Exception) {
            _selectedFileUri.update { emptyList() }
            _canMergeSelection.value = true
        }
    }

    fun setFileSelection(uri: Uri, sel: Boolean) = setFilesSelection(listOf(uri), sel)

    fun setFilesSelection(uris: Collection<Uri>, sel: Boolean) {
        val current = LinkedHashSet(_selectedFileUri.value)
        val changed = if (sel) current.addAll(uris) else current.removeAll(uris)
        if (changed) updateSelectedFileUrisFromUserInput(current.toList())
    }

    fun moveSelectedFile(from: Int, to: Int) {
        val current = _selectedFileUri.value
        if (from !in current.indices) return
        val list = current.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        updateSelectedFileUrisFromUserInput(list)
    }

    fun getSelectedFileInfo(uri: Uri): SelectedFileInfo {
        ensureParentMetadata(uri)
        return SelectedFileInfo(uri.displayName(), parentNameCache[uri] ?: cbzParentName[uri])
    }

    private fun haveSameParent(uris: List<Uri>): Boolean {
        if (uris.size <= 1) return true
        val expected = cbzParentName[uris.first()] ?: run { ensureParentMetadata(uris.first()); cbzParentName[uris.first()] }
        return uris.all { cbzParentName[it] == expected }
    }

    private fun ensureParentMetadata(uri: Uri) {
        val doc = DocumentFile.fromSingleUri(contextHelper.getContext(), uri)
        if (!fileNameCache.containsKey(uri)) fileNameCache[uri] = doc?.name ?: contextHelper.getFileName(uri)
        if (!parentUriCache.containsKey(uri)) {
            val parent = doc?.parentFile
            parentUriCache[uri] = parent?.uri
            parentNameCache[uri] = parent?.name
            parent?.name?.let { cbzParentName.putIfAbsent(uri, it) }
        }
    }

    fun refreshMihonManga() {
        viewModelScope.launch {
            repository.refreshMihonManga(
                _mihonDirectoryUri.value, _isLoadingMihonManga, _mihonLoadProgress,
                _mihonMangaEntries, fileNameCache, cbzParentName, parentNameCache, parentUriCache
            )
        }
    }

    fun convertToPDF(fileUris: List<Uri>, useParent: Boolean = false) {
        if (_isCurrentlyConverting.value) return
        refreshOutputDirectoryAvailability()
        val folder = getOutputFolder() ?: run {
            viewModelScope.launch { showToastAndTask("Select an output directory.", Toast.LENGTH_LONG, Level.WARNING) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isCurrentlyConverting.update { true }
            try {
                val names = namingStrategy.getPdfFileNames(
                    fileUris, useParent, "", _overrideMergeFiles.value, _autoNameWithChapters.value
                )
                val resolvedNames = namingStrategy.resolveFileNameConflicts(names, folder)
                setTask("Converting...")
                setSubTask("")
                val pdfs = convertCbzToPdf(
                    fileUris, contextHelper, { viewModelScope.launch(Dispatchers.Main) { appendSubTask(it) } },
                    _maxNumberOfPages.value, _batchSize.value, resolvedNames,
                    _overrideMergeFiles.value, _compressOutputPdf.value, folder
                )
                handleResult(pdfs)
            } catch (e: Exception) {
                showToastAndTask("Failed: ${e.message}", Toast.LENGTH_LONG, Level.WARNING)
            } finally { _isCurrentlyConverting.update { false } }
        }
    }

    private suspend fun handleResult(pdfs: List<DocumentFile>) {
        val msg = if (pdfs.size == 1) "Saved: ${pdfs.first().name}" else "Saved: ${pdfs.size} PDFs"
        showToastAndTask(msg, Toast.LENGTH_LONG)
        appendTask("Completed")
    }

    fun checkPermissionAndSelectFileAction(a: ComponentActivity, l: ManagedActivityResultLauncher<Array<String>, List<Uri>>) =
        PermissionsManager.checkPermissionAndSelectFileAction(a, l)

    fun checkPermissionAndSelectDirectoryAction(a: ComponentActivity, l: ManagedActivityResultLauncher<Uri?, Uri?>) =
        PermissionsManager.checkPermissionAndSelectDirectoryAction(a, l)

    private fun setTask(m: String) = _currentTaskStatus.update { m }
    private fun appendTask(m: String) = _currentTaskStatus.update { "$m\n$it" }
    private fun setSubTask(m: String) = _currentSubTaskStatus.update { m }
    private suspend fun appendSubTask(m: String) = withContext(Dispatchers.Main) {
        _currentSubTaskStatus.update { "$m\n$it" }; logger.info(m)
    }

    private fun getOutputFolder(): DocumentFile? {
        val uri = _overrideOutputDirectoryUri.value
        uri?.let { contextHelper.getDocumentTree(it)?.let { if (it.isWritableDir()) return it } }
        val downloads = contextHelper.getDefaultDownloadsTree()
        return if (downloads.isWritableDir()) downloads else null
    }

    private fun refreshOutputDirectoryAvailability() {
        val override = _overrideOutputDirectoryUri.value?.let { contextHelper.getDocumentTree(it).isWritableDir() } ?: false
        val default = contextHelper.getDefaultDownloadsTree().isWritableDir()
        _hasWritableOutputDirectory.value = override || default
    }

    private fun DocumentFile?.isWritableDir(): Boolean = this?.let { it.exists() && it.isDirectory && it.canWrite() } ?: false

    private suspend fun showToastAndTask(m: String, l: Int, lvl: Level = Level.INFO) = withContext(Dispatchers.Main) {
        appendTask(m); logger.log(lvl, m); contextHelper.showToast(m, l)
    }

    private fun Uri.displayName(): String = fileNameCache[this] ?: contextHelper.getFileName(this).also { fileNameCache[this] = it }
}