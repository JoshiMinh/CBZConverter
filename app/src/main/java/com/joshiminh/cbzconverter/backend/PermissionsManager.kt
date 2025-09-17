package com.joshiminh.cbzconverter.backend

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralizes permission checks and launchers for file/directory selection.
 *
 * Behavior:
 * - On Android R+ (API 30+), launches Storage Access Framework pickers directly and
 *   surfaces a toast reminding users to grant "All files" access if saving to
 *   public folders fails.
 * - On legacy devices, it requests READ/WRITE_EXTERNAL_STORAGE as needed.
 */
object PermissionsManager {

    private const val STORAGE_PERMISSION_CODE = 1001
    private val FILE_MIME_TYPES = arrayOf(
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/zip",
        "application/octet-stream"
    )

    /** Request array for legacy (pre-R) external storage permissions. */
    private val LEGACY_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // ----------------------------- Public API -----------------------------

    /**
     * Ensures storage permission (per OS version) and, if granted, launches a **file** picker.
     */
    fun checkPermissionAndSelectFileAction(
        activity: ComponentActivity,
        filePickerLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>
    ) {
        if (isRorAbove()) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    activity,
                    "Grant \"All files access\" from settings if saving to Downloads fails.",
                    Toast.LENGTH_LONG
                ).show()
            }
            launchFilePicker(filePickerLauncher)
        } else {
            if (hasLegacyStoragePermissions(activity)) {
                launchFilePicker(filePickerLauncher)
            } else {
                requestLegacyStoragePermissions(activity)
            }
        }
    }

    /**
     * Ensures storage permission (per OS version) and, if granted, launches a **directory** picker.
     *
     * @param initialDirectory Optional starting location for the tree picker (may be null).
     *                         Passing `null` is widely compatible and recommended.
     */
    fun checkPermissionAndSelectDirectoryAction(
        activity: ComponentActivity,
        directoryPickerLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
        initialDirectory: Uri? = null
    ) {
        if (isRorAbove()) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    activity,
                    "Grant \"All files access\" from settings if writing to Downloads fails.",
                    Toast.LENGTH_LONG
                ).show()
            }
            directoryPickerLauncher.launch(initialDirectory)
        } else {
            if (hasLegacyStoragePermissions(activity)) {
                directoryPickerLauncher.launch(initialDirectory)
            } else {
                requestLegacyStoragePermissions(activity)
            }
        }
    }

    private fun launchFilePicker(
        filePickerLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>
    ) {
        runCatching {
            filePickerLauncher.launch(FILE_MIME_TYPES)
        }.onFailure {
            filePickerLauncher.launch(arrayOf("*/*"))
        }
    }

    // ---------------------------- Internals ------------------------------

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun isRorAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun hasLegacyStoragePermissions(activity: ComponentActivity): Boolean {
        val writeGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val readGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        return writeGranted && readGranted
    }

    private fun requestLegacyStoragePermissions(activity: ComponentActivity) {
        ActivityCompat.requestPermissions(
            activity,
            LEGACY_PERMISSIONS,
            STORAGE_PERMISSION_CODE
        )
    }

}