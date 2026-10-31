package com.rootdrop.share.server

/**
 * One file the user picked for sharing.
 *
 * The URI is kept as a plain String on purpose: the ViewModel passes the
 * strings it gets from the system picker straight through, with no
 * conversion step. The server resolves each URI through ContentResolver
 * at request time and streams bytes, so it never copies a file into
 * app storage.
 *
 * @property uri content URI string from the system picker, for example
 *   a content:// URI. Never a filesystem path the server invents.
 * @property displayName file name shown on the index page and used as
 *   the download file name.
 * @property sizeBytes size in bytes as reported by the picker, or 0
 *   when unknown. A value of 0 means the server omits Content-Length
 *   and lets the transfer use chunked encoding.
 */
data class SharedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
)
