package com.rootdrop.share.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.rootdrop.share.server.SharedFile

/**
 * Sharing status for the Drop screen. Error carries a plain-language message
 * for display; no exception detail is ever shown to the user.
 */
sealed interface SharingStatus {
    data object Idle : SharingStatus
    data object Starting : SharingStatus
    data object Sharing : SharingStatus
    data class Error(val message: String) : SharingStatus
}

/**
 * Single immutable state for the Drop screen, updated only via
 * MutableStateFlow.update { it.copy(...) } in the ViewModel.
 */
@Immutable
data class DropUiState(
    val pickedFiles: List<SharedFile> = emptyList(),
    val status: SharingStatus = SharingStatus.Idle,
    val shareUrl: String? = null,
    val qrBitmap: Bitmap? = null,
)
