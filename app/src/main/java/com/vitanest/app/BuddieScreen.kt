package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// BuddieScreen — merged Buddie tab: Chat · Trade · Observations sub-tabs
// Replaces standalone AskScreen + BuddieTradeScreen in bottom nav (single "Buddie" entry).
// Stage 1+2 (2026-06-18): shell + sub-tab row + Chat slot fully wired.
//                         Trade/Observations are stub placeholders — stage 3/4.
// Layout: Header (title + recovery pill) → Sub-tab row → per-tab content
// Chat slot content lifted from AskScreen.kt verbatim, minus the observations strip
// (observations now live in their own sub-tab, not duplicated in Chat). ☘️

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.BriefStructured
import com.vitanest.app.data.remote.BuddieBudgetMonth
import com.vitanest.app.data.remote.BuddieBudgetResponse
import com.vitanest.app.data.remote.BuddieCandidateItem
import com.vitanest.app.data.remote.BuddieCandidatesResponse
import com.vitanest.app.data.remote.BuddieExcludedItem
import com.vitanest.app.data.remote.BuddieGrowthCandidateItem
import com.vitanest.app.data.remote.BuddieGrowthCandidatesResponse
import com.vitanest.app.data.remote.BuddieQueryProvenance
import com.vitanest.app.data.remote.BuddieTradeItem
import com.vitanest.app.data.remote.BuddieTradesResponse
import com.vitanest.app.data.remote.ObservationItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.vitanest.app.data.repository.VitaClawRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class ChatMode { AUTO, OFFLINE, QUERY, REPORT }
private enum class BuddieSubTab { CHAT, TRADE, OBSERVATIONS }

private val BuddyGreen  = Color(0xFF2D6A4F)
private val AmberDash   = Color(0xFFF59E0B)
private val QueryBlue   = Color(0xFF1D4ED8)
private val ReportPurple= Color(0xFF7C3AED)
private val ErrorRed    = Color(0xFFC0392B)
private val ConfGreen   = Color(0xFF3B6D11)
private val ConfAmber   = Color(0xFFBA7517)
private val ConfRed     = Color(0xFFA32D2D)
private val ConfGreenBg = Color(0xFFEAF3DE)
private val ConfAmberBg = Color(0xFFFAEEDA)
private val ConfRedBg   = Color(0xFFFCEBEB)

// domain_memory parent_domain → badge colour
private val DomainFinanceBg  = Color(0xFFDBEAFE)   // blue-100
private val DomainFinanceFg  = Color(0xFF1D4ED8)   // blue-700
private val DomainHealthBg   = Color(0xFFDCFCE7)   // green-100
private val DomainHealthFg   = Color(0xFF15803D)   // green-700
private val DomainEnergyBg   = Color(0xFFFEF3C7)   // amber-100
private val DomainEnergyFg   = Color(0xFFB45309)   // amber-700
private val DomainNewBg      = Color(0xFFF3F4F6)   // grey-100 — v0 · 0 obs
private val DomainNewFg      = Color(0xFF9CA3AF)   // grey-400

// ── Trade colours (lifted from BuddieTradeScreen.kt) ───────────
private val TradeGreen      = Color(0xFF2D6A4F)
private val TradeGreenBg    = Color(0xFFEAF3DE)
private val TradeGreenFg    = Color(0xFF3B6D11)
private val TradeAmberBg    = Color(0xFFFAEEDA)
private val TradeAmberFg    = Color(0xFF854F0B)
private val TradeRedBg      = Color(0xFFFCEBEB)
private val TradeRedFg      = Color(0xFFA32D2D)
private val TradeMutedBg    = Color(0xFFF2EFE8)
private val TradeBlue       = Color(0xFF185FA5)
private val TradeBlueBg     = Color(0xFFE6F1FB)
private val TradeBlueFg     = Color(0xFF185FA5)

enum class TradeTab { Income, Growth }

// ── Observations sub-tab state ──────────────────────────────────
enum class ObsDomainFilter { ALL, FINANCE, HEALTH, ENERGY, CROSS }

// Maps raw sub-domain strings (e.g. "income_performance", "hrv", "solar")
// to the four parent filter buckets shown as chips. Mirrors the icon
// grouping already used in ObservationCard, kept as a single source of truth.
private fun parentDomainOf(domain: String): ObsDomainFilter = when (domain) {
    "hrv", "spo2", "strain", "recovery", "rhr", "sleep" -> ObsDomainFilter.HEALTH
    "finance", "income", "portfolio", "capital",
    "income_performance", "income_signal",
    "portfolio_health", "portfolio_performance"         -> ObsDomainFilter.FINANCE
    "energy", "solar", "ev", "eddi", "grid", "summary"  -> ObsDomainFilter.ENERGY
    "cross"                                              -> ObsDomainFilter.CROSS
    else                                                  -> ObsDomainFilter.CROSS
}

data class ObservationsUiState(
    val selectedDate:   LocalDate          = LocalDate.now(),
    val responseDate:   String             = "",
    val observations:   List<ObservationItem> = emptyList(),
    val isLoading:      Boolean            = true,
    val error:          String?            = null,
    val domainFilter:   ObsDomainFilter    = ObsDomainFilter.ALL
)

class ObservationsViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ObservationsUiState())
    val state: StateFlow<ObservationsUiState> = _state.asStateFlow()

    init { load(LocalDate.now()) }

    fun goToPreviousDay() = load(_state.value.selectedDate.minusDays(1))

    fun goToNextDay() {
        val next = _state.value.selectedDate.plusDays(1)
        if (!next.isAfter(LocalDate.now())) load(next)
    }

    fun isToday(): Boolean = _state.value.selectedDate == LocalDate.now()

    fun setFilter(filter: ObsDomainFilter) {
        _state.value = _state.value.copy(domainFilter = filter)
    }

    fun submitFeedback(id: Int, rating: String) {
        _state.value = _state.value.copy(
            observations = _state.value.observations.map { obs ->
                if (obs.id == id) obs.copy(rating = rating) else obs
            }
        )
        viewModelScope.launch {
            repository.postObservationFeedback(id, rating)
        }
    }

    private fun load(date: LocalDate) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, selectedDate = date)
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            repository.getTodayObservations(obsDate = dateStr).fold(
                onSuccess = { resp ->
                    _state.value = _state.value.copy(
                        responseDate = resp.date,
                        observations = resp.observations,
                        isLoading    = false
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error     = err.message ?: "Could not load observations"
                    )
                }
            )
        }
    }
}

data class BuddieTradeState(
    val trades:           BuddieTradesResponse?          = null,
    val budget:           BuddieBudgetResponse?          = null,
    val incomeCandidates: BuddieCandidatesResponse?      = null,
    val growthCandidates: BuddieGrowthCandidatesResponse? = null,
    val selectedTab:      TradeTab                       = TradeTab.Income,
    val isLoading:        Boolean                        = true,
    val error:            String?                        = null
)

class BuddieTradeViewModel(
    private val repository: VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuddieTradeState())
    val state: StateFlow<BuddieTradeState> = _state.asStateFlow()

    init { load() }

    fun selectTab(tab: TradeTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tradesDeferred          = async { repository.getBuddieTrades() }
                val budgetDeferred          = async { repository.getBuddieBudget(months = 3) }
                val incomeCandidatesDeferred = async { repository.getBuddieCandidates() }
                val growthCandidatesDeferred = async { repository.getBuddieGrowthCandidates() }

                val trades           = tradesDeferred.await()
                val budget           = budgetDeferred.await()
                val incomeCandidates = incomeCandidatesDeferred.await()
                val growthCandidates = growthCandidatesDeferred.await()

                _state.value = _state.value.copy(
                    trades           = trades.getOrNull(),
                    budget           = budget.getOrNull(),
                    incomeCandidates = incomeCandidates.getOrNull(),
                    // null is valid — growth report not yet generated; handled in UI
                    growthCandidates = growthCandidates.getOrNull(),
                    isLoading        = false,
                    error            = if (trades.isFailure && budget.isFailure && incomeCandidates.isFailure)
                        "Could not load trade data" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

@Composable
fun BuddieScreen(
    navController: NavController,
    viewModel:     BuddieViewModel,
    repository:    VitaClawRepository = VitaClawRepository()
) {
    val state by viewModel.state.collectAsState()
    var selectedSubTab by remember { mutableStateOf(BuddieSubTab.CHAT) }

    // Hoisted so the Trade red dot reflects real state even before the
    // Trade sub-tab has been opened — same reasoning as unratedObservations below.
    val tradeViewModel = remember { BuddieTradeViewModel(repository) }
    val tradeState      by tradeViewModel.state.collectAsState()

    // Observations sub-tab owns its own day-navigable state, separate from
    // BuddieViewModel's today-only fetch (used only for the red dot below).
    val observationsViewModel = remember { ObservationsViewModel(repository) }

    LaunchedEffect(Unit) { viewModel.initialise() }

    // Safety net for the 2026-06-30 stuck-polling bug: if backgrounding
    // the app suspended the poll loop and a report job's status update
    // was missed, resuming the app re-checks and restarts polling rather
    // than requiring a full force-close + reopen to recover.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resumePollingIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Red-dot source data — cheap derivation from already-loaded state.
    // Trade dot: any trade awaiting Bought/Skipped decision this month, either track.
    // Observations dot: any observation not yet rated useful/wrong/important.
    val tradeHasUnactioned   = tradeState.trades?.trades?.any { it.status == "active" } ?: false
    val unratedObservations  = state.observations.count { it.rating == null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp)) {

            // ── Header ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Buddie",
                        fontFamily = T.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        color      = T.Ink
                    )
                    state.opening?.let { o ->
                        val pillColor = when (o.recoveryColour) {
                            "green" -> BuddyGreen
                            "red"   -> ErrorRed
                            else    -> Color(0xFFD4A017)
                        }
                        Box(
                            modifier = Modifier
                                .background(pillColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text       = "${o.recoveryScore.toInt()}% recovery",
                                fontSize   = 11.sp,
                                color      = T.Paper,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = T.heavyRule, color = T.Ink)

                // ── Sub-tab row ───────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubTabChip(
                        label    = "Chat",
                        selected = selectedSubTab == BuddieSubTab.CHAT,
                        showDot  = false,
                        onClick  = { selectedSubTab = BuddieSubTab.CHAT }
                    )
                    SubTabChip(
                        label    = "Trade",
                        selected = selectedSubTab == BuddieSubTab.TRADE,
                        showDot  = tradeHasUnactioned,
                        onClick  = { selectedSubTab = BuddieSubTab.TRADE }
                    )
                    SubTabChip(
                        label    = "Observations",
                        selected = selectedSubTab == BuddieSubTab.OBSERVATIONS,
                        showDot  = unratedObservations > 0,
                        onClick  = { selectedSubTab = BuddieSubTab.OBSERVATIONS }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Sub-tab content ───────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedSubTab) {
                    BuddieSubTab.CHAT          -> ChatTabContent(
                        navController = navController,
                        viewModel     = viewModel,
                        repository    = repository
                    )
                    BuddieSubTab.TRADE         -> TradeTabContent(
                        viewModel  = tradeViewModel,
                        repository = repository
                    )
                    BuddieSubTab.OBSERVATIONS  -> ObservationsTabContent(
                        viewModel = observationsViewModel
                    )
                }
            }
        }

        InkBottomNav(
            current       = "buddie",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Sub-tab chip ──────────────────────────────────────────────
@Composable
private fun SubTabChip(
    label: String,
    selected: Boolean,
    showDot: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) T.Ink else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (selected) 0.dp else 0.5.dp,
                color = T.Rule,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = label,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color      = if (selected) T.Paper else T.Muted
            )
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(ErrorRed, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

// ── Placeholder (stage 3/4 fill these in) ──────────────────────
@Composable
private fun PlaceholderTabContent(label: String) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = T.meta, color = T.Muted)
    }
}

// ── Chat sub-tab ────────────────────────────────────────────────
// Lifted from AskScreen.kt verbatim (header/nav stripped — shell owns those).
// Observations strip removed — relocated to its own sub-tab, not duplicated here.

// ── Report definitions ───────────────────────────────────────────
// One entry per report type. Adding report #3 means: a new ReportKind +
// a new entry here + a new *FormState subtype + a new *ParamsBody
// composable (see below) — never a new screen or new job-polling logic
// (that's all generic — see OfflineInbox / OfflineJobRow further down).
private enum class ReportKind { ORDER_PNL, HEALTH_REPORT }

private data class ReportDefinition(
    val kind: ReportKind,
    val title: String,
    val description: String,
    val available: Boolean = true
)

private val REPORT_DEFINITIONS = listOf(
    ReportDefinition(
        kind        = ReportKind.ORDER_PNL,
        title       = "Order PnL Report",
        description = "Per-order profit/loss across tickers"
    ),
    ReportDefinition(
        kind        = ReportKind.HEALTH_REPORT,
        title       = "Health Report",
        description = "Whoop recovery, sleep & strain summary"
    )
)

// Per-report params, one subtype per ReportKind. ChatTabContent holds a
// single `var formState: ReportFormState` and swaps the instance when the
// user picks a different report from the dropdown — ReportParamsPanel
// dispatches to the matching *ParamsBody based on which subtype it is.
private sealed class ReportFormState {
    data class OrderPnl(
        val selectedTickers: Set<String> = setOf("JEPQ", "QYLP"),
        val top5: Boolean = false
    ) : ReportFormState()

    data class HealthReport(
        val dateFrom: String? = null,
        val dateTo: String? = null,
        val detailDates: List<String> = emptyList()
    ) : ReportFormState() {
        companion object {
            // 1st of current month through today — actually sent if the
            // user doesn't change the fields, not just a placeholder.
            fun defaultCurrentMonth(): HealthReport {
                val today     = LocalDate.now()
                val firstDay  = today.withDayOfMonth(1)
                val fmt       = DateTimeFormatter.ISO_LOCAL_DATE
                return HealthReport(dateFrom = firstDay.format(fmt), dateTo = today.format(fmt))
            }
        }
    }
}

@Composable
private fun ChatTabContent(
    navController: NavController,
    viewModel:     BuddieViewModel,
    repository:    VitaClawRepository
) {
    val state     by viewModel.state.collectAsState()
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context   = LocalContext.current

    var inputText     by remember { mutableStateOf("") }
    var chatMode      by remember { mutableStateOf(ChatMode.AUTO) }
    var modeExpanded  by remember { mutableStateOf(false) }
    var tilesExpanded by remember { mutableStateOf(false) }
    var quotaExpanded by remember { mutableStateOf(false) }
    var inboxExpanded by remember { mutableStateOf(false) }
    var isSending     by remember { mutableStateOf(false) }

    // ── Report mode state ──────────────────────────────────────
    // formState holds whichever report's own params are active — swaps
    // to the matching default whenever a different report is picked from
    // the dropdown (see the dropdown's onClick handlers further below).
    var selectedReport by remember { mutableStateOf(REPORT_DEFINITIONS.first()) }
    var formState: ReportFormState by remember {
        mutableStateOf(ReportFormState.OrderPnl())
    }

    LaunchedEffect(state.bubbles.size) {
        if (state.bubbles.isNotEmpty()) {
            listState.animateScrollToItem(state.bubbles.size - 1)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val text = readTextFile(context, it)
            if (text.isNotBlank()) {
                inputText = text.trim()
                chatMode  = ChatMode.OFFLINE
            }
        }
    }

    fun sendMessage() {
        if (inputText.isBlank() || isSending) return
        isSending = true
        if (chatMode == ChatMode.QUERY) {
            viewModel.sendBuddieQuery(inputText)
        } else {
            viewModel.sendMessage(inputText, chatMode == ChatMode.OFFLINE)
        }
        inputText = ""
        isSending = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        QuotaTile(
            quota      = state.quotaData,
            isExpanded = quotaExpanded,
            onToggle   = { quotaExpanded = !quotaExpanded }
        )

        // ── Offline Inbox ─────────────────────────────────
        if (state.offlineJobs.isNotEmpty()) {
            OfflineInbox(
                jobs       = state.offlineJobs,
                expanded   = inboxExpanded,
                onToggle   = { inboxExpanded = !inboxExpanded },
                onDownload = { job ->
                    if (job.hasFile) {
                        // File jobs: Download is silent save, job stays in
                        // the list so Share is still available afterward.
                        downloadJobFile(context, scope, repository, job)
                    } else {
                        // Text jobs: unchanged one-tap save+share+dismiss.
                        downloadJobAsText(context, job)
                        viewModel.ackOfflineJob(job.jobId)
                    }
                },
                onShare    = { job ->
                    if (job.hasFile) {
                        shareJobFile(context, scope, repository, job)
                        viewModel.ackOfflineJob(job.jobId)
                    }
                },
                onDismiss  = { job -> viewModel.ackOfflineJob(job.jobId) },
                onClearAll = { viewModel.clearAllOfflineJobs() }
            )
        }

        // ── Chat area (weighted — fills remaining space, pushing
        //    tiles + input bar down to sit just above the bottom nav) ──
        if (state.isLoading) {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color       = BuddyGreen,
                        strokeWidth = 1.5.dp,
                        modifier    = Modifier.size(24.dp)
                    )
                    Text(text = "Buddy is thinking…", style = T.meta, color = T.Muted)
                }
            }
        } else {
            LazyColumn(
                state          = listState,
                modifier       = Modifier
                    .weight(1f)
                    .padding(horizontal = T.screenPadding),
                contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)
            ) {
                state.briefData?.structured?.let { s ->
                    item {
                        BriefCard(structured = s)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                state.opening?.let { o ->
                    item {
                        BuddyBubble(text = o.summary, provenance = o.provenance)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(state.bubbles) { b ->
                    when (b.role) {
                        "user"  -> UserBubble(b.text, b.timeDisplay)
                        "buddy" -> {
                            val linkedJob = b.jobId?.let { id -> state.offlineJobs.find { it.jobId == id } }
                            BuddyBubble(
                                text        = b.text,
                                provenance  = b.provenance,
                                elapsedMs   = b.elapsedMs,
                                isLoading   = b.isLoading,
                                isQueued    = b.isQueued,
                                timeDisplay = b.timeDisplay,
                                queryProvenance = b.queryProvenance,
                                reportJob   = linkedJob,
                                onDownloadReport = { job -> downloadJobFile(context, scope, repository, job) },
                                onShareReport    = { job ->
                                    shareJobFile(context, scope, repository, job)
                                    viewModel.ackOfflineJob(job.jobId)
                                }
                            )
                        }
                        else -> SystemCard(b.text)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // ── Quick tiles ───────────────────────────────────
        if (state.intents.isNotEmpty()) {
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Spacer(modifier = Modifier.height(8.dp))
            val visible = if (tilesExpanded) state.intents else state.intents.take(6)
            LazyRow(
                modifier              = Modifier.padding(horizontal = T.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visible) { intent ->
                    IntentTile(
                        label = intent.label,
                        onTap = {
                            inputText = intent.testQuery
                            sendMessage()
                        }
                    )
                }
                if (state.intents.size > 6) {
                    item {
                        IntentExpandChip(
                            expanded = tilesExpanded,
                            count    = state.intents.size - 6,
                            onTap    = { tilesExpanded = !tilesExpanded }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        }

        // ── Input + controls (pinned bottom) ───────────────
        HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (chatMode == ChatMode.REPORT) {
                ReportParamsPanel(
                    report             = selectedReport,
                    formState          = formState,
                    onFormStateChange  = { formState = it },
                    isSubmitting       = state.isSubmittingReport,
                    submitError        = state.reportSubmitError,
                    onDismissError     = { viewModel.clearReportSubmitError() }
                )
            } else {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = {
                        Text(
                            text      = if (state.quotaExceeded) "Quota exceeded"
                            else when (chatMode) {
                                ChatMode.OFFLINE -> "Offline prompt…"
                                ChatMode.QUERY   -> "Ask about your finance data…"
                                else             -> "Ask Buddy…"
                            },
                            style     = T.meta,
                            fontStyle = FontStyle.Italic
                        )
                    },
                    enabled         = (chatMode == ChatMode.QUERY || !state.quotaExceeded) && !isSending,
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = when (chatMode) {
                            ChatMode.OFFLINE -> AmberDash
                            ChatMode.QUERY   -> QueryBlue
                            else             -> T.Ink
                        },
                        unfocusedBorderColor = when (chatMode) {
                            ChatMode.OFFLINE -> AmberDash.copy(alpha = 0.5f)
                            ChatMode.QUERY   -> QueryBlue.copy(alpha = 0.5f)
                            else             -> T.Rule
                        },
                        focusedTextColor     = T.Ink,
                        unfocusedTextColor   = T.Ink,
                        cursorColor          = T.Ink,
                        disabledBorderColor  = T.Rule,
                        disabledTextColor    = T.Muted
                    ),
                    textStyle = T.meta,
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (chatMode != ChatMode.REPORT) {
                    // File upload
                    OutlinedButton(
                        onClick        = { filePicker.launch(arrayOf("text/plain", "text/*")) },
                        border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier       = Modifier.height(36.dp)
                    ) {
                        Text(text = "File", fontSize = 11.sp, color = T.Muted)
                    }

                    // Clear chat
                    OutlinedButton(
                        onClick        = { viewModel.clearChat() },
                        border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier       = Modifier.height(36.dp)
                    ) {
                        Text(text = "Clear", fontSize = 11.sp, color = T.Muted)
                    }
                }

                // Mode dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick        = { modeExpanded = true },
                        border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier       = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text(
                            text     = when (chatMode) {
                                ChatMode.OFFLINE -> "Offline"
                                ChatMode.QUERY   -> "NLP Query"
                                ChatMode.REPORT  -> selectedReport.title
                                else             -> "Auto"
                            },
                            fontSize = 11.sp,
                            color    = when (chatMode) {
                                ChatMode.OFFLINE -> AmberDash
                                ChatMode.QUERY   -> QueryBlue
                                ChatMode.REPORT  -> ReportPurple
                                else             -> T.Ink
                            }
                        )
                        Text(" ▾", fontSize = 11.sp, color = T.Muted)
                    }
                    DropdownMenu(
                        expanded         = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        modifier         = Modifier.background(T.Paper)
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Auto — Polars → Gemini", style = T.meta) },
                            onClick = { chatMode = ChatMode.AUTO; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text    = { Text("Offline — dolphin3:8b", style = T.meta) },
                            onClick = { chatMode = ChatMode.OFFLINE; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text    = { Text("NLP Query — finance data", style = T.meta) },
                            onClick = { chatMode = ChatMode.QUERY; modeExpanded = false }
                        )
                        REPORT_DEFINITIONS.forEach { def ->
                            DropdownMenuItem(
                                text    = { Text("Report — ${def.title}", style = T.meta) },
                                onClick = {
                                    selectedReport = def
                                    formState = when (def.kind) {
                                        ReportKind.ORDER_PNL      -> ReportFormState.OrderPnl()
                                        ReportKind.HEALTH_REPORT  -> ReportFormState.HealthReport.defaultCurrentMonth()
                                    }
                                    chatMode = ChatMode.REPORT
                                    modeExpanded = false
                                    // Reclaim vertical space — the report
                                    // panel competes with chat for room, so
                                    // collapse the inbox if it was open.
                                    inboxExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            enabled = false,
                            text    = { Text("Council — coming soon", style = T.meta, color = T.Muted) },
                            onClick = {}
                        )
                    }
                }

                // Send / Queue / Generate
                Button(
                    onClick        = {
                        if (chatMode == ChatMode.REPORT) {
                            when (val fs = formState) {
                                is ReportFormState.OrderPnl -> viewModel.submitReport(
                                    tickers = fs.selectedTickers.toList(),
                                    top5    = fs.top5
                                )
                                is ReportFormState.HealthReport -> viewModel.submitHealthReport(
                                    dateFrom    = fs.dateFrom,
                                    dateTo      = fs.dateTo,
                                    detailDates = fs.detailDates.ifEmpty { null }
                                )
                            }
                        } else {
                            sendMessage()
                        }
                    },
                    enabled        = if (chatMode == ChatMode.REPORT) {
                        !state.isSubmittingReport && when (val fs = formState) {
                            is ReportFormState.OrderPnl     -> fs.selectedTickers.isNotEmpty()
                            is ReportFormState.HealthReport -> !viewModel.hasHealthReportInFlight()
                        }
                    } else
                        inputText.trim().isNotEmpty() && !isSending &&
                                (chatMode == ChatMode.QUERY || !state.quotaExceeded),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = when (chatMode) {
                            ChatMode.OFFLINE -> AmberDash
                            ChatMode.QUERY   -> QueryBlue
                            ChatMode.REPORT  -> ReportPurple
                            else             -> T.Ink
                        },
                        contentColor           = T.Paper,
                        disabledContainerColor = T.Rule,
                        disabledContentColor   = T.Muted
                    ),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier       = Modifier.height(36.dp)
                ) {
                    Text(
                        text       = when (chatMode) {
                            ChatMode.OFFLINE -> "Queue"
                            ChatMode.REPORT  -> if (state.isSubmittingReport) "..." else "Generate"
                            else             -> "Send"
                        },
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Report params panel ──────────────────────────────────────────
// Renders in place of the free-text input when chatMode == REPORT.
// This composable owns only the shared shell (title row, collapse
// toggle, submit error, submitting spinner) — the actual fields differ
// per report and live in OrderPnlParamsBody / HealthReportParamsBody
// below. Adding report #3 means a new *ParamsBody + one more branch in
// the dispatch below, not a change to this shell.
@Composable
private fun ReportParamsPanel(
    report: ReportDefinition,
    formState: ReportFormState,
    onFormStateChange: (ReportFormState) -> Unit,
    isSubmitting: Boolean,
    submitError: String?,
    onDismissError: () -> Unit
) {
    var collapsed by remember(report.kind) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, ReportPurple.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .animateContentSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(text = report.title, style = T.sectionHead, color = ReportPurple)
            Text(
                text     = if (collapsed) "${collapsedSummary(formState)}  ▾" else "▴",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }

        if (collapsed) return@Column

        if (submitError != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(text = submitError, style = T.meta, color = ErrorRed)
                TextButton(onClick = onDismissError, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(text = "Dismiss", fontSize = 10.sp, color = T.Muted)
                }
            }
        }

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (formState) {
                is ReportFormState.OrderPnl -> OrderPnlParamsBody(
                    state    = formState,
                    onChange = onFormStateChange
                )
                is ReportFormState.HealthReport -> HealthReportParamsBody(
                    state    = formState,
                    onChange = onFormStateChange
                )
            }
        }

        if (isSubmitting) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color       = ReportPurple,
                    strokeWidth = 1.5.dp,
                    modifier    = Modifier.size(14.dp)
                )
                Text(text = "Submitting…", style = T.meta, color = T.Muted)
            }
        }
    }
}

// One-line summary shown when the panel is collapsed.
private fun collapsedSummary(formState: ReportFormState): String = when (formState) {
    is ReportFormState.OrderPnl -> {
        val n = formState.selectedTickers.size
        "$n ticker${if (n == 1) "" else "s"} · ${if (formState.top5) "Top 5" else "Full"}"
    }
    is ReportFormState.HealthReport -> {
        val range = if (formState.dateFrom != null || formState.dateTo != null)
            "${formState.dateFrom ?: "…"} → ${formState.dateTo ?: "…"}"
        else "Full history"
        range
    }
}

// ── Order PnL params body ────────────────────────────────────────
@Composable
private fun OrderPnlParamsBody(
    state: ReportFormState.OrderPnl,
    onChange: (ReportFormState.OrderPnl) -> Unit
) {
    var tickerInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text       = "TICKERS",
            fontSize   = 9.sp,
            color      = T.Muted,
            fontWeight = FontWeight.Medium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            state.selectedTickers.forEach { ticker ->
                FilterChip(
                    selected = true,
                    onClick  = { onChange(state.copy(selectedTickers = state.selectedTickers - ticker)) },
                    label    = { Text(text = "$ticker ✕", fontSize = 11.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ReportPurple.copy(alpha = 0.15f),
                        selectedLabelColor     = ReportPurple
                    )
                )
            }
        }
        // Free-text add — validates format only (uppercase letters,
        // 1-6 chars), no backend lookup. Tapping an existing chip
        // removes it (✕ above), so this is the only way to add.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = tickerInput,
                onValueChange = { tickerInput = it.uppercase().filter { c -> c.isLetter() }.take(6) },
                placeholder   = { Text("Add ticker…", style = T.meta, fontStyle = FontStyle.Italic) },
                singleLine    = true,
                textStyle     = T.meta,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ReportPurple,
                    unfocusedBorderColor = ReportPurple.copy(alpha = 0.4f)
                ),
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            OutlinedButton(
                onClick = {
                    if (tickerInput.isNotBlank()) {
                        onChange(state.copy(selectedTickers = state.selectedTickers + tickerInput))
                        tickerInput = ""
                    }
                },
                enabled        = tickerInput.isNotBlank(),
                border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                shape          = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier       = Modifier.height(36.dp)
            ) {
                Text(text = "Add", fontSize = 11.sp, color = ReportPurple)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text       = "MODE",
            fontSize   = 9.sp,
            color      = T.Muted,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(T.Rule.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(3.dp)
        ) {
            listOf(false, true).forEach { isTop5 ->
                val active = state.top5 == isTop5
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChange(state.copy(top5 = isTop5)) }
                        .background(
                            if (active) T.Paper else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = if (isTop5) "Top 5 best/worst" else "Full export",
                        fontSize   = 10.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color      = if (active) T.Ink else T.Muted
                    )
                }
            }
        }
    }
}

// ── Health report params body ────────────────────────────────────
// Disjoint shape from Order PnL — no tickers. Date fields are plain
// text inputs (YYYY-MM-DD) rather than a date-picker dialog for this
// first pass; swap for a real picker later if the typed format proves
// error-prone in practice.
@Composable
private fun HealthReportParamsBody(
    state: ReportFormState.HealthReport,
    onChange: (ReportFormState.HealthReport) -> Unit
) {
    var detailDateInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text       = "DATE RANGE (optional)",
            fontSize   = 9.sp,
            color      = T.Muted,
            fontWeight = FontWeight.Medium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value         = state.dateFrom ?: "",
                onValueChange = { onChange(state.copy(dateFrom = it.ifBlank { null })) },
                placeholder   = { Text("From YYYY-MM-DD", style = T.meta, fontStyle = FontStyle.Italic) },
                singleLine    = true,
                textStyle     = T.meta,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ReportPurple,
                    unfocusedBorderColor = ReportPurple.copy(alpha = 0.4f)
                ),
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            OutlinedTextField(
                value         = state.dateTo ?: "",
                onValueChange = { onChange(state.copy(dateTo = it.ifBlank { null })) },
                placeholder   = { Text("To YYYY-MM-DD", style = T.meta, fontStyle = FontStyle.Italic) },
                singleLine    = true,
                textStyle     = T.meta,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ReportPurple,
                    unfocusedBorderColor = ReportPurple.copy(alpha = 0.4f)
                ),
                modifier      = Modifier.weight(1f).height(48.dp)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text       = "DETAIL DATES (optional — defaults to most recent day)",
            fontSize   = 9.sp,
            color      = T.Muted,
            fontWeight = FontWeight.Medium
        )
        if (state.detailDates.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                state.detailDates.forEach { date ->
                    FilterChip(
                        selected = true,
                        onClick  = { onChange(state.copy(detailDates = state.detailDates - date)) },
                        label    = { Text(text = "$date ✕", fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ReportPurple.copy(alpha = 0.15f),
                            selectedLabelColor     = ReportPurple
                        )
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = detailDateInput,
                onValueChange = { detailDateInput = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                placeholder   = { Text("Add date YYYY-MM-DD…", style = T.meta, fontStyle = FontStyle.Italic) },
                singleLine    = true,
                textStyle     = T.meta,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ReportPurple,
                    unfocusedBorderColor = ReportPurple.copy(alpha = 0.4f)
                ),
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            OutlinedButton(
                onClick = {
                    if (detailDateInput.isNotBlank()) {
                        onChange(state.copy(detailDates = state.detailDates + detailDateInput))
                        detailDateInput = ""
                    }
                },
                enabled        = detailDateInput.isNotBlank(),
                border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                shape          = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier       = Modifier.height(36.dp)
            ) {
                Text(text = "Add", fontSize = 11.sp, color = ReportPurple)
            }
        }
        Text(
            text     = "Output: PDF (xlsx also generated server-side, not yet downloadable separately)",
            fontSize = 9.sp,
            color    = T.Muted
        )
    }
}

// ── Trade sub-tab ───────────────────────────────────────────────
// Lifted from BuddieTradeScreen.kt verbatim (header/own-nav stripped — shell owns those).
@Composable
private fun TradeTabContent(
    viewModel:  BuddieTradeViewModel,
    repository: VitaClawRepository
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(
                    color    = TradeGreen,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        state.error != null && state.trades == null -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = state.error ?: "Unknown error",
                        fontSize  = 13.sp,
                        color     = T.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.load() },
                        shape   = RoundedCornerShape(4.dp)
                    ) {
                        Text("Retry", fontSize = 12.sp, color = T.Ink)
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier       = Modifier.fillMaxSize()
            ) {
                // ── Budget ────────────────────────────
                state.budget?.let { budget ->
                    item { BudgetSection(budget) }
                }

                // ── Tab switcher ──────────────────────
                item {
                    TradeTabRow(
                        selectedTab   = state.selectedTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }

                // ── Candidates first (Buddie's pick) ──
                when (state.selectedTab) {
                    TradeTab.Income -> {
                        state.incomeCandidates?.let { cands ->
                            item { IncomeCandidatesSection(cands) }
                        }
                    }
                    TradeTab.Growth -> {
                        item {
                            GrowthCandidatesSection(state.growthCandidates)
                        }
                    }
                }

                // ── Active trades (unactioned — show buttons) ──
                state.trades?.let { tradesResp ->
                    val trackFilter = if (state.selectedTab == TradeTab.Income)
                        "paper_buy" else "paper_growth"

                    val active = tradesResp.trades.filter {
                        it.status == "active" && (
                                it.tradeType == trackFilter ||
                                        (state.selectedTab == TradeTab.Income && it.tradeType.isBlank())
                                )
                    }
                    if (active.isNotEmpty()) {
                        item {
                            TradeSectionHeader(
                                label = "Awaiting your decision",
                                count = active.size
                            )
                        }
                        items(active) { trade ->
                            ActiveTradeCard(trade = trade, repository = repository)
                            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                        }
                    }

                    // ── Action log (actioned trades — V2 style) ──
                    val actioned = tradesResp.trades.filter {
                        it.status in listOf("executed", "skipped", "verified", "expired") && (
                                it.tradeType == trackFilter ||
                                        (state.selectedTab == TradeTab.Income && it.tradeType.isBlank())
                                )
                    }
                    if (actioned.isNotEmpty()) {
                        item {
                            ActionLogSection(
                                trades     = actioned,
                                trackLabel = if (state.selectedTab == TradeTab.Income)
                                    "Income" else "Growth"
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Observations sub-tab ─────────────────────────────────────────
@Composable
private fun ObservationsTabContent(
    viewModel: ObservationsViewModel
) {
    val state by viewModel.state.collectAsState()

    val dateLabel = remember(state.selectedDate) {
        if (state.selectedDate == LocalDate.now()) "Today · ${formatObsDateLong(state.selectedDate)}"
        else formatObsDateLong(state.selectedDate)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Day navigation ──────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.goToPreviousDay() }, modifier = Modifier.size(32.dp)) {
                Text("‹", fontSize = 18.sp, color = T.Ink)
            }
            Text(text = dateLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = T.Ink)
            IconButton(
                onClick  = { viewModel.goToNextDay() },
                enabled  = !viewModel.isToday(),
                modifier = Modifier.size(32.dp)
            ) {
                Text("›", fontSize = 18.sp, color = if (viewModel.isToday()) T.Rule else T.Ink)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(
                        color       = BuddyGreen,
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text      = state.error ?: "Unknown error",
                        fontSize  = 13.sp,
                        color     = T.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
            state.observations.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(text = "No observations for this day", style = T.meta, color = T.Muted)
                }
            }
            else -> {
                val unratedCount = state.observations.count { it.rating == null }
                val unrated = state.observations.filter { it.rating == null }
                val filtered = if (state.domainFilter == ObsDomainFilter.ALL)
                    unrated
                else
                    unrated.filter { parentDomainOf(it.domain) == state.domainFilter }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // ── Daily summary line ────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = T.screenPadding, vertical = 12.dp)
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    text     = "${state.observations.size} observations",
                                    fontSize = 12.sp,
                                    color    = T.Muted
                                )
                                if (unratedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text       = "$unratedCount unrated",
                                            fontSize   = 10.sp,
                                            color      = ErrorRed,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Text(text = "All reviewed ✓", fontSize = 10.sp, color = TradeGreenFg)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                    }

                    // ── Filter chips ───────────────────────
                    item {
                        LazyRow(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = T.screenPadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                listOf(
                                    ObsDomainFilter.ALL     to "All",
                                    ObsDomainFilter.FINANCE to "Finance",
                                    ObsDomainFilter.HEALTH  to "Health",
                                    ObsDomainFilter.ENERGY  to "Energy",
                                    ObsDomainFilter.CROSS   to "Cross"
                                )
                            ) { (filterValue, label) ->
                                val isSelected = state.domainFilter == filterValue
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) T.Ink else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 0.dp else 0.5.dp,
                                            color = T.Rule,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { viewModel.setFilter(filterValue) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text       = label,
                                        fontSize   = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        color      = if (isSelected) T.Paper else T.Muted
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                    }

                    // ── Observation cards ──────────────────
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text  = if (unratedCount == 0) "All observations reviewed ✓"
                                    else "No unrated observations in this category",
                                    style = T.meta,
                                    color = T.Muted
                                )
                            }
                        }
                    } else {
                        items(filtered) { obs ->
                            Box(modifier = Modifier.padding(horizontal = T.screenPadding)) {
                                ObservationCard(
                                    obs        = obs,
                                    onFeedback = { id, rating -> viewModel.submitFeedback(id, rating) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatObsDateLong(date: LocalDate): String {
    val dayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month   = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dayName ${date.dayOfMonth} $month"
}

// ── Budget section ────────────────────────────────────────────

@Composable
private fun BudgetSection(budget: BuddieBudgetResponse) {
    // Show current month budget first, then prior months
    val current = budget.budgets.firstOrNull { it.month == budget.currentMonth }
    val prior   = budget.budgets.filter { it.month != budget.currentMonth }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text      = "BUDGET",
            fontSize  = 10.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Muted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))

        current?.let { BudgetMonthCard(it, isCurrent = true) }
        prior.forEach { month ->
            Spacer(Modifier.height(6.dp))
            BudgetMonthCard(month, isCurrent = false)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

@Composable
private fun BudgetMonthCard(month: BuddieBudgetMonth, isCurrent: Boolean) {
    val spentPct = if (month.openingGbp > 0)
        (month.spentGbp / month.openingGbp).coerceIn(0.0, 1.0).toFloat()
    else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(
                width = if (isCurrent) 1.dp else 0.5.dp,
                color = if (isCurrent) TradeGreen else T.Rule,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = month.month,
                fontSize  = 12.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Ink
            )
            if (isCurrent) {
                Text(
                    text     = "current",
                    fontSize = 10.sp,
                    color    = TradeGreenFg
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BudgetStat("Remaining", "£${"%.0f".format(month.remainingGbp)}", TradeGreen)
            BudgetStat("Spent",     "£${"%.2f".format(month.spentGbp)}",    T.Ink)
            BudgetStat("Earned",    "£${"%.2f".format(month.incomeEarnedGbp)}", T.Ink)
            BudgetStat("Target",    "£${"%.0f".format(month.incomeTargetGbp)}", T.Muted)
        }
        Spacer(Modifier.height(8.dp))
        // Spend progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(T.Rule, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(spentPct)
                    .height(3.dp)
                    .background(TradeGreen, RoundedCornerShape(2.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text     = "${"%.0f".format(spentPct * 100)}% of £${"%.0f".format(month.openingGbp)} budget used",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
}

@Composable
private fun BudgetStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 9.sp, color = T.Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ── Section header ────────────────────────────────────────────

@Composable
private fun TradeSectionHeader(label: String, count: Int, meta: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text      = label.uppercase(),
            fontSize  = 10.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Muted,
            letterSpacing = 0.8.sp
        )
        Text(
            text     = meta ?: "$count trade${if (count != 1) "s" else ""}",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
}

// ── Active trade card ─────────────────────────────────────────

@Composable
private fun ActiveTradeCard(
    trade:      BuddieTradeItem,
    repository: VitaClawRepository
) {
    var cardStatus by remember(trade.id) { mutableStateOf(trade.status) }
    var isPosting  by remember { mutableStateOf(false) }
    var postError  by remember { mutableStateOf<String?>(null) }
    val scope      = rememberCoroutineScope()

    val windowOpen = remember(trade.expiresAt) {
        val exp = trade.expiresAt
        if (exp.isNullOrBlank()) true   // growth trades have no expiry — always open
        else try {
            !LocalDate.parse(exp, DateTimeFormatter.ISO_LOCAL_DATE)
                .isBefore(LocalDate.now())
        } catch (_: Exception) { true }
    }

    val daysToExDiv = daysUntilTrade(trade.exDivDate)
    val (exDivBg, exDivFg) = when {
        daysToExDiv <= 3L  -> Pair(TradeRedBg,   TradeRedFg)
        daysToExDiv <= 10L -> Pair(TradeAmberBg, TradeAmberFg)
        else               -> Pair(TradeMutedBg, T.Muted)
    }

    fun postFeedback(action: String) {
        scope.launch {
            isPosting = true
            postError = null
            repository.postTradeFeedback(id = trade.id, action = action)
                .fold(
                    onSuccess = { response -> cardStatus = response.status },
                    onFailure = { err ->
                        postError = when (err.message) {
                            "window_closed"    -> "window_closed"
                            "already_recorded" -> "already_recorded"
                            else               -> err.message
                        }
                    }
                )
            isPosting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 12.dp)
    ) {
        // Ticker + status badge
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = trade.ticker,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            when (cardStatus) {
                "executed" -> Box(
                    modifier = Modifier
                        .background(TradeGreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "Executed \u2713",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TradeGreenFg
                    )
                }
                "skipped" -> Box(
                    modifier = Modifier
                        .background(TradeMutedBg, RoundedCornerShape(10.dp))
                        .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "Skipped", fontSize = 10.sp, color = T.Muted)
                }
                else -> Box(
                    modifier = Modifier
                        .background(TradeGreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "active",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TradeGreenFg
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Income-specific: ex-div countdown + payment info
        if (trade.tradeType != "paper_growth") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text     = "Buy before ${formatTradeDateShort(trade.exDivDate)}",
                    fontSize = 12.sp,
                    color    = T.Muted
                )
                Box(
                    modifier = Modifier
                        .background(exDivBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "${daysToExDiv}d",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color      = exDivFg
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${trade.shares?.toInt() ?: 0} shares @ £${"%.2f".format(trade.priceGbp)}",
                    fontSize = 12.sp, color = T.Muted)
                Text("·", fontSize = 12.sp, color = T.Muted)
                Text("Capital: £${"%.2f".format(trade.capitalGbp)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T.Ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Income:", fontSize = 12.sp, color = T.Muted)
                Text("£${"%.2f".format(trade.projectedIncomeGbp)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TradeGreen)
                Text("· pays ${formatTradeDateShort(trade.paymentDate)}",
                    fontSize = 12.sp, color = T.Muted)
            }
        } else {
            // Growth-specific: capital context only
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capital: £${"%.2f".format(trade.capitalGbp)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T.Ink)
                Text("·", fontSize = 12.sp, color = T.Muted)
                Text("Target: +5%", fontSize = 12.sp, color = TradeBlue)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Bottom row — ghost notice + action controls
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "Paper trade \u2014 not executed",
                fontSize  = 10.sp,
                color     = T.Muted,
                fontStyle = FontStyle.Italic
            )
            when {
                cardStatus == "executed" || cardStatus == "skipped" -> { }
                !windowOpen || postError == "window_closed" -> {
                    Text(
                        text      = "Window closed \u2014 ex-div passed",
                        fontSize  = 10.sp,
                        color     = T.Muted,
                        fontStyle = FontStyle.Italic
                    )
                }
                postError == "already_recorded" -> {
                    Text(
                        text     = "Already recorded",
                        fontSize = 10.sp,
                        color    = T.Muted,
                        fontStyle = FontStyle.Italic
                    )
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick        = { postFeedback("executed") },
                            enabled        = !isPosting,
                            shape          = RoundedCornerShape(4.dp),
                            border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(28.dp)
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color       = T.Muted
                                )
                            } else {
                                Text("Bought", fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(
                            onClick        = { postFeedback("skipped") },
                            enabled        = !isPosting,
                            shape          = RoundedCornerShape(4.dp),
                            border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(28.dp)
                        ) {
                            Text("Skipped", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Past trades (collapsed) ───────────────────────────────────

@Composable
private fun ActionLogSection(
    trades:     List<BuddieTradeItem>,
    trackLabel: String
) {
    val bought   = trades.filter { it.status == "executed" || it.status == "verified" }
    val skipped  = trades.filter { it.status == "skipped" }
    val deployed = bought.sumOf { it.capitalGbp.


    toDouble() }
    val projectedIncome = bought.sumOf {
        (it.actualIncomeGbp ?: it.projectedIncomeGbp).toDouble()
    }
    val hasVerified = bought.any { it.actualIncomeGbp != null }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding)
                .padding(top = 12.dp, bottom = 16.dp)
                .background(TradeMutedBg, RoundedCornerShape(10.dp))
                .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR ACTIONS · ${trackLabel.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = T.Muted,
                    letterSpacing = 0.7.sp
                )
                Text(text = "${trades.size} trades", fontSize = 10.sp, color = T.Muted)
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

            // Stat bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionStatItem(
                    value = "${bought.size}",
                    label = "Bought",
                    valueColor = if (bought.isNotEmpty()) TradeGreen else T.Muted
                )
                ActionStatDivider()
                ActionStatItem(
                    value = "${skipped.size}",
                    label = "Skipped",
                    valueColor = T.Ink
                )
                ActionStatDivider()
                ActionStatItem(
                    value = "£${"%.0f".format(deployed)}",
                    label = "Deployed",
                    valueColor = if (deployed > 0) T.Ink else T.Muted
                )
                ActionStatDivider()
                ActionStatItem(
                    value = "£${"%.2f".format(projectedIncome)}",
                    label = if (hasVerified) "Earned" else "Projected",
                    valueColor = if (hasVerified) TradeGreen else T.Muted
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

            // Action rows
            trades.forEachIndexed { idx, trade ->
                ActionLogRow(trade)
                if (idx < trades.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = T.Rule,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionStatItem(value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = valueColor)
        Text(text = label, fontSize = 9.sp, color = T.Muted, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun ActionStatDivider() {
    Box(modifier = Modifier.width(0.5.dp).height(28.dp).background(T.Rule))
}

@Composable
private fun ActionLogRow(trade: BuddieTradeItem) {
    val isBought  = trade.status == "executed" || trade.status == "verified"
    val isExpired = trade.status == "expired"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (isBought) Color(0xFFEAF3DE) else TradeMutedBg,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isBought  -> Icons.Default.Check
                    isExpired -> Icons.Default.Schedule
                    else      -> Icons.Default.Close
                },
                contentDescription = trade.status,
                tint = if (isBought) TradeGreen else T.Muted,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trade.ticker,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isExpired) T.Muted else T.Ink
            )
            val sub = when {
                isBought  -> "£${"%.2f".format(trade.capitalGbp)} · ${
                    if (trade.actualIncomeGbp != null)
                        "£${"%.2f".format(trade.actualIncomeGbp)} earned"
                    else
                        "£${"%.2f".format(trade.projectedIncomeGbp)} projected"
                }"
                isExpired -> "Window expired"
                else      -> "Passed"
            }
            Text(text = sub, fontSize = 10.sp, color = T.Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTradeDateShort(trade.tradeDate),
                fontSize = 10.sp,
                color = T.Muted
            )
            if (trade.tradeType == "paper_growth") {
                Text(text = "growth", fontSize = 9.sp, color = TradeBlue)
            }
        }
    }
}

// ── Candidates section ────────────────────────────────────────

@Composable
private fun IncomeCandidatesSection(cands: BuddieCandidatesResponse) {
    var excludedExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "CANDIDATES · ${cands.month}",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${cands.passedCount} passed · ${cands.totalEvaluated} evaluated",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Selected candidate first — prominent
        val selected  = cands.candidates.firstOrNull { it.selected }
        val remaining = cands.candidates.filter { !it.selected }

        selected?.let {
            CandidateCard(candidate = it, isSelected = true)
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        remaining.forEach { cand ->
            CandidateCard(candidate = cand, isSelected = false)
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        // Excluded — collapsible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { excludedExpanded = !excludedExpanded }
                .padding(horizontal = T.screenPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = "EXCLUDED",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${cands.excludedCount} tickers · ${if (excludedExpanded) "▲" else "▼"}",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }

        if (excludedExpanded) {
            cands.excluded.forEach { item ->
                ExcludedRow(item)
                HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "Generated ${cands.generatedAt}",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier.padding(horizontal = T.screenPadding).padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun CandidateCard(candidate: BuddieCandidateItem, isSelected: Boolean) {
    val (exDivBg, exDivFg) = when {
        candidate.daysToExDiv <= 3  -> Pair(TradeRedBg,   TradeRedFg)
        candidate.daysToExDiv <= 10 -> Pair(TradeAmberBg, TradeAmberFg)
        else                        -> Pair(TradeMutedBg, T.Muted)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(Color(0xFFF8FDF5))
                else Modifier
            )
            .padding(horizontal = T.screenPadding, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text      = candidate.ticker,
                    fontSize  = if (isSelected) 16.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    color     = T.Ink
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .background(TradeGreenBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text      = "Selected",
                            fontSize  = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color     = TradeGreenFg
                        )
                    }
                }
                if (candidate.confirmed) {
                    Box(
                        modifier = Modifier
                            .background(TradeMutedBg, RoundedCornerShape(10.dp))
                            .border(0.5.dp, T.Rule, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("confirmed", fontSize = 9.sp, color = T.Muted)
                    }
                }
            }
            // Ex-div countdown badge
            Box(
                modifier = Modifier
                    .background(exDivBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text      = "${candidate.daysToExDiv}d",
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color     = exDivFg
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capital:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "£${"%.2f".format(candidate.capitalGbp)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = T.Ink
                )
                Text("·", fontSize = 11.sp, color = T.Muted)
                Text("Income:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "£${"%.2f".format(candidate.projectedIncome)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TradeGreen
                )
            }
            Box(
                modifier = Modifier
                    .background(TradeMutedBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = "${"%.1f".format(candidate.annualYield)}% yield",
                    fontSize = 10.sp,
                    color = T.Muted
                )
            }
        }

        Text(
            text     = "Ex-div ${formatTradeDateShort(candidate.exDivDate)}",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ExcludedRow(item: BuddieExcludedItem) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Text(
            text      = item.ticker,
            fontSize  = 12.sp,
            fontWeight = FontWeight.Medium,
            color     = T.Ink,
            modifier  = Modifier.width(56.dp)
        )
        Text(
            text     = item.reason,
            fontSize = 11.sp,
            color    = T.Muted,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

// ── Tab row ───────────────────────────────────────────────────

@Composable
private fun TradeTabRow(
    selectedTab:   TradeTab,
    onTabSelected: (TradeTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding)
            .padding(top = 14.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TradeTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val label = when (tab) {
                TradeTab.Income -> "Income"
                TradeTab.Growth -> "Growth"
            }
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) TradeMutedBg else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = if (isSelected) T.Ink else T.Rule,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text       = label,
                    fontSize   = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color      = if (isSelected) T.Ink else T.Muted
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
}

// ── Growth candidates section ─────────────────────────────────

@Composable
private fun GrowthCandidatesSection(cands: BuddieGrowthCandidatesResponse?) {
    if (cands == null) {
        // Empty state — agent hasn't run yet
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text      = "GROWTH · CANDIDATES",
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = T.Muted,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TradeMutedBg, RoundedCornerShape(8.dp))
                    .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = "Buddie's first growth analysis runs tonight at 09:00",
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color     = T.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "25 growth tickers will be evaluated. The selected candidate will appear here in the morning.",
                        fontSize  = 11.sp,
                        color     = T.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        return
    }

    var excludedExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.screenPadding)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text          = "GROWTH · ${cands.month}",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Medium,
                color         = T.Muted,
                letterSpacing = 0.8.sp
            )
            Text(
                text     = "${cands.passedCount} passed · ${cands.totalEvaluated} evaluated",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // Stale warning
        if (cands.isStale && cands.staleDays > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TradeAmberBg)
                    .padding(horizontal = T.screenPadding, vertical = 6.dp)
            ) {
                Text(
                    text     = "Data is ${cands.staleDays}d old — agent may not have run",
                    fontSize = 10.sp,
                    color    = TradeAmberFg
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        val selected  = cands.candidates.firstOrNull { it.selected }
        val remaining = cands.candidates.filter { !it.selected }

        selected?.let {
            GrowthCandidateCard(candidate = it, isSelected = true)
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        if (remaining.isNotEmpty()) {
            var othersExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { othersExpanded = !othersExpanded }
                    .padding(horizontal = T.screenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = "OTHER CANDIDATES",
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Medium,
                    color         = T.Muted,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text     = "${remaining.size} · ${if (othersExpanded) "▲" else "▼"}",
                    fontSize = 10.sp,
                    color    = T.Muted
                )
            }
            if (othersExpanded) {
                remaining.forEach { cand ->
                    GrowthCandidateCard(candidate = cand, isSelected = false)
                    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text     = "Generated ${cands.generatedAt}",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier
                .padding(horizontal = T.screenPadding)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun GrowthCandidateCard(
    candidate:  BuddieGrowthCandidateItem,
    isSelected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(Color(0xFFF0F6FC))
                else Modifier
            )
            .padding(horizontal = T.screenPadding, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = candidate.ticker,
                    fontSize   = if (isSelected) 16.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = T.Ink
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .background(TradeBlueBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = "Selected",
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color      = TradeBlueFg
                        )
                    }
                }
                if (candidate.tradeApproval == "required") {
                    Box(
                        modifier = Modifier
                            .background(TradeAmberBg, RoundedCornerShape(10.dp))
                            .border(0.5.dp, TradeAmberFg.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text     = "⚠ approval needed",
                            fontSize = 9.sp,
                            color    = TradeAmberFg
                        )
                    }
                }
            }
            // Score badge
            Box(
                modifier = Modifier
                    .background(TradeMutedBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text     = "score ${"%.1f".format(candidate.score)}",
                    fontSize = 10.sp,
                    color    = T.Muted
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Return:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "+${"%.1f".format(candidate.capitalReturnPct)}%",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TradeBlue
                )
                Text("·", fontSize = 11.sp, color = T.Muted)
                Text("RSI:", fontSize = 11.sp, color = T.Muted)
                Text(
                    "${"%.0f".format(candidate.rsi14)}",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = when {
                        candidate.rsi14 < 40 -> TradeBlue
                        candidate.rsi14 > 60 -> TradeAmberFg
                        else                 -> T.Ink
                    }
                )
            }
            Box(
                modifier = Modifier
                    .background(TradeMutedBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text     = "${"%.0f".format(candidate.priceVs52wHigh)}% of 52w high",
                    fontSize = 10.sp,
                    color    = T.Muted
                )
            }
        }

        Text(
            text     = "${candidate.holdingDays}d held · ${candidate.orderCount} orders",
            fontSize = 10.sp,
            color    = T.Muted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ── Date helpers ──────────────────────────────────────────────

private fun daysUntilTrade(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        val target = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        ChronoUnit.DAYS.between(LocalDate.now(), target).coerceAtLeast(0)
    } catch (_: Exception) { 0L }
}

private fun formatTradeDateShort(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    return try {
        val d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${d.dayOfMonth} $month"
    } catch (_: Exception) { dateStr }
}

// ── Observation card ──────────────────────────────────────────
// observation_type = "observation" → solid 0.5dp border (T.Rule)
// observation_type = "hypothesis"  → dashed amber border + "hypothesis" label
// domain_memory.parent_domain → badge colour (finance=blue, health=green, energy=amber)
// domain_memory null or v0·0obs → muted grey badge
// claimId = action stripped of "OBSERVE: " prefix — shown as muted label below content
// confidence_calibrated null → show "—"
@Composable
private fun ObservationCard(
    obs: ObservationItem,
    onFeedback: (Int, String) -> Unit
) {
    val domainIcon = when (obs.domain) {
        "hrv"                              -> "❤️"
        "spo2"                             -> "🫁"
        "strain"                           -> "💪"
        "recovery"                         -> "🔋"
        "finance", "income", "portfolio",
        "capital", "income_performance",
        "income_signal",
        "portfolio_health",
        "portfolio_performance"            -> "📈"
        "energy", "solar", "ev",
        "eddi", "grid", "summary"          -> "⚡"
        else                               -> "●"
    }

    val isHypothesis = obs.observationType == "hypothesis"

    // Border: solid for observation, dashed amber for hypothesis
    // Compose doesn't support native dashed borders — simulate with amber colour at 0.5dp
    // and a "hypothesis" label. Full dashed path requires Canvas; amber outline is sufficient signal.
    val borderColor = if (isHypothesis) AmberDash else T.Rule
    val borderWidth = if (isHypothesis) 1.dp else 0.5.dp

    // Confidence badge colours
    val (confBg, confFg) = when {
        obs.confidence >= 0.8 -> ConfGreenBg to ConfGreen
        obs.confidence >= 0.6 -> ConfAmberBg to ConfAmber
        else                  -> ConfRedBg   to ConfRed
    }

    // domain_memory badge — parent_domain drives colour
    val memory = obs.domainMemory
    val isNewDomain = memory == null || (memory.version == 0 && memory.totalObservations == 0)
    val (memBg, memFg) = when (memory?.parentDomain) {
        "finance" -> DomainFinanceBg to DomainFinanceFg
        "health"  -> DomainHealthBg  to DomainHealthFg
        "energy"  -> DomainEnergyBg  to DomainEnergyFg
        else      -> DomainNewBg     to DomainNewFg
    }
    val memLabel = if (memory != null)
        "v${memory.version} · ${memory.totalObservations} obs"
    else
        "v0 · 0 obs"

    // calibrated confidence display
    val calDisplay = obs.confidenceCalibrated?.let { "%.2f".format(it) } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable { /* consume click — prevent collapse toggle */ }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Row 1: domain icon + name  |  domain_memory badge ─
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Domain icon + name
            Text(
                text     = "$domainIcon ${obs.domain}",
                fontSize = 12.sp,
                color    = T.Muted
            )
            // domain_memory badge: "v3 · 37 obs"
            Box(
                modifier = Modifier
                    .background(memBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = memLabel,
                    fontSize   = 10.sp,
                    color      = if (isNewDomain) memFg else memFg,
                    fontWeight = if (isNewDomain) FontWeight.Normal else FontWeight.Medium
                )
            }
        }

        // ── Hypothesis label (amber, only for hypothesis type) ─
        if (isHypothesis) {
            Text(
                text      = "hypothesis",
                fontSize  = 10.sp,
                color     = AmberDash,
                fontStyle = FontStyle.Italic
            )
        }

        // ── Content — serif ───────────────────────────────────
        Text(
            text       = obs.content,
            fontFamily = T.Serif,
            fontSize   = 13.sp,
            color      = T.Ink,
            lineHeight = 19.sp
        )

        // ── Claim ID — stripped "OBSERVE: " prefix ────────────
        if (obs.claimId.isNotBlank()) {
            Text(
                text     = obs.claimId,
                fontSize = 10.sp,
                color    = T.Muted,
                fontStyle = FontStyle.Italic
            )
        }

        // ── Row 2: confidence raw  |  calibrated ─────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Raw confidence badge
            Box(
                modifier = Modifier
                    .background(confBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = "${"%.0f".format(obs.confidence * 100)}%",
                    fontSize   = 11.sp,
                    color      = confFg,
                    fontWeight = FontWeight.Medium
                )
            }
            // Calibrated confidence
            Text(
                text     = "cal: $calDisplay",
                fontSize = 10.sp,
                color    = T.Muted
            )
        }

        // ── Feedback buttons ──────────────────────────────────
        // hypothesis button set deferred — backend P4 ratings not ready yet
        // Both types show same buttons for now
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "useful"    to "👍 Useful",
                "wrong"     to "👎 Wrong",
                "important" to "⭐ Important"
            ).forEach { (value, label) ->
                val isSelected = obs.rating == value
                OutlinedButton(
                    onClick        = { onFeedback(obs.id, value) },
                    border         = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (isSelected) 1.dp else 0.5.dp
                    ),
                    colors         = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) T.Ink else Color.Transparent,
                        contentColor   = if (isSelected) T.Paper else T.Muted
                    ),
                    shape          = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier       = Modifier
                        .weight(1f)
                        .height(30.dp)
                ) {
                    Text(text = label, fontSize = 10.sp)
                }
            }
        }
    }
}

// ── Offline Inbox ─────────────────────────────────────────────
@Composable
private fun OfflineInbox(
    jobs: List<PendingOfflineItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDownload: (PendingOfflineItem) -> Unit,
    onShare: (PendingOfflineItem) -> Unit,
    onDismiss: (PendingOfflineItem) -> Unit,
    onClearAll: () -> Unit
) {
    val doneCount  = jobs.count { it.status == "done" }
    val errorCount = jobs.count { it.status == "error" }
    // Only clear jobs that are actually finished — never silently drop
    // something still generating just because "Clear all" was tapped.
    val clearableCount = jobs.count { it.status != "pending" && it.status != "queued" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E7))
            .animateContentSize()
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = T.screenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(text = "Offline Inbox", style = T.sectionHead, color = T.Ink)
                Box(
                    modifier = Modifier
                        .background(AmberDash, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "$doneCount", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (errorCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(ErrorRed, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "$errorCount err", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (expanded && clearableCount > 0) {
                    Text(
                        text     = "Clear all",
                        fontSize = 11.sp,
                        color    = T.Muted,
                        modifier = Modifier.clickable(onClick = onClearAll)
                    )
                }
                Text(
                    text     = if (expanded) "▲" else "▼",
                    style    = T.meta,
                    color    = T.Muted,
                    modifier = Modifier.clickable(onClick = onToggle)
                )
            }
        }
        if (expanded) {
            HorizontalDivider(thickness = T.ruleThickness, color = AmberDash.copy(alpha = 0.3f))
            // Height-capped + scrollable — was an unbounded Column.forEach,
            // which is why the list grew past the screen with no way to
            // scroll through older entries.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(jobs, key = { it.jobId }) { job ->
                    OfflineJobRow(
                        job        = job,
                        onDownload = { onDownload(job) },
                        onShare    = { onShare(job) },
                        onDismiss  = { onDismiss(job) }
                    )
                    HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                }
            }
        }
    }
}

// ── Job display label ────────────────────────────────────────
// Server-side report jobs leave message blank (confirmed 2026-06-25 —
// every order_pnl_report entry in /chat/offline/pending has message: "").
// Fall back to response, which report jobs always populate with a real
// summary ("PnL report ready -- 196 order rows..."); generic fallback
// only if somehow both are empty.
private fun jobDisplayLabel(job: PendingOfflineItem): String = when {
    job.message.isNotBlank()  -> job.message
    job.response.isNotBlank() -> job.response
    else                       -> "Offline job — ${job.jobId}"
}

// ── Offline job row ───────────────────────────────────────────
// Three states per job:
//   pending           → spinner, no actions yet
//   done, no file     → legacy text job, single "Save" button (download+share in one tap)
//   done, hasFile     → report job, two buttons: "Download" (silent) + "Share" (explicit)
//   error             → "Dismiss" only
@Composable
private fun OfflineJobRow(
    job: PendingOfflineItem,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val isError   = job.status == "error"
    val isPending = job.status == "pending" || job.status == "queued"
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = jobDisplayLabel(job),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = if (isError) T.Muted else T.Ink,
                maxLines   = 1
            )
            Text(
                text  = when {
                    isError   -> "Failed"
                    isPending -> "Generating…"
                    else      -> job.provenance
                },
                style = T.meta,
                color = if (isError) ErrorRed else T.Muted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            isError -> {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(text = "Dismiss", fontSize = 11.sp, color = T.Muted)
                }
            }
            isPending -> {
                CircularProgressIndicator(
                    color       = T.Muted,
                    strokeWidth = 1.5.dp,
                    modifier    = Modifier.size(16.dp)
                )
            }
            job.hasFile -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick        = onDownload,
                        border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier       = Modifier.height(32.dp)
                    ) {
                        Text(text = "Download", fontSize = 11.sp, color = T.Ink)
                    }
                    Button(
                        onClick        = onShare,
                        colors         = ButtonDefaults.buttonColors(containerColor = T.Ink, contentColor = T.Paper),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier       = Modifier.height(32.dp)
                    ) {
                        Text(text = "Share", fontSize = 11.sp)
                    }
                }
            }
            else -> {
                Button(
                    onClick        = onDownload,
                    colors         = ButtonDefaults.buttonColors(containerColor = T.Ink, contentColor = T.Paper),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier       = Modifier.height(32.dp)
                ) {
                    Text(text = "Save", fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Download job as .txt ──────────────────────────────────────
private fun downloadJobAsText(context: Context, job: PendingOfflineItem) {
    val content = buildString {
        appendLine("OFFLINE RESPONSE — VitaClaw")
        appendLine("Job ID: ${job.jobId}")
        appendLine("Completed: ${job.completedAt}")
        appendLine("Provenance: ${job.provenance}")
        appendLine()
        appendLine("QUERY:")
        appendLine(job.message)
        appendLine()
        appendLine("RESPONSE:")
        appendLine(job.response)
    }
    try {
        val file = java.io.File(context.getExternalFilesDir(null), "vitaclaw_offline_${job.jobId}.txt")
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VitaClaw: ${job.message.take(40)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save offline response"))
    } catch (e: Exception) { e.printStackTrace() }
}

// ── Read text file ────────────────────────────────────────────
private fun readTextFile(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        } ?: ""
    } catch (e: Exception) { "" }
}

// ── Report file download (binary file — .xlsx or .pdf depending on
//    report kind) ──────────────────────────────────────────────
// Saves to the public Downloads folder so the file shows up in the
// phone's Files app / Downloads, not just inside VitaNest.
//
// minSdk is 26 (confirmed against build.gradle — NOT 29 as assumed
// earlier in this session). MediaStore.Downloads requires API 29+, so
// API 26-28 needs a real legacy branch: write into the public Downloads
// directory directly via java.io.File, then hand the result to
// FileProvider for sharing — same FileProvider mechanism downloadJobAsText
// already uses, just pointed at Environment.DIRECTORY_DOWNLOADS instead
// of getExternalFilesDir. Requires res/xml/file_paths.xml to declare an
// <external-path name="downloads" path="Download/" /> entry — added
// 2026-06-27, FileProvider throws IllegalArgumentException without it.

private fun isHealthReportJob(job: PendingOfflineItem): Boolean =
    job.message.startsWith("Health Report") ||
            job.response.contains("Health report", ignoreCase = true)

private fun reportFileExtension(job: PendingOfflineItem): String =
    if (isHealthReportJob(job)) "pdf" else "xlsx"

private fun reportMimeType(job: PendingOfflineItem): String =
    if (isHealthReportJob(job)) "application/pdf"
    else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private fun reportFileName(job: PendingOfflineItem): String {
    // No file_path string is returned by /chat/offline/pending (confirmed
    // 2026-06-25 — only has_file: Boolean is present). Name from job_id
    // instead; the actual bytes come from GET .../job/{job_id}/file
    // regardless of what we call the saved local copy.
    return "vitaclaw_report_${job.jobId}.${reportFileExtension(job)}"
}

private suspend fun saveReportFileToDownloads(
    context: Context,
    repository: VitaClawRepository,
    job: PendingOfflineItem
): Uri? {
    val bytesResult = repository.downloadOfflineJobFile(job.jobId)
    val bytes = bytesResult.getOrNull() ?: return null

    val fileName = reportFileName(job)
    val mimeType = reportMimeType(job)

    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // API 29+ — MediaStore.Downloads, scoped storage.
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver  = context.contentResolver
            val targetUri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            resolver.openOutputStream(targetUri)?.use { out -> out.write(bytes) }

            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(targetUri, contentValues, null, null)

            targetUri
        } else {
            // API 26-28 — no MediaStore.Downloads. Write directly into the
            // public Downloads directory, then wrap via FileProvider (the
            // same provider/authority downloadJobAsText already uses) so
            // the result is a content:// Uri usable by Download/Share —
            // a raw file:// Uri would violate FileUriExposedException on
            // these API levels when handed to another app via an Intent.
            @Suppress("DEPRECATION")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            file.writeBytes(bytes)

            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// "Download" — silent save, no share sheet. Toast confirms success/failure
// since there's otherwise no feedback the file actually landed.
private fun downloadJobFile(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    repository: VitaClawRepository,
    job: PendingOfflineItem
) {
    scope.launch {
        val uri = saveReportFileToDownloads(context, repository, job)
        val message = if (uri != null) "Saved to Downloads — ${reportFileName(job)}"
        else "Couldn't save report — try again"
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}

// "Share" — saves (if not already reachable) then opens the share sheet
// with the file attached, same UX shape as the existing text-job "Save".
private fun shareJobFile(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    repository: VitaClawRepository,
    job: PendingOfflineItem
) {
    scope.launch {
        val uri = saveReportFileToDownloads(context, repository, job)
        if (uri == null) {
            android.widget.Toast.makeText(
                context, "Couldn't prepare report for sharing — try again", android.widget.Toast.LENGTH_LONG
            ).show()
            return@launch
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = reportMimeType(job)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, job.message.take(60))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }
}

// ── Brief card ────────────────────────────────────────────────
@Composable
private fun BriefCard(structured: BriefStructured) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val recoveryScore  = structured.recoveryScore
        val recoveryStatus = structured.recoveryStatus
        val recoveryTrend  = structured.recoveryTrend
        if (recoveryScore != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    recoveryScore >= 67f -> BuddyGreen
                    recoveryScore >= 34f -> Color(0xFFD4A017)
                    else                 -> ErrorRed
                }
                Text(
                    text       = "${recoveryScore.toInt()}% $recoveryStatus",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = statusColor
                )
                recoveryTrend?.let { Text(text = it, style = T.meta, color = T.Muted) }
            }
            val hrv = structured.hrvMs
            val rhr = structured.rhrBpm
            if (hrv != null || rhr != null) {
                Text(
                    text  = listOfNotNull(
                        hrv?.let { "HRV ${"%.0f".format(it)}ms" },
                        rhr?.let { "RHR ${it.toInt()}bpm" }
                    ).joinToString("  ·  "),
                    style = T.meta, color = T.Muted
                )
            }
        }
        HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        val portVal = structured.portfolioValueGbp
        val pnl     = structured.pnlGbp
        val pnlPct  = structured.pnlPct
        if (portVal != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(text = "£${"%.0f".format(portVal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.Ink)
                if (pnl != null && pnlPct != null) {
                    val c = if (pnl >= 0) BuddyGreen else ErrorRed
                    Text(
                        text  = "${if (pnl >= 0) "+" else ""}£${"%.0f".format(pnl)} (${"%.1f".format(pnlPct)}%)",
                        style = T.meta, color = c
                    )
                }
            }
        }
        val gap    = structured.incomeGapGbp
        val target = structured.incomeTargetGbp
        if (gap != null && target != null) {
            Text(
                text  = "£${"%.0f".format(gap)} needed for £${target.toInt()}/month target",
                style = T.meta, color = T.Muted
            )
        }
        structured.exDivAlert?.let { alert ->
            if (alert.isAlert) {
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "⚠ ${alert.tickers} ex-div in ${alert.daysAway}d",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = AmberDash
                    )
                    Text(text = alert.date, style = T.meta, color = T.Muted)
                }
            }
        }
        structured.weather?.let { w ->
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Text(text = "☁ ${w.substringAfter(":").trim()}", style = T.meta, color = T.Muted)
        }
    }
}

// ── Buddy bubble ──────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BuddyBubble(
    text: String,
    provenance: String  = "",
    elapsedMs: Long     = 0L,
    isLoading: Boolean  = false,
    isQueued: Boolean   = false,
    timeDisplay: String = "",
    queryProvenance: BuddieQueryProvenance? = null,
    reportJob: PendingOfflineItem? = null,
    onDownloadReport: (PendingOfflineItem) -> Unit = {},
    onShareReport: (PendingOfflineItem) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val haptic    = LocalHapticFeedback.current
    var copied    by remember { mutableStateOf(false) }
    var detailExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    val isOutOfDomain = queryProvenance?.boundary == "OUT_OF_DOMAIN"
    val bubbleColor   = if (isOutOfDomain) Color(0xFF6B7280) else BuddyGreen

    Column(modifier = Modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .background(
                    bubbleColor,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
                )
                .combinedClickable(
                    onClick      = { },
                    onLongClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (isLoading) {
                Text(text = "thinking…", style = T.meta,
                    color = T.Paper.copy(alpha = 0.7f), fontStyle = FontStyle.Italic)
            } else {
                val lines = parseAnswerLines(text)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    lines.forEach { line ->
                        when (line) {
                            is AnswerLine.Header  -> Text(line.text, fontFamily = T.Serif,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = T.Paper)
                            is AnswerLine.SubHead -> Text(line.text,
                                fontWeight = FontWeight.Medium, fontSize = 12.sp, color = T.Paper)
                            is AnswerLine.DataRow -> Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(line.label, fontSize = 11.sp,
                                    color = T.Paper.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                                Text(line.value, fontSize = 11.sp,
                                    color = T.Paper, fontWeight = FontWeight.Bold)
                            }
                            is AnswerLine.Plain   -> Text(line.text,
                                fontSize = 12.sp, color = T.Paper, lineHeight = 17.sp)
                            is AnswerLine.Divider -> HorizontalDivider(
                                color = T.Paper.copy(alpha = 0.3f), thickness = 0.5.dp)
                            is AnswerLine.Empty   -> Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
        if (copied) {
            Text(
                text     = "Copied",
                fontSize = 10.sp,
                color    = T.Muted,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
            )
        }
        if (!isLoading) {
            val parts = listOfNotNull(
                provenance.ifBlank { null },
                if (elapsedMs > 0) "${elapsedMs}ms" else null,
                timeDisplay.ifBlank { null }
            )
            if (parts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = parts.joinToString(" · "), style = T.meta, color = T.Muted, fontSize = 10.sp)
            }
        }
        if (reportJob != null && reportJob.hasFile) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick        = { onDownloadReport(reportJob) },
                    border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier       = Modifier.height(32.dp)
                ) {
                    Text(text = "Download", fontSize = 11.sp, color = T.Ink)
                }
                Button(
                    onClick        = { onShareReport(reportJob) },
                    colors         = ButtonDefaults.buttonColors(containerColor = T.Ink, contentColor = T.Paper),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier       = Modifier.height(32.dp)
                ) {
                    Text(text = "Share", fontSize = 11.sp)
                }
            }
        }
        if (!isLoading && queryProvenance != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier
                    .clickable(onClick = { detailExpanded = !detailExpanded }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                queryProvenance.confidence.ifBlank { null }?.let { conf ->
                    Box(
                        modifier = Modifier
                            .background(T.Rule, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(text = conf, fontSize = 9.sp, color = T.Muted)
                    }
                }
                Text(
                    text     = if (detailExpanded) "How was this answered ▲" else "How was this answered ▼",
                    fontSize = 10.sp,
                    color    = T.Muted
                )
            }
            if (detailExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Color(0xFFF5F3EE), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (queryProvenance.tables.isNotEmpty()) {
                        Text(
                            text = "Tables: ${queryProvenance.tables.joinToString(", ")}",
                            fontSize = 10.sp, color = T.Muted
                        )
                    }
                    if (queryProvenance.totalLatencyMs > 0) {
                        Text(text = "Total: ${queryProvenance.totalLatencyMs}ms", fontSize = 10.sp, color = T.Muted)
                    }
                }
            }
        }
    }
}

// ── User bubble ───────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(text: String, timeDisplay: String = "") {
    val clipboard = LocalClipboardManager.current
    val haptic    = LocalHapticFeedback.current
    var copied    by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }

    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp,
        bottomEnd = 12.dp, bottomStart = 12.dp)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(Color.White, shape)
                .border(0.5.dp, T.Rule, shape)
                .combinedClickable(
                    onClick     = { },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(text = text, fontSize = 13.sp, color = T.Ink, lineHeight = 18.sp)
        }
        if (copied) {
            Text(
                text     = "Copied",
                fontSize = 10.sp,
                color    = T.Muted,
                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
            )
        } else if (timeDisplay.isNotEmpty()) {
            Text(text = timeDisplay, style = T.meta, color = T.Muted,
                fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── System card ───────────────────────────────────────────────
@Composable
private fun SystemCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(text = text, style = T.meta, color = T.Muted)
    }
}

// ── Intent tile ───────────────────────────────────────────────
@Composable
private fun IntentTile(label: String, onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White)
            .border(0.5.dp, T.Rule, shape)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = T.Ink)
    }
}

// ── Expand chevron ────────────────────────────────────────────
@Composable
private fun IntentExpandChip(expanded: Boolean, count: Int, onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(T.Paper)
            .border(0.5.dp, BuddyGreen, shape)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text       = if (expanded) "Show less ▲" else "+$count more ▼",
            fontSize   = 12.sp,
            color      = BuddyGreen,
            fontWeight = FontWeight.Medium
        )
    }
}