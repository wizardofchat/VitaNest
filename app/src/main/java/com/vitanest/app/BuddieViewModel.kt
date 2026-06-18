package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BuddieViewModel — Buddie chat state, offline inbox, observations ☘️
// Updated: init fetch now parallel (async/await), not sequential.
//          getBrief() removed — brief comes from HomeViewModel cache.
//          initialise() guard prevents re-fetch on tab switch. ☘️

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.BuddieQueryProvenance
import com.vitanest.app.data.remote.ChatOpeningResponse
import com.vitanest.app.data.remote.IntentItem
import com.vitanest.app.data.remote.ObservationItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.data.remote.QuotaResponse
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val timeDisplay: String = "",
    val queryProvenance: BuddieQueryProvenance? = null
)

data class BuddieUiState(
    val isLoading: Boolean                    = true,
    val quotaData: QuotaResponse?             = null,
    val quotaExceeded: Boolean                = false,
    val opening: ChatOpeningResponse?         = null,
    val briefData: BriefResponse?             = null,
    val intents: List<IntentItem>             = emptyList(),
    val bubbles: List<BubbleMsg>              = emptyList(),
    val offlineJobs: List<PendingOfflineItem> = emptyList(),
    val observations: List<ObservationItem>   = emptyList()
)

class BuddieViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuddieUiState())
    val state: StateFlow<BuddieUiState> = _state.asStateFlow()

    private var initialised = false

    fun initialise(cachedBrief: BriefResponse? = null) {
        // Apply cached brief immediately — no network wait
        if (cachedBrief != null && _state.value.briefData == null) {
            _state.value = _state.value.copy(briefData = cachedBrief)
        }
        if (initialised) return
        initialised = true

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // All calls in parallel — Ask screen opens as fast as the slowest single call,
            // not the sum of all calls
            coroutineScope {
                val quotaDeferred       = async { repository.getQuota() }
                val openingDeferred     = async { repository.getChatOpening() }
                val historyDeferred     = async { repository.getChatHistory() }
                val offlineDeferred     = async { repository.getChatOfflinePending() }
                val intentsDeferred     = async { repository.getIntents() }
                val observationsDeferred= async { repository.getTodayObservations() }

                quotaDeferred.await().onSuccess { q ->
                    _state.value = _state.value.copy(
                        quotaData     = q,
                        quotaExceeded = q.status == "quota_exceeded"
                    )
                }
                openingDeferred.await().onSuccess { o ->
                    _state.value = _state.value.copy(opening = o)
                }
                historyDeferred.await().onSuccess { h ->
                    val bubbles = h.exchanges.reversed().map { e ->
                        BubbleMsg(
                            role        = e.role,
                            text        = e.message,
                            provenance  = e.provenance,
                            elapsedMs   = e.elapsedMs,
                            timeDisplay = parseTsToDisplay(e.ts)
                        )
                    }
                    _state.value = _state.value.copy(bubbles = bubbles)
                }
                offlineDeferred.await().onSuccess { p ->
                    _state.value = _state.value.copy(offlineJobs = p.jobs)
                }
                intentsDeferred.await().onSuccess { r ->
                    _state.value = _state.value.copy(intents = r.intents.filter { it.enabled })
                }
                observationsDeferred.await().onSuccess { o ->
                    _state.value = _state.value.copy(observations = o.observations)
                }
            }

            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun sendMessage(message: String, offline: Boolean = false) {
        if (message.isBlank()) return
        val trimmed    = message.trim()
        val userBubble = BubbleMsg(role = "user", text = trimmed)
        val placeholder = if (offline)
            BubbleMsg(role = "buddy", text = "Queued offline — Buddy will notify you via Telegram", isQueued = true)
        else
            BubbleMsg(role = "buddy", text = "...", isLoading = true)
        _state.value = _state.value.copy(bubbles = _state.value.bubbles + userBubble + placeholder)

        viewModelScope.launch {
            repository.sendChat(trimmed, offline).fold(
                onSuccess = { resp ->
                    val updated = _state.value.bubbles.dropLast(1) + BubbleMsg(
                        role       = "buddy",
                        text       = resp.response,
                        provenance = resp.provenance,
                        elapsedMs  = resp.elapsedMs,
                        isQueued   = resp.asyncMode
                    )
                    _state.value = _state.value.copy(bubbles = updated)
                    // Refresh quota after send
                    repository.getQuota().onSuccess { q ->
                        _state.value = _state.value.copy(
                            quotaData     = q,
                            quotaExceeded = q.status == "quota_exceeded"
                        )
                    }
                },
                onFailure = { err ->
                    val errText = if (err.message?.contains("50/day") == true)
                        "Daily limit reached (50/day). Resets at midnight."
                    else
                        "Could not reach VitaClaw — check Tailscale"
                    val updated = _state.value.bubbles.dropLast(1) +
                            BubbleMsg(role = "buddy", text = errText)
                    _state.value = _state.value.copy(bubbles = updated)
                }
            )
        }
    }

    // NLP Query — separate path from sendMessage(). Hits /buddie/query
    // (skill_executor stack), not /chat. Does not refresh Gemini quota —
    // this endpoint runs its own Job1/Job2 Gemini Flash calls, not counted
    // against the /chat 50/day limit.
    fun sendBuddieQuery(question: String) {
        if (question.isBlank()) return
        val trimmed     = question.trim()
        val userBubble  = BubbleMsg(role = "user", text = trimmed)
        val placeholder = BubbleMsg(role = "buddy", text = "...", isLoading = true)
        _state.value = _state.value.copy(bubbles = _state.value.bubbles + userBubble + placeholder)

        viewModelScope.launch {
            repository.postBuddieQuery(trimmed).fold(
                onSuccess = { resp ->
                    val updated = _state.value.bubbles.dropLast(1) + BubbleMsg(
                        role            = "buddy",
                        text            = resp.answer,
                        elapsedMs       = resp.latencyMs,
                        queryProvenance = resp.provenance
                    )
                    _state.value = _state.value.copy(bubbles = updated)
                },
                onFailure = { _ ->
                    val updated = _state.value.bubbles.dropLast(1) +
                            BubbleMsg(role = "buddy", text = "Could not reach VitaClaw — check Tailscale")
                    _state.value = _state.value.copy(bubbles = updated)
                }
            )
        }
    }

    fun ackOfflineJob(jobId: String) {
        viewModelScope.launch {
            repository.ackOfflineMessage(jobId)
            _state.value = _state.value.copy(
                offlineJobs = _state.value.offlineJobs.filter { it.jobId != jobId }
            )
        }
    }

    fun refreshQuota() {
        viewModelScope.launch {
            repository.getQuota().onSuccess { q ->
                _state.value = _state.value.copy(
                    quotaData     = q,
                    quotaExceeded = q.status == "quota_exceeded"
                )
            }
        }
    }

    fun clearChat() {
        _state.value = _state.value.copy(bubbles = emptyList())
    }

    fun submitFeedback(id: Int, rating: String) {
        // Optimistic local update — reflect immediately, POST in background
        _state.value = _state.value.copy(
            observations = _state.value.observations.map { obs ->
                if (obs.id == id) obs.copy(rating = rating) else obs
            }
        )
        viewModelScope.launch {
            repository.postObservationFeedback(id, rating)
        }
    }
}