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

class ContextHelper(private val context: Context) {

    /**
     * Best-effort display name for a [Uri].
     * - Tries ContentResolver (DISPLAY_NAME)
     * - Falls back to DocumentFileCompat
     * - Finally falls back to the last path segment
     */
    fun getFileName(uri: Uri): String {
        // Query content resolver when possible
        if (uri.scheme.equals("content", ignoreCase = true)) {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        cursor.getString(idx)?.let { return it }
                    }
                }
        }

        // Try DocumentFileCompat (works with SAF Uris, including tree/document)
        DocumentFileCompat.fromUri(context, uri)?.name?.let { return it }

        // Last-resort: last path segment
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
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