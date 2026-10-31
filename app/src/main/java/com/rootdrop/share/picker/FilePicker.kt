package com.rootdrop.share.picker

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Registers the SYSTEM picker for multiple documents of any MIME type and
 * returns a launch function the Compose screen can call, for example from a
 * button click. No custom file picker UI is built here.
 *
 * Each picked Uri is resolved to a [PickedFile] (display name plus size via
 * [OpenableColumns]) and its read permission is persisted where the system
 * grants a persistable permission, so the local server can still read the
 * file while sharing is active.
 */
@Composable
fun rememberFilePickerLauncher(
    onPicked: (List<PickedFile>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            val files: List<PickedFile> = uris.map { uri: Uri ->
                persistReadPermission(contentResolver = context.contentResolver, uri = uri)
                resolvePickedFile(contentResolver = context.contentResolver, uri = uri)
            }
            onPicked(files)
        },
    )
    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * Resolves display name and size for one picked Uri via [OpenableColumns].
 * A missing name falls back to the last path segment, and an unknown or
 * negative size is reported as 0.
 */
fun resolvePickedFile(
    contentResolver: ContentResolver,
    uri: Uri,
): PickedFile {
    val fallbackName: String = uri.lastPathSegment ?: uri.toString()
    val projection: Array<String> = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE,
    )
    val resolved: PickedFile? = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            val displayName: String = if (nameIndex >= 0) {
                cursor.getString(nameIndex) ?: fallbackName
            } else {
                fallbackName
            }
            val sizeBytes: Long = if (sizeIndex >= 0) {
                val rawSize: Long = cursor.getLong(sizeIndex)
                if (rawSize >= 0L) rawSize else 0L
            } else {
                0L
            }
            PickedFile(uri = uri.toString(), displayName = displayName, sizeBytes = sizeBytes)
        } else {
            null
        }
    }
    return resolved ?: PickedFile(uri = uri.toString(), displayName = fallbackName, sizeBytes = 0L)
}

private fun persistReadPermission(
    contentResolver: ContentResolver,
    uri: Uri,
) {
    try {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        // The system picker did not grant a persistable permission for this
        // Uri. The Uri remains usable while the picker's grant is alive, so
        // there is nothing else to do here.
    }
}
