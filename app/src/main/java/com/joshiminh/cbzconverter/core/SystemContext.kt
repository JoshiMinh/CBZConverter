package com.joshiminh.cbzconverter.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.DocumentFileCompat
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class MihonCbzFile(val name: String, val uri: Uri)
data class MihonMangaEntry(val name: String, val files: List<MihonCbzFile>)
data class SelectedFileInfo(val displayName: String, val parentName: String?)

class ContextHelper(private val context: Context) {
    fun getFileName(uri: Uri): String {
        val candidates = buildList {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i != -1 && c.moveToFirst()) add(c.getString(i))
                    }
            }
            DocumentFileCompat.fromUri(context, uri)?.name?.let(::add)
            uri.path?.let(::add)
            add(uri.toString())
        }.mapNotNull { it?.let(::decodeUtf8) }
            .map(::stripNoise)
            .map(::lastPathishSegment)
            .map(::trimQueryAndFrag)
            .map(::trimQuotes)
            .map(String::trim)
            .filter(String::isNotBlank)

        return candidates.firstOrNull(::isMeaningful) ?: "Unknown"
    }

    private fun decodeUtf8(s: String): String =
        runCatching { URLDecoder.decode(s, StandardCharsets.UTF_8.name()) }.getOrDefault(s)

    private fun stripNoise(s: String): String = s.replace('\u0000', ' ').trim()

    private fun lastPathishSegment(s: String): String =
        s.substringAfterLast('/').substringAfterLast(':')

    private fun trimQueryAndFrag(s: String): String =
        s.substringBefore('?').substringBefore('#')

    private fun trimQuotes(s: String): String = s.trim().trim('"', '\'')

    private fun isMeaningful(name: String): Boolean {
        val base = name.lowercase().substringBeforeLast('.').trim()
        if (base.isBlank()) return false
        val placeholders = listOf("document", "file", "download", "content", "untitled")
        return placeholders.none { base == it || base.startsWith("$it ") || base.startsWith("${it}_") }
    }

    @JvmOverloads
    fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(context, message, length).show()

    @JvmOverloads
    fun showToast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(context, resId, length).show()

    fun getDocumentTree(uri: Uri?): DocumentFile? =
        uri?.let { DocumentFileCompat.fromUri(context, it) }

    fun getDefaultDownloadsTree(): DocumentFile? {
        val authority = "com.android.externalstorage.documents"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "primary:Download")
        val resolver = context.contentResolver
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        val hasPersistedGrant = resolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.isWritePermission &&
                    (permission.uri == treeUri || permission.uri == documentUri)
        }

        if (!hasPersistedGrant) return null

        return DocumentFileCompat.fromUri(context, documentUri)
            ?: DocumentFileCompat.fromUri(context, treeUri)
    }

    @Throws(IOException::class)
    fun createDocumentFile(parent: DocumentFile, fileName: String, mimeType: String): DocumentFile {
        parent.findFile(fileName)?.let { if (it.exists()) it.delete() }
        val uri = DocumentsContract.createDocument(context.contentResolver, parent.uri, mimeType, fileName)
            ?: throw IOException("Unable to create file: $fileName")
        return DocumentFile.fromSingleUri(context, uri)
            ?: throw IOException("Unable to resolve created file: $fileName")
    }

    fun openInputStream(uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)?.buffered()

    fun openOutputStream(uri: Uri, mode: String = "w") =
        context.contentResolver.openOutputStream(uri, mode)?.buffered()

    fun getCacheDir(): File = context.cacheDir
    fun getPreferences(): SharedPreferences =
        context.getSharedPreferences("cbz_converter_prefs", Context.MODE_PRIVATE)
    fun getContext(): Context = context
}