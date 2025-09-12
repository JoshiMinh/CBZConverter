package com.joshiminh.cbzconverter.backend

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.StringRes
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
        val base = name.lowercase()
            .substringBeforeLast('.')
            .replace(Regex("\\s*\\(\\d+\\)$"), "")
            .replace(Regex("[-_\\s]*\\d+$"), "")
            .trim()

        if (base.isBlank()) return false
        val placeholders = listOf("document", "file", "download", "content", "item", "untitled")
        return placeholders.none { base == it || base.startsWith("$it ") || base.startsWith("${it}_") || base.startsWith("$it-") }
    }

    @JvmOverloads
    fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(context, message, length).show()

    @JvmOverloads
    fun showToast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(context, resId, length).show()

    fun getOutputFolderUri(uri: Uri?): File? {
        if (uri == null) return null
        val doc = DocumentFileCompat.fromUri(context, uri) ?: return null
        val abs = doc.getAbsolutePath(context)
        return File(abs)
    }

    fun openInputStream(uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)?.buffered()

    fun getCacheDir(): File = context.cacheDir

    @Suppress("DEPRECATION")
    fun getExternalStoragePublicDirectory(type: String): File =
        Environment.getExternalStoragePublicDirectory(type)

    fun getPreferences(): SharedPreferences =
        context.getSharedPreferences("cbz_converter_prefs", Context.MODE_PRIVATE)

    fun getContext(): Context = context
}