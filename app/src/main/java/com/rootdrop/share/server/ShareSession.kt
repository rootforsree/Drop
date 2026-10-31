package com.rootdrop.share.server

/**
 * A running share.
 *
 * @property baseUrl full LAN URL the receiving phone opens, including
 *   the trailing slash, for example http://192.168.0.42:8080/.
 *   The port in this URL is the port the server actually bound, which
 *   is 8080 unless 8080 was busy, in which case it is the ephemeral
 *   port the server fell back to.
 */
data class ShareSession(
    val baseUrl: String,
)
