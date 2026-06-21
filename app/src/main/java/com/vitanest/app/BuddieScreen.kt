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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.vitanest.app.data.remote.BuddieQueryProvenance
import com.vitanest.app.data.remote.ObservationItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch
import com.vitanest.app.data.repository.VitaClawRepository
import java.io.BufferedReader
import java.io.InputStreamReader

private enum class ChatMode { AUTO, OFFLINE, QUERY }
private enum class BuddieSubTab { CHAT, TRADE, OBSERVATIONS }

private val BuddyGreen  = Color(0xFF2D6A4F)
private val AmberDash   = Color(0xFFF59E0B)
private val QueryBlue   = Color(0xFF1D4ED8)
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

@Composable
fun BuddieScreen(
    navController: NavController,
    viewModel:     BuddieViewModel,
    repository:    VitaClawRepository = VitaClawRepository()
) {
    val state by viewModel.state.collectAsState()
    var selectedSubTab by remember { mutableStateOf(BuddieSubTab.CHAT) }

    LaunchedEffect(Unit) { viewModel.initialise() }

    // Red-dot source data — cheap derivation from already-loaded state.
    // Trade dot: any trade awaiting Bought/Skipped decision this month.
    // Observations dot: any observation not yet rated useful/wrong/important.
    val tradeHasUnactioned   = false // wired in stage 3 once BuddieTradeViewModel state is hoisted here
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
                    BuddieSubTab.TRADE         -> PlaceholderTabContent("Trade — coming in stage 3")
                    BuddieSubTab.OBSERVATIONS  -> PlaceholderTabContent("Observations — coming in stage 4")
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
                    downloadJobAsText(context, job)
                    viewModel.ackOfflineJob(job.jobId)
                },
                onDismiss  = { job -> viewModel.ackOfflineJob(job.jobId) }
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
                        "buddy" -> BuddyBubble(
                            text        = b.text,
                            provenance  = b.provenance,
                            elapsedMs   = b.elapsedMs,
                            isLoading   = b.isLoading,
                            isQueued    = b.isQueued,
                            timeDisplay = b.timeDisplay,
                            queryProvenance = b.queryProvenance
                        )
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

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
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
                                else             -> "Auto"
                            },
                            fontSize = 11.sp,
                            color    = when (chatMode) {
                                ChatMode.OFFLINE -> AmberDash
                                ChatMode.QUERY   -> QueryBlue
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
                        DropdownMenuItem(
                            enabled = false,
                            text    = { Text("Council — coming soon", style = T.meta, color = T.Muted) },
                            onClick = {}
                        )
                    }
                }

                // Send / Queue
                Button(
                    onClick        = { sendMessage() },
                    enabled        = inputText.trim().isNotEmpty() && !isSending &&
                            (chatMode == ChatMode.QUERY || !state.quotaExceeded),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = when (chatMode) {
                            ChatMode.OFFLINE -> AmberDash
                            ChatMode.QUERY   -> QueryBlue
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
                        text       = if (chatMode == ChatMode.OFFLINE) "Queue" else "Send",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
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
    onDismiss: (PendingOfflineItem) -> Unit
) {
    val doneCount  = jobs.count { it.status == "done" }
    val errorCount = jobs.count { it.status == "error" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E7))
            .clickable(onClick = onToggle)
            .animateContentSize()
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
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
            Text(text = if (expanded) "▲" else "▼", style = T.meta, color = T.Muted)
        }
        if (expanded) {
            HorizontalDivider(thickness = T.ruleThickness, color = AmberDash.copy(alpha = 0.3f))
            jobs.forEach { job ->
                OfflineJobRow(job = job, onDownload = { onDownload(job) }, onDismiss = { onDismiss(job) })
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            }
        }
    }
}

// ── Offline job row ───────────────────────────────────────────
@Composable
private fun OfflineJobRow(
    job: PendingOfflineItem,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val isError = job.status == "error"
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = job.message,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = if (isError) T.Muted else T.Ink,
                maxLines   = 1
            )
            Text(
                text  = if (isError) "Failed" else job.provenance,
                style = T.meta,
                color = if (isError) ErrorRed else T.Muted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isError) {
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(text = "Dismiss", fontSize = 11.sp, color = T.Muted)
            }
        } else {
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
    queryProvenance: BuddieQueryProvenance? = null
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