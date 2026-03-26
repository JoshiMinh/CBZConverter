package com.joshiminh.cbzconverter.core

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.logging.Logger

class MihonMangaRepository(private val contextHelper: ContextHelper) {
    private val logger = Logger.getLogger(MihonMangaRepository::class.java.name)
    private val PREF_MIHON_DIR = "mihon_directory"

    suspend fun refreshMihonManga(
        mihonDirectoryUri: Uri?,
        isLoadingMihonManga: MutableStateFlow<Boolean>,
        mihonLoadProgress: MutableStateFlow<Float>,
        mihonMangaEntries: MutableStateFlow<List<MihonMangaEntry>>,
        fileNameCache: MutableMap<Uri, String>,
        cbzParentName: MutableMap<Uri, String>,
        parentNameCache: MutableMap<Uri, String?>,
        parentUriCache: MutableMap<Uri, Uri?>
    ) = withContext(Dispatchers.IO) {
        val rootUri = mihonDirectoryUri ?: return@withContext
        if (isLoadingMihonManga.value) return@withContext

        isLoadingMihonManga.value = true
        mihonLoadProgress.value = 0f

        try {
            val root = DocumentFile.fromTreeUri(contextHelper.getContext(), rootUri) ?: return@withContext
            val downloads = root.findFile("downloads") ?: return@withContext

            val mangaDirs = downloads.listFiles().filter { it.isDirectory }
                .flatMap { it.listFiles().filter { it.isDirectory } }

            val total = mangaDirs.size.coerceAtLeast(1)
            val result = mutableListOf<MihonMangaEntry>()
            
            mangaDirs.forEachIndexed { index, manga ->
                val mangaName = manga.name ?: "Unknown"
                val cbzFiles = manga.listFiles()
                    .filter { !it.isDirectory && it.name?.endsWith(".cbz", true) == true }
                    .map { file ->
                        val name = file.name ?: "Unknown"
                        cbzParentName[file.uri] = mangaName
                        parentNameCache[file.uri] = mangaName
                        parentUriCache[file.uri] = manga.uri
                        fileNameCache[file.uri] = name
                        MihonCbzFile(name, file.uri)
                    }

                if (cbzFiles.isNotEmpty()) {
                    result.add(MihonMangaEntry(mangaName, cbzFiles))
                }
                mihonLoadProgress.value = (index + 1) / total.toFloat()
            }
            mihonMangaEntries.value = result.sortedBy { it.name.lowercase() }
        } finally {
            isLoadingMihonManga.value = false
        }
    }

    fun persistMihonPermission(uri: Uri) {
        runCatching {
            contextHelper.getContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { logger.warning("Failed to persist Mihon directory permission: ${it.message}") }
        contextHelper.getPreferences().edit().putString(PREF_MIHON_DIR, uri.toString()).apply()
    }
}