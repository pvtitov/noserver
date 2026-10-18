package com.github.pvtitov.noserver.sample

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val configured = (application as SampleApplication).isConfigured
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (configured) MainScreen() else NotConfigured()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConfigured() {
    Text(stringResource(R.string.not_configured), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.not_configured_hint))
}

@Composable
private fun ColumnScope.MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enabled = !state.busy

    StatusLine(state.status)

    Text(state.email ?: stringResource(R.string.not_signed_in), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::logIn, enabled = enabled) { Text(stringResource(R.string.action_log_in)) }
        Button(onClick = viewModel::logOut, enabled = enabled) { Text(stringResource(R.string.action_log_out)) }
    }

    HorizontalDivider()
    Text(stringResource(R.string.my_note_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.ownNoteText,
        onValueChange = viewModel::onOwnNoteChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.my_note_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.updated_at, formatUpdatedAt(state.ownNoteUpdatedAt)))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::upload, enabled = enabled) { Text(stringResource(R.string.action_upload)) }
        Button(onClick = viewModel::download, enabled = enabled) { Text(stringResource(R.string.action_download)) }
    }

    HorizontalDivider()
    Text(stringResource(R.string.my_token_title), style = MaterialTheme.typography.titleMedium)
    val token = state.shareToken
    SelectionContainer {
        Text(token ?: stringResource(R.string.no_token))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { token?.let { copyToken(context, it) } }, enabled = enabled && token != null) {
            Text(stringResource(R.string.copy))
        }
        Button(onClick = { token?.let { shareToken(context, it) } }, enabled = enabled && token != null) {
            Text(stringResource(R.string.share))
        }
    }

    HorizontalDivider()
    Text(stringResource(R.string.shared_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.sharedTokenInput,
        onValueChange = viewModel::onSharedTokenInputChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.shared_token_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = viewModel::readShared, enabled = enabled) { Text(stringResource(R.string.action_read_shared)) }
    state.sharedNote?.let { note ->
        SelectionContainer {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.updated_at, formatUpdatedAt(note.updatedAt)))
    }
}

@Composable
private fun StatusLine(status: Status?) {
    if (status == null) return
    val outcome = status.detail?.let { stringResource(status.outcome, it) } ?: stringResource(status.outcome)
    Text(
        stringResource(R.string.status_format, stringResource(status.action), outcome),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun formatUpdatedAt(updatedAt: Long?): String =
    if (updatedAt == null || updatedAt == 0L) {
        stringResource(R.string.never)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(updatedAt))
    }

private fun copyToken(context: Context, token: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(context.getString(R.string.token_clip_label), token)
    // Anyone holding the token can read the note, so keep it out of clipboard previews.
    // ClipDescription extras exist from API 24; the string key is the constant's value before API 33.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveKey, true) }
    }
    clipboard.setPrimaryClip(clip)
}

private fun shareToken(context: Context, token: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, token)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
}
