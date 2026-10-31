package com.rootdrop.share.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootdrop.share.R
import com.rootdrop.share.picker.rememberFilePickerLauncher

// Dimensions live in this file: no dimens file exists in the working copy
// yet (no res directory landed with the scaffold), per the task allowance.
// All values sit on the 8pt grid.
private val HorizontalPadding = 16.dp
private val VerticalPadding = 16.dp
private val SectionSpacing = 16.dp
private val ItemSpacing = 8.dp
private val ButtonHeight = 48.dp
private val IconButtonSize = 48.dp
private val QrSize = 256.dp
private val ProgressSize = 24.dp

/**
 * Stateful route. Owns the ViewModel, the system-picker launcher, and the
 * one-shot event collection. The ViewModel is never passed below this
 * function; only state and event lambdas reach the content composable.
 */
@Composable
fun DropRoute(
    viewModel: DropViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val launchPicker = rememberFilePickerLauncher { files ->
        viewModel.onPickFilesResult(files)
    }
    val copiedMessage = stringResource(R.string.drop_url_copied)
    val stopFailedMessage = stringResource(R.string.drop_stop_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DropEvent.UrlCopied -> snackbarHostState.showSnackbar(copiedMessage)
                DropEvent.StopFailed -> snackbarHostState.showSnackbar(stopFailedMessage)
            }
        }
    }
    DropScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onPickFiles = launchPicker,
        onStartSharing = viewModel::onStartSharing,
        onStopSharing = viewModel::onStopSharing,
        onRemoveFile = viewModel::onRemoveFile,
        onCopyUrl = viewModel::onCopyUrl,
    )
}

/**
 * Stateless content: state in, event lambdas out. No ViewModel, no side
 * effects in the body, no nested same-axis scrollable (the LazyColumn is
 * the only scrollable on this screen).
 */
@Composable
fun DropScreenContent(
    state: DropUiState,
    snackbarHostState: SnackbarHostState,
    onPickFiles: () -> Unit,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onCopyUrl: () -> Unit,
) {
    val context = LocalContext.current
    val isBusy = state.status is SharingStatus.Sharing ||
        state.status is SharingStatus.Starting
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HorizontalPadding,
                end = HorizontalPadding,
                top = scaffoldPadding.calculateTopPadding() + VerticalPadding,
                bottom = scaffoldPadding.calculateBottomPadding() + VerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            item(key = "title", contentType = "header") {
                Text(
                    text = stringResource(R.string.drop_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item(key = "status", contentType = "status") {
                when (val status = state.status) {
                    SharingStatus.Idle -> Text(
                        text = stringResource(R.string.drop_status_idle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SharingStatus.Starting -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
                        Text(
                            text = stringResource(R.string.drop_status_starting),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    SharingStatus.Sharing -> Text(
                        text = stringResource(
                            R.string.drop_status_sharing,
                            state.shareUrl ?: "",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is SharingStatus.Error -> Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item(key = "pick", contentType = "action") {
                Button(
                    onClick = onPickFiles,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                ) {
                    Text(text = stringResource(R.string.drop_pick_files))
                }
            }
            item(key = "share-action", contentType = "action") {
                when (state.status) {
                    SharingStatus.Sharing -> Button(
                        onClick = onStopSharing,
                        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                    ) {
                        Text(text = stringResource(R.string.drop_stop_sharing))
                    }
                    is SharingStatus.Error -> Button(
                        onClick = onStartSharing,
                        enabled = state.pickedFiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                    ) {
                        Text(text = stringResource(R.string.drop_retry))
                    }
                    SharingStatus.Idle, SharingStatus.Starting -> Button(
                        onClick = onStartSharing,
                        enabled = state.pickedFiles.isNotEmpty() &&
                            state.status !is SharingStatus.Starting,
                        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                    ) {
                        Text(text = stringResource(R.string.drop_start_sharing))
                    }
                }
            }
            if (state.pickedFiles.isEmpty()) {
                item(key = "empty", contentType = "hint") {
                    Text(
                        text = stringResource(R.string.drop_no_files),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                itemsIndexed(
                    items = state.pickedFiles,
                    key = { _, file -> file.uri },
                    contentType = { _, _ -> "file" },
                ) { index, file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                        ) {
                            Text(
                                text = file.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Formatter.formatFileSize(context, file.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = { onRemoveFile(index) },
                            enabled = !isBusy,
                            modifier = Modifier.size(IconButtonSize),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.drop_remove_file,
                                    file.displayName,
                                ),
                            )
                        }
                    }
                }
            }
            val qrBitmap = state.qrBitmap
            val shareUrl = state.shareUrl
            if (state.status is SharingStatus.Sharing && qrBitmap != null && shareUrl != null) {
                item(key = "qr", contentType = "sharing") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.drop_qr_description),
                            modifier = Modifier.size(QrSize),
                        )
                        SelectionContainer {
                            Text(
                                text = shareUrl,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = onCopyUrl,
                            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                        ) {
                            Text(text = stringResource(R.string.drop_copy_url))
                        }
                    }
                }
            }
        }
    }
}
