package com.rootdrop.share.picker

import androidx.compose.runtime.Immutable

/**
 * One file chosen through the system picker.
 *
 * The uri field holds the string form of the content Uri, matching the
 * shape the server boundary expects (uri, displayName, sizeBytes), so the
 * screen can pass picked files on without re-resolving them.
 */
@Immutable
data class PickedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
)
