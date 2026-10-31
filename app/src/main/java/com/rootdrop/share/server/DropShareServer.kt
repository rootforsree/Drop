package com.rootdrop.share.server

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.InputStream
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local HTTP server that serves exactly the files passed to [start],
 * while sharing is active, and nothing else.
 *
 * Shape chosen: one instance constructed by MainActivity and handed to
 * the ViewModel, so the ViewModel only holds this class and calls
 * [start] / [stop]. No other package needs to construct it.
 *
 * Boundary (this is the whole contract with the UI package):
 * - suspend fun start(files: List<SharedFile>): ShareSession
 * - suspend fun stop()
 * - val isRunning: StateFlow<Boolean>
 *
 * Error reporting: [start] throws when it cannot run (empty file list,
 * no LAN address, or no port it can bind), and the ViewModel maps that
 * exception to its error state. [stop] never throws: stopping an
 * already-stopped server is a no-op. [isRunning] reflects the server
 * lifecycle only, not network reachability.
 *
 * WiFi behaviour: the server binds 0.0.0.0, so a WiFi drop does not by
 * itself kill the listener or crash the app, and the server keeps
 * running. If WiFi comes back on the same network the same URL works
 * again; if it comes back with a different address, the ViewModel
 * should call [start] again with the same file list to get a fresh
 * [ShareSession.baseUrl]. There is no engine callback for interface
 * loss, so [isRunning] stays true across a drop; it is not a claim
 * that the URL is currently reachable.
 *
 * Serving rules: GET / is the index page, GET /files/{index} serves
 * one of the started files as an attachment, GET /all.zip streams a
 * zip built on the fly. Every other path gets Ktor's default 404.
 * Files are opened through ContentResolver at request time and their
 * bytes are streamed to the response; no file is ever copied wholesale
 * into app storage, and no path outside the started list is served,
 * because the only selector is an integer index into that list.
 */
class DropShareServer(
    context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val lifecycleMutex = Mutex()

    private val isRunningFlow = MutableStateFlow(false)

    /** True from a successful [start] until [stop]. See the class notes. */
    val isRunning: StateFlow<Boolean> = isRunningFlow.asStateFlow()

    // Mutable because start() replaces the running server instance on
    // restart and stop() clears it. All access is serialized by
    // lifecycleMutex, so no two lifecycle changes can interleave.
    private var currentServer: EmbeddedServer<*, *>? = null

    /**
     * Starts (or restarts) sharing [files] and returns the LAN URL.
     * Binds 0.0.0.0, port 8080 preferred; when 8080 is busy it falls
     * back to an ephemeral port and reports the actual port.
     *
     * @throws IllegalArgumentException when [files] is empty.
     * @throws IllegalStateException when no LAN address exists or when
     *   no usable port can be determined after binding.
     * @throws Exception when binding fails on both 8080 and an
     *   ephemeral port; the ephemeral-port bind exception propagates.
     */
    suspend fun start(files: List<SharedFile>): ShareSession =
        lifecycleMutex.withLock {
            if (files.isEmpty()) {
                throw IllegalArgumentException("Cannot start sharing with no files.")
            }
            stopLocked()
            val snapshot: List<SharedFile> = files.toList()
            val lanIp: String = findLanIpAddress()
                ?: throw IllegalStateException(
                    "No LAN address available. Connect to WiFi and try again.",
                )
            val started: Pair<EmbeddedServer<*, *>, Int> = try {
                startOnPort(port = PREFERRED_PORT, snapshot = snapshot)
            } catch (e: Exception) {
                startOnPort(port = EPHEMERAL_PORT, snapshot = snapshot)
            }
            currentServer = started.first
            isRunningFlow.value = true
            ShareSession(baseUrl = "http://$lanIp:${started.second}/")
        }

    /** Stops the server cleanly and releases the port. Never throws. */
    suspend fun stop() {
        lifecycleMutex.withLock {
            stopLocked()
        }
    }

    private fun stopLocked() {
        val server: EmbeddedServer<*, *>? = currentServer
        currentServer = null
        isRunningFlow.value = false
        if (server != null) {
            runCatching { server.stop(GRACE_PERIOD_MILLIS, TIMEOUT_MILLIS) }
        }
    }

    private suspend fun startOnPort(
        port: Int,
        snapshot: List<SharedFile>,
    ): Pair<EmbeddedServer<*, *>, Int> {
        val server: EmbeddedServer<*, *> = createServer(port = port, files = snapshot)
        try {
            server.start(wait = false)
        } catch (e: Exception) {
            runCatching { server.stop(GRACE_PERIOD_MILLIS, TIMEOUT_MILLIS) }
            throw e
        }
        val resolvedPort: Int? = server.engine.resolvedConnectors()
            .firstOrNull()
            ?.port
            ?.takeIf { resolved: Int -> resolved > 0 }
        val actualPort: Int = resolvedPort
            ?: if (port > 0) port else throw IllegalStateException(
                "Server started but its port could not be determined.",
            )
        return server to actualPort
    }

    private fun createServer(port: Int, files: List<SharedFile>): EmbeddedServer<*, *> {
        val resolver: ContentResolver = contentResolver
        return embeddedServer(CIO, port = port, host = "0.0.0.0") {
            routing {
                get("/") {
                    call.respondText(
                        buildIndexHtml(files),
                        ContentType.Text.Html,
                        HttpStatusCode.OK,
                    )
                }
                get("/files/{index}") {
                    val index: Int? = call.parameters["index"]?.toIntOrNull()
                    val file: SharedFile? = if (index == null) {
                        null
                    } else {
                        files.getOrNull(index)
                    }
                    if (file == null) {
                        call.respondText(
                            "Not found",
                            ContentType.Text.Plain,
                            HttpStatusCode.NotFound,
                        )
                        return@get
                    }
                    val input: InputStream? = openSharedFile(resolver, file)
                    if (input == null) {
                        call.respondText(
                            "Not found",
                            ContentType.Text.Plain,
                            HttpStatusCode.NotFound,
                        )
                        return@get
                    }
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        attachmentValue(file.displayName),
                    )
                    val contentLength: Long? = if (file.sizeBytes > 0L) {
                        file.sizeBytes
                    } else {
                        null
                    }
                    try {
                        call.respondOutputStream(
                            ContentType.Application.OctetStream,
                            HttpStatusCode.OK,
                            contentLength,
                        ) {
                            input.copyTo(this)
                        }
                    } finally {
                        runCatching { input.close() }
                    }
                }
                get("/all.zip") {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        attachmentValue(ZIP_FILE_NAME),
                    )
                    call.respondOutputStream(
                        ContentType.Application.Zip,
                        HttpStatusCode.OK,
                    ) {
                        ZipOutputStream(this).use { zip: ZipOutputStream ->
                            val usedNames = mutableSetOf<String>()
                            for (file: SharedFile in files) {
                                val input: InputStream? = openSharedFile(resolver, file)
                                if (input == null) {
                                    continue
                                }
                                try {
                                    zip.putNextEntry(
                                        ZipEntry(uniqueZipName(file.displayName, usedNames)),
                                    )
                                    input.copyTo(zip)
                                    zip.closeEntry()
                                } finally {
                                    runCatching { input.close() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openSharedFile(
        resolver: ContentResolver,
        file: SharedFile,
    ): InputStream? {
        val uri: Uri = try {
            Uri.parse(file.uri)
        } catch (e: Exception) {
            return null
        }
        return try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds a Content-Disposition attachment value with both a quoted
     * filename (control characters, quotes and backslashes replaced)
     * and an RFC 5987 filename* form, so non-ASCII names survive.
     */
    private fun attachmentValue(displayName: String): String {
        val safeName: String = sanitizeFileName(displayName)
        val encoded: String = URLEncoder.encode(displayName, "UTF-8")
            .replace("+", "%20")
        return "attachment; filename=\"$safeName\"; filename*=UTF-8''$encoded"
    }

    private fun sanitizeFileName(displayName: String): String {
        val cleaned: String = displayName.map { character: Char ->
            if (character == '"' || character == '\\' || character.code < 0x20 ||
                character.code == 0x7F
            ) {
                '_'
            } else {
                character
            }
        }.joinToString(separator = "")
        return cleaned.ifBlank { "file" }
    }

    private fun uniqueZipName(displayName: String, usedNames: MutableSet<String>): String {
        val base: String = sanitizeFileName(displayName)
            .replace('/', '_')
            .replace('\\', '_')
            .ifBlank { "file" }
        if (usedNames.add(base)) {
            return base
        }
        val dot: Int = base.lastIndexOf('.')
        val stem: String = if (dot > 0) base.substring(0, dot) else base
        val extension: String = if (dot > 0) base.substring(dot) else ""
        // Mutable because it counts candidate names until an unused
        // one is found; each loop either returns or increments it.
        var suffix = 2
        while (true) {
            val candidate: String = "$stem ($suffix)$extension"
            if (usedNames.add(candidate)) {
                return candidate
            }
            suffix += 1
        }
    }

    private companion object {
        const val PREFERRED_PORT = 8080
        const val EPHEMERAL_PORT = 0
        const val GRACE_PERIOD_MILLIS = 1000L
        const val TIMEOUT_MILLIS = 1000L
        const val ZIP_FILE_NAME = "drop-files.zip"
    }
}
