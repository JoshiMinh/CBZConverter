package com.joshiminh.cbzconverter.backend

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Centralizes permission checks and launchers for file/directory selection.
 *
 * Behavior:
 * - On Android R+ (API 30+), this follows your original logic:
 *   requires MANAGE_EXTERNAL_STORAGE (All Files Access) before launching pickers.
 * - On legacy devices, it requests READ/WRITE_EXTERNAL_STORAGE as needed.
 */
object PermissionsManager {

    private const val STORAGE_PERMISSION_CODE = 1001

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
                openAllFilesAccessSettings(activity)
            } else {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        } else {
            if (hasLegacyStoragePermissions(activity)) {
                filePickerLauncher.launch(arrayOf("*/*"))
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
                openAllFilesAccessSettings(activity)
            } else {
                directoryPickerLauncher.launch(initialDirectory)
            }
        } else {
            if (hasLegacyStoragePermissions(activity)) {
                directoryPickerLauncher.launch(initialDirectory)
            } else {
                requestLegacyStoragePermissions(activity)
            }
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

    /**
     * Opens the per-app "All Files Access" settings on Android R+.
     * Falls back to the generic settings if the per-app one isn’t available.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun openAllFilesAccessSettings(activity: ComponentActivity) {
        val packageUri = "package:${activity.packageName}".toUri()

        // Prefer the per-app page
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = packageUri
        }

        // Fallback to global page if needed
        val globalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        try {
            ContextCompat.startActivity(activity, appIntent, null)
        } catch (_: Exception) {
            ContextCompat.startActivity(activity, globalIntent, null)
        }
    }
}