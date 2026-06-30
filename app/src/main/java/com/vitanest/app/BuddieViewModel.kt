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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun jobDisplayLabelForBubble(job: PendingOfflineItem): String = when {
    job.response.isNotBlank() -> job.response
    job.message.isNotBlank()  -> job.message
    else                       -> "Job ${job.jobId} finished"
}

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
    val queryProvenance: BuddieQueryProvenance? = null,
    val jobId: String?      = null  // report jobs only — links this bubble
    // to an offlineJobs entry so polling
    // can update it in place once done
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
    val observations: List<ObservationItem>   = emptyList(),
    val isSubmittingReport: Boolean           = false,
    val reportSubmitError: String?            = null
)

class BuddieViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuddieUiState())
    val state: StateFlow<BuddieUiState> = _state.asStateFlow()

    private var initialised = false

    // Polling loop for offline jobs (Dolphin text + report jobs share one
    // list). Only one loop runs at a time — started on initialise() if
    // jobs are already pending, and (re)started whenever a new job is
    // submitted. Self-stops once nothing is pending.
    private var pollJob: Job? = null

    fun initialise(cachedBrief: BriefResponse? = null) {
        // Apply cached brief immediately — no network wait
        if (cachedBrief != null && _state.value.briefData == null) {
            _state.value = _state.value.copy(briefData = cachedBrief)
        }
        if (initialised) return
        initialised = true

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val t0 = System.currentTimeMillis()
            fun lap(label: String) =
                android.util.Log.d("BuddieTiming", "$label: ${System.currentTimeMillis() - t0}ms")

            // All six fire concurrently. isLoading flips false as soon as
            // opening + history land (the two that gate first paint) —
            // quota/offline/intents/observations fill in afterward without
            // blocking the screen. TEMP: lap() logs left in to find which
            // call is actually slow (Logcat tag "BuddieTiming") — remove
            // once the real bottleneck is confirmed and addressed.
            coroutineScope {
                val quotaDeferred       = async { repository.getQuota().also { lap("quota") } }
                val openingDeferred     = async { repository.getChatOpening().also { lap("opening") } }
                val historyDeferred     = async { repository.getChatHistory().also { lap("history") } }
                val offlineDeferred     = async { repository.getChatOfflinePending().also { lap("offline") } }
                val intentsDeferred     = async { repository.getIntents().also { lap("intents") } }
                val observationsDeferred= async { repository.getTodayObservations().also { lap("observations") } }

                // First-paint blockers — screen unblocks as soon as these two land.
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
                _state.value = _state.value.copy(isLoading = false)
                lap("first-paint-unblocked")

                // Background fill-ins — update state whenever they land,
                // no longer gating the screen.
                quotaDeferred.await().onSuccess { q ->
                    _state.value = _state.value.copy(
                        quotaData     = q,
                        quotaExceeded = q.status == "quota_exceeded"
                    )
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

            maybeStartPolling()
        }
    }

    // ── Offline job polling ────────────────────────────────────
    // 4s cadence (middle of the 3-5s the backend contract suggests).
    // Runs only while at least one job is in-flight ("queued" — the
    // status returned on submit per the order_pnl_report contract — or
    // "pending"); self-stops once nothing is in-flight. 60s soft timeout
    // per overall loop run — after that we stop polling rather than spin
    // forever (matches contract doc's guidance: no server-side timeout
    // is enforced, so the client must own this).
    private fun isInFlight(status: String) = status == "pending" || status == "queued"

    private fun maybeStartPolling() {
        val hasPending = _state.value.offlineJobs.any { isInFlight(it.status) }
        android.util.Log.d("BuddiePoll", "maybeStartPolling called — hasPending=$hasPending, pollJobActive=${pollJob?.isActive}")
        if (!hasPending) return
        if (pollJob?.isActive == true) {
            android.util.Log.d("BuddiePoll", "skipped — a poll loop is already active")
            return
        }

        pollJob = viewModelScope.launch {
            // No hard timeout — keep polling as long as anything is
            // in-flight. A loop that gives up after a guessed duration
            // and never restarts itself is worse than one that runs a
            // bit longer on a slow job: the symptom (observed 2026-06-30)
            // was the UI getting stuck until the app was force-closed and
            // reopened, because nothing else calls maybeStartPolling()
            // except initialise() and a fresh submit. Server-side jobs
            // are confirmed to complete in under 10s in practice, so this
            // loop in normal operation just runs a handful of ticks —
            // the absence of a cap is a safety net, not an expectation.
            android.util.Log.d("BuddiePoll", "poll loop started")
            while (true) {
                delay(4_000L)
                android.util.Log.d("BuddiePoll", "tick — fetching /chat/offline/pending")
                repository.getChatOfflinePending().fold(
                    onSuccess = { p ->
                        android.util.Log.d("BuddiePoll", "fetch OK — ${p.jobs.size} jobs, statuses=${p.jobs.map { it.status }}")
                        // Merge by jobId — do NOT blind-replace. The
                        // server's /pending list can lag a few seconds
                        // behind a just-submitted job (confirmed
                        // 2026-06-30: job completed_at was AFTER the poll
                        // tick that should have caught it, meaning the
                        // job hadn't appeared in the list yet at that
                        // exact moment). A blind replace silently drops
                        // the locally-tracked in-flight job from state,
                        // the "nothing in-flight" check then sees an
                        // empty set and exits — permanently, since the
                        // job's real completion is never re-checked.
                        // Server entries always win when present (their
                        // status is authoritative); a local-only job
                        // missing from the response is kept as-is so the
                        // loop keeps polling until the server actually
                        // reports it.
                        val serverById = p.jobs.associateBy { it.jobId }
                        val merged = _state.value.offlineJobs.map { local ->
                            serverById[local.jobId] ?: local
                        }
                        // Any server job not already tracked locally
                        // (e.g. from a previous session) gets added too.
                        val localIds = merged.map { it.jobId }.toSet()
                        val mergedFull = merged + p.jobs.filter { it.jobId !in localIds }
                        _state.value = _state.value.copy(offlineJobs = mergedFull)
                        syncReportBubbles(mergedFull)
                    },
                    onFailure = { err ->
                        android.util.Log.e("BuddiePoll", "fetch FAILED: ${err.message}", err)
                    }
                )
                if (_state.value.offlineJobs.none { isInFlight(it.status) }) {
                    android.util.Log.d("BuddiePoll", "nothing in-flight, loop exiting")
                    return@launch
                }
            }
        }
    }

    // Updates any chat bubble tagged with a jobId once that job's status
    // changes — keeps the chat-window bubble in sync with the inbox row
    // for the same job, rather than only the inbox strip reflecting it.
    private fun syncReportBubbles(jobs: List<PendingOfflineItem>) {
        val byId = jobs.associateBy { it.jobId }
        val updated = _state.value.bubbles.map { bubble ->
            val jobId = bubble.jobId ?: return@map bubble
            val job   = byId[jobId] ?: return@map bubble
            if (isInFlight(job.status)) return@map bubble
            bubble.copy(
                text      = if (job.status == "error") "Report failed — try again"
                else jobDisplayLabelForBubble(job),
                isQueued  = false,
                isLoading = false
            )
        }
        if (updated != _state.value.bubbles) {
            _state.value = _state.value.copy(bubbles = updated)
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
                            BubbleMsg(role = "buddy", text = "Couldn't reach VitaClaw for that query — try again")
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

    // Acks every finished job at once — never an in-flight one, so a
    // report still generating is never silently dropped by "Clear all".
    fun clearAllOfflineJobs() {
        val toClear = _state.value.offlineJobs.filter { !isInFlight(it.status) }
        if (toClear.isEmpty()) return
        viewModelScope.launch {
            coroutineScope {
                toClear.forEach { job -> launch { repository.ackOfflineMessage(job.jobId) } }
            }
            val clearedIds = toClear.map { it.jobId }.toSet()
            _state.value = _state.value.copy(
                offlineJobs = _state.value.offlineJobs.filterNot { it.jobId in clearedIds }
            )
        }
    }

    // ── Reports ─────────────────────────────────────────────────
    // order_pnl_report is the first report type. tickers must be
    // non-empty — mirrors the server's own 400 guard, checked here too
    // so the failure is instant and doesn't round-trip the network.
    fun submitReport(
        tickers: List<String>,
        dateFrom: String? = null,
        dateTo: String? = null,
        top5: Boolean = false,
        rankBy: String = "dca"
    ) {
        if (tickers.isEmpty()) {
            _state.value = _state.value.copy(reportSubmitError = "Pick at least one ticker")
            return
        }
        _state.value = _state.value.copy(isSubmittingReport = true, reportSubmitError = null)

        val label = if (top5) "Top 5 best/worst" else "Full export"
        val requestSummary = "Order PnL Report — ${tickers.joinToString(", ")} — $label"

        viewModelScope.launch {
            repository.submitOfflineReport(
                tickers  = tickers,
                dateFrom = dateFrom,
                dateTo   = dateTo,
                top5     = top5,
                rankBy   = rankBy
            ).fold(
                onSuccess = { resp ->
                    val newJob = PendingOfflineItem(
                        jobId   = resp.jobId,
                        message = requestSummary,
                        status  = resp.status
                    )
                    // Chat window: user bubble for the request, queued
                    // buddy bubble tagged with jobId so polling can find
                    // and update it once the report finishes.
                    val userBubble  = BubbleMsg(role = "user", text = requestSummary)
                    val queuedBubble = BubbleMsg(
                        role     = "buddy",
                        text     = "Generating report — job ${resp.jobId}…",
                        isQueued = true,
                        jobId    = resp.jobId
                    )
                    _state.value = _state.value.copy(
                        isSubmittingReport = false,
                        offlineJobs        = _state.value.offlineJobs + newJob,
                        bubbles            = _state.value.bubbles + userBubble + queuedBubble
                    )
                    maybeStartPolling()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isSubmittingReport = false,
                        reportSubmitError  = err.message ?: "Could not submit report — check Tailscale"
                    )
                }
            )
        }
    }

    // whoop_health_report — second report type. All params optional
    // server-side, so no client-side empty-field guard like order_pnl's
    // ticker check. A known backend race ("another operation is in
    // progress") can fire if a second health-report request lands while
    // one is still generating — hasHealthReportInFlight() lets the UI
    // disable Generate while that's true, rather than relying on the
    // server to reject the second request cleanly.
    fun hasHealthReportInFlight(): Boolean =
        _state.value.offlineJobs.any {
            isInFlight(it.status) && it.message.startsWith("Health Report")
        }

    fun submitHealthReport(
        dateFrom: String? = null,
        dateTo: String? = null,
        detailDates: List<String>? = null,
        formats: List<String> = listOf("xlsx", "pdf")
    ) {
        if (hasHealthReportInFlight()) {
            _state.value = _state.value.copy(
                reportSubmitError = "A health report is already generating — wait for it to finish"
            )
            return
        }
        _state.value = _state.value.copy(isSubmittingReport = true, reportSubmitError = null)

        val rangeLabel = if (dateFrom != null || dateTo != null)
            "${dateFrom ?: "…"} to ${dateTo ?: "…"}" else "full history"
        val requestSummary = "Health Report — $rangeLabel"

        viewModelScope.launch {
            repository.submitOfflineHealthReport(
                dateFrom    = dateFrom,
                dateTo      = dateTo,
                detailDates = detailDates,
                formats     = formats
            ).fold(
                onSuccess = { resp ->
                    val newJob = PendingOfflineItem(
                        jobId   = resp.jobId,
                        message = requestSummary,
                        status  = resp.status
                    )
                    val userBubble   = BubbleMsg(role = "user", text = requestSummary)
                    val queuedBubble = BubbleMsg(
                        role     = "buddy",
                        text     = "Generating health report — job ${resp.jobId}…",
                        isQueued = true,
                        jobId    = resp.jobId
                    )
                    _state.value = _state.value.copy(
                        isSubmittingReport = false,
                        offlineJobs        = _state.value.offlineJobs + newJob,
                        bubbles            = _state.value.bubbles + userBubble + queuedBubble
                    )
                    maybeStartPolling()
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isSubmittingReport = false,
                        reportSubmitError  = err.message ?: "Could not submit report — check Tailscale"
                    )
                }
            )
        }
    }

    fun clearReportSubmitError() {
        _state.value = _state.value.copy(reportSubmitError = null)
    }

    // Safety net for the 2026-06-30 stuck-polling bug: if the polling
    // coroutine got suspended/cancelled by an Activity lifecycle event
    // while backgrounded, nothing else calls maybeStartPolling() again
    // until app restart. Call this from a LaunchedEffect keyed on screen
    // visibility (e.g. re-entering the Chat tab) so a stuck job recovers
    // without requiring a full app close+reopen.
    fun resumePollingIfNeeded() {
        maybeStartPolling()
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