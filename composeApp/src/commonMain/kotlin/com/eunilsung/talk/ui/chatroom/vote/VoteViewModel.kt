package com.eunilsung.talk.ui.chatroom.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteFormItem
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.domain.usecase.VoteUseCases
import com.eunilsung.talk.util.Log

class VoteViewModel(
    private val voteUseCases: VoteUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoteUiState>(VoteUiState.Loading)
    val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()

    private val _closeScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeScreen: SharedFlow<Unit> = _closeScreen.asSharedFlow()

    private val backStack = ArrayDeque<VoteUiState>()

    private var chatRoomId: String = ""
    private var currentVoteId: String? = null

    fun onAction(action: VoteActions) {
        when (action) {
            is VoteActions.Load -> {
                chatRoomId = action.chatRoomId
                currentVoteId = action.voteId
                backStack.clear()
                showInitial(action.mode, action.voteId)
            }
            VoteActions.OnRefresh -> refresh()
            VoteActions.OnBack -> back()

            VoteActions.OnCreateClick -> {
                push()
                _uiState.value = VoteUiState.Create()
            }
            is VoteActions.OnVoteClick -> openVote(action.summary)

            is VoteActions.OnTitleChange -> updateForm { it.copy(title = action.title) }
            is VoteActions.OnItemContentChange -> updateForm { form ->
                form.copy(items = form.items.map { item ->
                    if (item.id == action.id) item.copy(content = action.content) else item
                })
            }
            VoteActions.OnAddItem -> updateForm { form ->
                val nextId = (form.items.maxOfOrNull { it.id } ?: -1) + 1
                form.copy(items = form.items + VoteFormItem(id = nextId))
            }
            is VoteActions.OnRemoveItem -> updateForm { form ->
                if (form.items.size <= 2) form
                else form.copy(items = form.items.filterNot { it.id == action.id })
            }
            is VoteActions.OnToggleMultiSelect -> updateForm { it.copy(multiSelect = action.enabled) }
            is VoteActions.OnToggleAnonymous -> updateForm { it.copy(anonymous = action.enabled) }
            is VoteActions.OnToggleAllowAddItem -> updateForm { it.copy(allowAddItem = action.enabled) }
            is VoteActions.OnToggleUseEndTime -> updateForm { it.copy(useEndTime = action.enabled) }
            is VoteActions.OnEndTimeChange -> updateForm { it.copy(endTime = action.endTime) }
            VoteActions.OnSubmitCreate -> submitCreate()

            is VoteActions.OnToggleOption -> toggleOption(action.idx)
            VoteActions.OnSubmitVote -> submitVote()
            VoteActions.OnViewResult -> {
                val id = currentVoteId ?: (_uiState.value as? VoteUiState.Participate)?.detail?.id
                if (!id.isNullOrBlank()) navigateToVote(id, forceResult = true)
            }

            VoteActions.OnReVote -> reVote()
            VoteActions.OnCloseVote -> closeVote()
        }
    }


    private fun push() {
        backStack.addLast(_uiState.value)
    }

    private fun back() {
        val prev = backStack.removeLastOrNull()
        if (prev == null) {
            _closeScreen.tryEmit(Unit)
        } else {
            _uiState.value = prev
        }
    }

    private fun showInitial(mode: VoteMode, voteId: String?) {
        when (mode) {
            VoteMode.LIST -> goToList()
            VoteMode.CREATE -> _uiState.value = VoteUiState.Create()
            VoteMode.PARTICIPATE -> fetchVote(voteId, forceResult = false, onFail = ::goToList)
            VoteMode.RESULT -> fetchVote(voteId, forceResult = true, onFail = ::goToList)
        }
    }

    private fun openVote(summary: VoteSummary) {
        navigateToVote(summary.id, forceResult = summary.hasVoted || summary.isClosed)
    }

    private fun navigateToVote(voteId: String, forceResult: Boolean) {
        push()
        fetchVote(voteId, forceResult, onFail = ::back)
    }

    private fun fetchVote(voteId: String?, forceResult: Boolean, onFail: () -> Unit) {
        if (voteId.isNullOrBlank()) { onFail(); return }
        currentVoteId = voteId
        _uiState.value = VoteUiState.Loading
        viewModelScope.launch {
            val data = runCatching { voteUseCases.getVote(chatRoomId, voteId) }
                .onFailure { Log.message("getVote failed: ${it.message}") }
                .getOrNull()
            if (data == null) {
                onFail()
                return@launch
            }
            val myId = Config.MyInfo.userId
            _uiState.value = if (forceResult || data.isClosed || data.hasVoted(myId)) {
                VoteUiState.Result(result = data.toResult(myId))
            } else {
                VoteUiState.Participate(detail = data.toDetail(myId))
            }
        }
    }

    private fun refresh() {
        when (val s = _uiState.value) {
            is VoteUiState.List -> goToList()
            is VoteUiState.Participate -> fetchVote(s.detail.id, forceResult = false, onFail = {})
            is VoteUiState.Result -> fetchVote(s.result.id, forceResult = true, onFail = {})
            else -> Unit
        }
    }

    private fun goToList(delayMs: Long = 0) {
        currentVoteId = null
        viewModelScope.launch {
            val current = _uiState.value
            if (current is VoteUiState.List) {
                _uiState.value = current.copy(isRefreshing = true)
            } else {
                _uiState.value = VoteUiState.Loading
            }
            if (delayMs > 0) delay(delayMs)
            val votes = runCatching { voteUseCases.getVotes(chatRoomId) }
                .onFailure { Log.message("getVotes failed: ${it.message}") }
                .getOrDefault(emptyList())
            _uiState.value = VoteUiState.List(votes = votes)
        }
    }


    private inline fun updateForm(transform: (VoteForm) -> VoteForm) {
        val current = _uiState.value
        if (current is VoteUiState.Create) {
            _uiState.value = current.copy(form = transform(current.form))
        }
    }

    private fun submitCreate() {
        val current = _uiState.value as? VoteUiState.Create ?: return
        if (!current.form.canSubmit || current.isSubmitting) return
        _uiState.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            runCatching { voteUseCases.createVote(chatRoomId, current.form) }
                .onFailure { Log.message("createVote failed: ${it.message}") }
            returnToList()
        }
    }

    private fun returnToList() {
        backStack.clear()
        goToList(delayMs = 600)
    }


    private fun toggleOption(idx: Int) {
        val current = _uiState.value as? VoteUiState.Participate ?: return
        if (current.detail.hasVoted || current.detail.isClosed) return
        val selected = current.selected.toMutableSet()
        if (current.detail.multiSelect) {
            if (!selected.add(idx)) selected.remove(idx)
        } else {
            selected.clear()
            if (idx !in current.selected) selected.add(idx)
        }
        _uiState.value = current.copy(selected = selected)
    }

    private fun submitVote() {
        val current = _uiState.value as? VoteUiState.Participate ?: return
        if (current.detail.hasVoted || current.detail.isClosed) return
        if (current.selected.isEmpty() || current.isSubmitting) return
        val voteId = current.detail.id
        _uiState.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            val data = runCatching { voteUseCases.submitVote(chatRoomId, voteId, current.selected) }
                .onFailure { Log.message("submitVote failed: ${it.message}") }
                .getOrNull()
            if (data == null) {
                _uiState.value = current
                return@launch
            }
            _uiState.value = VoteUiState.Result(result = data.toResult(Config.MyInfo.userId))
        }
    }

    private fun reVote() {
        val current = _uiState.value as? VoteUiState.Result ?: return
        if (!current.result.canReVote) return
        val voteId = current.result.id
        _uiState.value = VoteUiState.Loading
        viewModelScope.launch {
            val data = runCatching { voteUseCases.reVote(chatRoomId, voteId) }
                .onFailure { Log.message("reVote failed: ${it.message}") }
                .getOrNull()
            if (data == null) {
                _uiState.value = current
                return@launch
            }
            val myId = Config.MyInfo.userId
            _uiState.value = if (data.isClosed) {
                VoteUiState.Result(result = data.toResult(myId))
            } else {
                VoteUiState.Participate(detail = data.toDetail(myId))
            }
        }
    }

    private fun closeVote() {
        val current = _uiState.value as? VoteUiState.Result ?: return
        val result = current.result
        if (!result.canClose) return
        viewModelScope.launch {
            runCatching { voteUseCases.closeVote(chatRoomId, result.id, result.title) }
                .onFailure { Log.message("closeVote failed: ${it.message}") }
            returnToList()
        }
    }
}
