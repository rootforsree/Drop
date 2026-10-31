package com.rootdrop.share.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootdrop.share.R
import com.rootdrop.share.picker.PickedFile
import com.rootdrop.share.picker.copyTextToClipboard
import com.rootdrop.share.qr.qrBitmap
import com.rootdrop.share.server.DropShareServer
import com.rootdrop.share.server.SharedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Dispatcher note: no dispatcher binding lives inside the ui package, and
// creating a DI module would mean editing files outside this package, so
// Dispatchers.IO is used for server work and Dispatchers.Default for QR
// encoding. Business logic never runs on the calling thread, and no
// dispatcher is hardcoded for UI-state updates (those run in viewModelScope).

/** One-shot events from the ViewModel. State stays in DropUiState. */
sealed interface DropEvent {
    data object UrlCopied : DropEvent
    data object StopFailed : DropEvent
}

class DropViewModel(
    private val context: Context,
    private val shareServer: DropShareServer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DropUiState())
    val uiState: StateFlow<DropUiState> = _uiState.asStateFlow()

    private val _events = Channel<DropEvent>(Channel.BUFFERED)
    val events: Flow<DropEvent> = _events.receiveAsFlow()

    /**
     * Accepts files from the picker package's system-picker launcher, which
     * already resolved display names and sizes and persisted read
     * permission. PickedFile maps one to one onto the server's SharedFile,
     * so this only converts the type and drops duplicates. Picks are
     * ignored while sharing is active or starting, because the running
     * server serves the snapshot taken at start time; the screen also
     * disables Pick while sharing, so this is a guard, not the normal path.
     */
    fun onPickFilesResult(files: List<PickedFile>) {
        if (files.isEmpty()) {
            return
        }
        val currentStatus = _uiState.value.status
        if (currentStatus is SharingStatus.Sharing || currentStatus is SharingStatus.Starting) {
            return
        }
        _uiState.update { current ->
            val knownUris = current.pickedFiles.map { file -> file.uri }.toSet()
            val fresh = files
                .filter { file -> file.uri !in knownUris }
                .map { file ->
                    SharedFile(
                        uri = file.uri,
                        displayName = file.displayName,
                        sizeBytes = file.sizeBytes,
                    )
                }
            current.copy(pickedFiles = current.pickedFiles + fresh)
        }
    }

    /** Starts sharing the currently picked files, or reports a plain error. */
    fun onStartSharing() {
        val current = _uiState.value
        if (current.status is SharingStatus.Sharing || current.status is SharingStatus.Starting) {
            return
        }
        if (current.pickedFiles.isEmpty()) {
            _uiState.update {
                it.copy(
                    status = SharingStatus.Error(
                        context.getString(R.string.drop_error_no_files),
                    ),
                )
            }
            return
        }
        _uiState.update { it.copy(status = SharingStatus.Starting, shareUrl = null, qrBitmap = null) }
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    shareServer.start(current.pickedFiles)
                }
                val bitmap = withContext(Dispatchers.Default) {
                    qrBitmap(session.baseUrl, QR_SIZE_PX)
                }
                _uiState.update {
                    it.copy(
                        status = SharingStatus.Sharing,
                        shareUrl = session.baseUrl,
                        qrBitmap = bitmap,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _uiState.update {
                    it.copy(
                        status = SharingStatus.Error(
                            context.getString(R.string.drop_error_start_failed),
                        ),
                        shareUrl = null,
                        qrBitmap = null,
                    )
                }
            }
        }
    }

    /**
     * Stops the running server. On success the state returns to Idle with
     * the URL and QR cleared. On failure the sharing state is kept exactly
     * as it was (the server may still be running, so claiming "stopped"
     * would be wrong) and a one-shot StopFailed event is sent instead.
     */
    fun onStopSharing() {
        if (_uiState.value.status is SharingStatus.Idle) {
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { shareServer.stop() }
                _uiState.update {
                    it.copy(
                        status = SharingStatus.Idle,
                        shareUrl = null,
                        qrBitmap = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _events.send(DropEvent.StopFailed)
            }
        }
    }

    /** Removes one picked file. Ignored while sharing is active or starting. */
    fun onRemoveFile(index: Int) {
        val currentStatus = _uiState.value.status
        if (currentStatus is SharingStatus.Sharing || currentStatus is SharingStatus.Starting) {
            return
        }
        _uiState.update { current ->
            if (index !in current.pickedFiles.indices) {
                current
            } else {
                current.copy(
                    pickedFiles = current.pickedFiles.filterIndexed { position, _ ->
                        position != index
                    },
                )
            }
        }
    }

    /** Copies the current share URL to the clipboard and confirms once. */
    fun onCopyUrl() {
        viewModelScope.launch {
            val url = _uiState.value.shareUrl ?: return@launch
            copyTextToClipboard(
                context = context,
                label = context.getString(R.string.drop_clip_label),
                text = url,
            )
            _events.send(DropEvent.UrlCopied)
        }
    }

    private companion object {
        const val QR_SIZE_PX = 512
    }
}
