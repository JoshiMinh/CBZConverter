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

    /**
     * Best-effort display name for a [Uri].
     * Priority:
     * 1) ContentResolver DISPLAY_NAME (if not a generic placeholder)
     * 2) DocumentFileCompat.name (if not generic)
     * 3) Decode from path segment
     * 4) Decode from full Uri string (handles some SAF providers returning "document")
     * 5) "Unknown"
     */
    fun getFileName(uri: Uri): String {
        // 1) Query content resolver when possible
        if (uri.scheme.equals("content", ignoreCase = true)) {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        cursor.getString(idx)?.let { name ->
                            val cleaned = cleanCandidate(name)
                            if (isMeaningful(cleaned)) return cleaned
                        }
                    }
                }
        }

        // 2) Try DocumentFileCompat (works with SAF Uris, including tree/document)
        DocumentFileCompat.fromUri(context, uri)?.name?.let { dfName ->
            val cleaned = cleanCandidate(dfName)
            if (isMeaningful(cleaned)) return cleaned
        }

        // 3) Infer from raw path (decode and strip directory/colon prefixes)
        uri.path?.let { rawPath ->
            val decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
            val guess = cleanCandidate(
                decoded.substringAfterLast('/')
                    .substringAfterLast(':')
            )
            if (isMeaningful(guess)) return guess
        }

        // 4) Last-chance: parse from the FULL uri string (handles ?query, #fragment)
        uri.toString().let { full ->
            val decoded = URLDecoder.decode(full, StandardCharsets.UTF_8.name())
            val guess = cleanCandidate(
                decoded.substringAfterLast('/')
                    .substringAfterLast(':')
                    .substringBefore('?')
                    .substringBefore('#')
            )
            if (isMeaningful(guess)) return guess
        }

        // 5) Give up
        return "Unknown"
    }

    /** Remove whitespace, trim quotes, and normalize suspicious placeholders. */
    private fun cleanCandidate(name: String?): String {
        if (name == null) return ""
        // Strip surrounding quotes and trim spaces
        var s = name.trim().trim('"', '\'').trim()
        // Some providers expose mime-like or generic names without extension; keep as-is,
        // ViewModel will add .pdf later. Just normalize "document" variants to plain "document".
        if (s.contains(File.separator)) s = s.substringAfterLast(File.separator)
        return s
    }

    /**
     * True if the candidate looks like a real filename, not a generic placeholder.
     * The base name is derived by removing the extension and any trailing numeric
     * suffixes such as "-1" or "(1)" before comparison.
     */
    private fun isMeaningful(name: String): Boolean {
        if (name.isBlank()) return false

        // Normalize by stripping extension and trailing numeric suffixes
        var base = name.lowercase().substringBeforeLast('.')
        base = base
            .replace(Regex("\\s*\\(\\d+\\)$"), "") // "document(1)" -> "document"
            .replace(Regex("[-_\\s]*\\d+$"), "")        // "document-1" -> "document"
            .trim()

        val placeholders = listOf("document", "file", "download", "content")
        if (placeholders.any { base.startsWith(it) }) return false

        return true
    }

    /** Show a toast with a default SHORT length. */
    @JvmOverloads
    fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, length).show()
    }

    /** Overload to toast a string resource. */
    @JvmOverloads
    fun showToast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, resId, length).show()
    }

    /**
     * Resolve a SAF folder [Uri] to a filesystem [File], if possible.
     * Returns null when path mapping is not available on this device/uri.
     */
    fun getOutputFolderUri(uri: Uri?): File? {
        if (uri == null) return null
        val doc = DocumentFileCompat.fromUri(context, uri) ?: return null
        val abs = doc.getAbsolutePath(context) ?: return null
        return File(abs)
    }

    /** Open a buffered input stream for the given [Uri]. */
    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)?.buffered()
    }

    /** App cache directory. */
    fun getCacheDir(): File = context.cacheDir

    /**
     * Public external storage directory for the given type (e.g., [Environment.DIRECTORY_DOCUMENTS]).
     * NOTE: Deprecated API; prefer the Storage Access Framework (SAF) or MediaStore when targeting modern Android.
     */
    @Suppress("DEPRECATION")
    fun getExternalStoragePublicDirectory(type: String): File {
        return Environment.getExternalStoragePublicDirectory(type)
    }

    /** Persisted app preferences. */
    fun getPreferences(): SharedPreferences =
        context.getSharedPreferences("cbz_converter_prefs", Context.MODE_PRIVATE)

    /** Expose application context when needed for advanced operations. */
    fun getContext(): Context = context
}