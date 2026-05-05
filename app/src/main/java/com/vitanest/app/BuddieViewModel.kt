package com.vitanest.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.ChatOpeningResponse
import com.vitanest.app.data.remote.IntentItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.data.remote.QuotaResponse
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun parseTsToDisplay(ts: String): String {
    if (ts.isBlank()) return ""
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx")
        val parsed    = OffsetDateTime.parse(ts, formatter)
        val local     = parsed.atZoneSameInstant(ZoneId.systemDefault())
        local.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "" }
}

data class BubbleMsg(
    val role: String,
    val text: String,
    val provenance: String  = "",
    val elapsedMs: Long     = 0L,
    val isLoading: Boolean  = false,
    val isQueued: Boolean   = false,
    val timeDisplay: String = ""
)

data class BuddieUiState(
    val isLoading: Boolean                    = true,
    val quotaData: QuotaResponse?             = null,
    val quotaExceeded: Boolean                = false,
    val opening: ChatOpeningResponse?         = null,
    val briefData: BriefResponse?             = null,
    val intents: List<IntentItem>             = emptyList(),
    val bubbles: List<BubbleMsg>              = emptyList(),
    val offlineJobs: List<PendingOfflineItem> = emptyList()
)

class BuddieViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuddieUiState())
    val state: StateFlow<BuddieUiState> = _state.asStateFlow()

    private var initialised = false

    fun initialise() {
        if (initialised) return
        initialised = true
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            repository.getQuota().onSuccess { q ->
                _state.value = _state.value.copy(quotaData = q, quotaExceeded = q.status == "quota_exceeded")
            }
            repository.getChatOpening().onSuccess { o ->
                _state.value = _state.value.copy(opening = o)
            }
            repository.getBrief().onSuccess { b ->
                _state.value = _state.value.copy(briefData = b)
            }
            repository.getChatHistory().onSuccess { h ->
                val bubbles = h.exchanges.reversed().map { e ->
                    BubbleMsg(role = e.role, text = e.message, provenance = e.provenance,
                        elapsedMs = e.elapsedMs, timeDisplay = parseTsToDisplay(e.ts))
                }
                _state.value = _state.value.copy(bubbles = bubbles)
            }
            repository.getChatOfflinePending().onSuccess { p ->
                _state.value = _state.value.copy(offlineJobs = p.jobs)
            }
            repository.getIntents().onSuccess { r ->
                _state.value = _state.value.copy(intents = r.intents.filter { it.enabled })
            }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun sendMessage(message: String, offline: Boolean = false) {
        if (message.isBlank()) return
        val trimmed = message.trim()
        val userBubble = BubbleMsg(role = "user", text = trimmed)
        val placeholder = if (offline)
            BubbleMsg(role = "buddy", text = "Queued offline - Buddy will notify you via Telegram", isQueued = true)
        else
            BubbleMsg(role = "buddy", text = "...", isLoading = true)
        _state.value = _state.value.copy(bubbles = _state.value.bubbles + userBubble + placeholder)
        viewModelScope.launch {
            repository.sendChat(trimmed, offline).fold(
                onSuccess = { resp ->
                    val updated = _state.value.bubbles.dropLast(1) + BubbleMsg(
                        role = "buddy", text = resp.response, provenance = resp.provenance,
                        elapsedMs = resp.elapsedMs, isQueued = resp.asyncMode)
                    _state.value = _state.value.copy(bubbles = updated)
                    repository.getQuota().onSuccess { q ->
                        _state.value = _state.value.copy(quotaData = q, quotaExceeded = q.status == "quota_exceeded")
                    }
                },
                onFailure = { err ->
                    val errText = if (err.message?.contains("50/day") == true)
                        "Daily limit reached (50/day). Resets at midnight."
                    else "Could not reach VitaClaw - check Tailscale"
                    val updated = _state.value.bubbles.dropLast(1) + BubbleMsg(role = "buddy", text = errText)
                    _state.value = _state.value.copy(bubbles = updated)
                }
            )
        }
    }

    fun ackOfflineJob(jobId: String) {
        viewModelScope.launch {
            repository.ackOfflineMessage(jobId)
            _state.value = _state.value.copy(offlineJobs = _state.value.offlineJobs.filter { it.jobId != jobId })
        }
    }

    fun refreshQuota() {
        viewModelScope.launch {
            repository.getQuota().onSuccess { q ->
                _state.value = _state.value.copy(quotaData = q, quotaExceeded = q.status == "quota_exceeded")
            }
        }
    }
    fun clearChat() {
        _state.value = _state.value.copy(bubbles = emptyList())
    }
}