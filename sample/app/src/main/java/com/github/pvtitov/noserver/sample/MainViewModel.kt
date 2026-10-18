package com.github.pvtitov.noserver.sample

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.pvtitov.noserver.GoogleDriveRepository
import com.github.pvtitov.noserver.NoServerException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Result of the last action. Only resource IDs and an exception class name: the status line must
 * never repeat the token, e-mail, or note content.
 */
data class Status(@param:StringRes val action: Int, @param:StringRes val outcome: Int, val detail: String? = null)

data class MainUiState(
    val email: String? = null,
    val ownNoteText: String = "",
    val ownNoteUpdatedAt: Long? = null,
    val shareToken: String? = null,
    val sharedTokenInput: String = "",
    val sharedNote: Note? = null,
    val status: Status? = null,
    val busy: Boolean = false,
)

class MainViewModel : ViewModel() {

    private val repository = GoogleDriveRepository(FILE_NAME, Note("", 0))

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    fun onOwnNoteChange(text: String) = _state.update { it.copy(ownNoteText = text) }

    fun onSharedTokenInputChange(text: String) = _state.update { it.copy(sharedTokenInput = text) }

    fun logIn() = runCall(R.string.action_log_in) {
        val email = repository.login<Note>()
        _state.update { it.copy(email = email) }
        val token = repository.getShareToken()
        _state.update { it.copy(shareToken = token) }
        // login() guarantees the file exists, so null here can only mean it doesn't decode as a Note.
        val note = repository.download<Note>() ?: return@runCall R.string.outcome_own_not_a_note
        _state.update { it.copy(ownNoteText = note.text, ownNoteUpdatedAt = note.updatedAt) }
        R.string.outcome_ok
    }

    fun download() = runCall(R.string.action_download) {
        val note = repository.download<Note>() ?: return@runCall R.string.outcome_no_file
        _state.update { it.copy(ownNoteText = note.text, ownNoteUpdatedAt = note.updatedAt) }
        R.string.outcome_ok
    }

    fun upload() = runCall(R.string.action_upload) {
        val note = Note(_state.value.ownNoteText, System.currentTimeMillis())
        if (!repository.upload(note)) return@runCall R.string.outcome_not_uploaded
        _state.update { it.copy(ownNoteUpdatedAt = note.updatedAt) }
        R.string.outcome_ok
    }

    fun readShared() = runCall(R.string.action_read_shared) {
        // Pasting often brings a trailing newline or space along with the token.
        val token = _state.value.sharedTokenInput.trim()
        _state.update { it.copy(sharedNote = null) }
        val note = repository.download<Note>(token)
        _state.update { it.copy(sharedNote = note) }
        if (note == null) R.string.outcome_not_a_note else R.string.outcome_ok
    }

    fun logOut() = runCall(R.string.action_log_out) {
        repository.logout()
        _state.update {
            it.copy(email = null, ownNoteText = "", ownNoteUpdatedAt = null, shareToken = null, sharedNote = null)
        }
        R.string.outcome_ok
    }

    /**
     * Runs one library call at a time. The library keeps a single pending action, so a second call
     * started while signed out would replace the first and leave it suspended forever.
     */
    private fun runCall(@StringRes action: Int, call: suspend () -> Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, status = Status(action, R.string.outcome_running)) }
        viewModelScope.launch {
            val status = try {
                Status(action, call())
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoServerException) {
                when (e) {
                    is NoServerException.NoGoogleAccount -> Status(action, R.string.outcome_no_google_account)
                    is NoServerException.Cancelled -> Status(action, R.string.outcome_cancelled)
                    is NoServerException.NoAccess -> Status(action, R.string.outcome_no_access)
                    // The cause's message isn't shown: it can carry request URLs with file IDs.
                    is NoServerException.Failed -> Status(action, R.string.outcome_failed, e.cause?.javaClass?.simpleName)
                }
            } catch (e: Exception) {
                Status(action, R.string.outcome_unexpected, e.javaClass.simpleName)
            }
            _state.update { it.copy(busy = false, status = status) }
        }
    }

    private companion object {
        const val FILE_NAME = "noserver-sample.json"
    }
}
