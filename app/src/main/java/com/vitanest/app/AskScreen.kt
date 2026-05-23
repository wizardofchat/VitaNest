package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// AskScreen — Buddie chat, offline inbox, observations, file upload, clear chat ☘️
// Layout: Header → Quota → Offline Inbox → Observations → Input → Tiles → Chat
// Updated: ObservationCard shows domain_memory (v · k badge), confidence_calibrated,
//          claim_id (stripped OBSERVE: prefix), observation_type (solid/dashed border)

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.vitanest.app.data.remote.ObservationItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch
import com.vitanest.app.data.remote.PaperTradeResponse
import com.vitanest.app.data.remote.PaperTradeSelection
import com.vitanest.app.data.repository.VitaClawRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.BufferedReader
import java.io.InputStreamReader

private enum class ChatMode { AUTO, OFFLINE }

private val BuddyGreen  = Color(0xFF2D6A4F)
private val AmberDash   = Color(0xFFF59E0B)
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
fun AskScreen(
    navController: NavController,
    viewModel:     BuddieViewModel,
    repository:    VitaClawRepository = VitaClawRepository()
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
    var obsExpanded        by remember { mutableStateOf(false) }
    var paperTradeExpanded by remember { mutableStateOf(false) }
    var isSending     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.initialise() }

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
        viewModel.sendMessage(inputText, chatMode == ChatMode.OFFLINE)
        inputText = ""
        isSending = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) {

            // ── Header ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding)
            ) {
                Spacer(modifier = Modifier.height(52.dp))
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
                Spacer(modifier = Modifier.height(4.dp))
                QuotaTile(
                    quota      = state.quotaData,
                    isExpanded = quotaExpanded,
                    onToggle   = { quotaExpanded = !quotaExpanded }
                )
            }

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

            // ── Observations ──────────────────────────────────
            if (state.observations.isNotEmpty()) {
                ObservationsInbox(
                    observations = state.observations,
                    expanded     = obsExpanded,
                    onToggle     = { obsExpanded = !obsExpanded },
                    onFeedback   = { id, rating -> viewModel.submitFeedback(id, rating) }
                )
            }

            // ── Paper Trade tile ──────────────────────────────
            PaperTradeTile(
                repository = repository,
                expanded   = paperTradeExpanded,
                onToggle   = { paperTradeExpanded = !paperTradeExpanded }
            )

            // ── Input + controls ──────────────────────────────
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
                            else if (chatMode == ChatMode.OFFLINE) "Offline prompt…"
                            else "Ask Buddy…",
                            style     = T.meta,
                            fontStyle = FontStyle.Italic
                        )
                    },
                    enabled         = !state.quotaExceeded && !isSending,
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = if (chatMode == ChatMode.OFFLINE) AmberDash else T.Ink,
                        unfocusedBorderColor = if (chatMode == ChatMode.OFFLINE) AmberDash.copy(alpha = 0.5f) else T.Rule,
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
                                text     = if (chatMode == ChatMode.OFFLINE) "Offline" else "Auto",
                                fontSize = 11.sp,
                                color    = if (chatMode == ChatMode.OFFLINE) AmberDash else T.Ink
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
                                enabled = false,
                                text    = { Text("Council — coming soon", style = T.meta, color = T.Muted) },
                                onClick = {}
                            )
                        }
                    }

                    // Send / Queue
                    Button(
                        onClick        = { sendMessage() },
                        enabled        = inputText.trim().isNotEmpty() && !isSending && !state.quotaExceeded,
                        colors         = ButtonDefaults.buttonColors(
                            containerColor         = if (chatMode == ChatMode.OFFLINE) AmberDash else T.Ink,
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

            // ── Chat area ─────────────────────────────────────
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
                                timeDisplay = b.timeDisplay
                            )
                            else -> SystemCard(b.text)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        InkBottomNav(
            current       = "ask",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

// ── Observations Inbox ────────────────────────────────────────
@Composable
private fun ObservationsInbox(
    observations: List<ObservationItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFeedback: (Int, String) -> Unit
) {
    val unratedCount = observations.count { it.rating == null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F3EE))
            .clickable(onClick = onToggle)
            .animateContentSize()
    ) {
        // ── Header row ────────────────────────────────────────
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
                Text(text = "Today's observations", style = T.sectionHead, color = T.Ink)
                Box(
                    modifier = Modifier
                        .background(
                            if (unratedCount > 0) T.Ink else Color(0xFF3B6D11),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = if (unratedCount > 0) "$unratedCount" else "✓",
                        fontSize   = 10.sp,
                        color      = T.Paper,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Red dot only if unrated remain
                if (unratedCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(ErrorRed, RoundedCornerShape(3.dp))
                    )
                }
            }
            Text(text = if (expanded) "▲" else "▼", style = T.meta, color = T.Muted)
        }

        // ── Expanded cards ────────────────────────────────────
        if (expanded) {
            val unrated = observations.filter { it.rating == null }
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            if (unrated.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = T.screenPadding, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "All observations reviewed ✓", style = T.meta, color = T.Muted)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = T.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    unrated.forEach { obs ->
                        ObservationCard(obs = obs, onFeedback = onFeedback)
                    }
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
@Composable
private fun BuddyBubble(
    text: String,
    provenance: String  = "",
    elapsedMs: Long     = 0L,
    isLoading: Boolean  = false,
    isQueued: Boolean   = false,
    timeDisplay: String = ""
) {
    Column(modifier = Modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .background(
                    BuddyGreen,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
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
    }
}

// ── User bubble ───────────────────────────────────────────────
@Composable
private fun UserBubble(text: String, timeDisplay: String = "") {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp,
        bottomEnd = 12.dp, bottomStart = 12.dp)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(Color.White, shape)
                .border(0.5.dp, T.Rule, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(text = text, fontSize = 13.sp, color = T.Ink, lineHeight = 18.sp)
        }
        if (timeDisplay.isNotEmpty()) {
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
// ── Paper Trade Tile ──────────────────────────────────────────
// Collapsible tile — collapsed by default.
// Fetches GET /buddie/paper-trade/latest on first expand.
// Run Now: POST /buddie/paper-trade/run → re-fetches on success.
// Days remaining colour: ≤3 red · 4–7 amber · >7 grey.

private val PaperGreen    = Color(0xFF2D6A4F)
private val PaperGreenBg  = Color(0xFFEAF3DE)
private val PaperAmberBg  = Color(0xFFFAEEDA)
private val PaperAmberFg  = Color(0xFFBA7517)
private val PaperRedBg    = Color(0xFFFCEBEB)
private val PaperRedFg    = Color(0xFFA32D2D)

private fun parseAnnualYield(rationale: String): String? {
    val match = Regex("annual=([\\d.]+)%").find(rationale)
    return match?.groupValues?.get(1)?.let { "$it%" }
}

private fun daysUntil(dateStr: String): Long {
    return try {
        val target = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        ChronoUnit.DAYS.between(LocalDate.now(), target).coerceAtLeast(0)
    } catch (e: Exception) { -1L }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        "${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
    } catch (e: Exception) { dateStr }
}

@Composable
fun PaperTradeTile(
    repository: VitaClawRepository,
    expanded:   Boolean,
    onToggle:   () -> Unit
) {
    var data      by remember { mutableStateOf<PaperTradeResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var runResult by remember { mutableStateOf<String?>(null) }
    var loaded    by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()

    // Fetch on first expand
    LaunchedEffect(expanded) {
        if (expanded && !loaded) {
            isLoading = true
            repository.getPaperTrade().fold(
                onSuccess = { data = it; loaded = true },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    fun runNow() {
        scope.launch {
            isRunning  = true
            runResult  = null
            repository.runPaperTrade().fold(
                onSuccess = {
                    runResult = if (it.status == "ok") "Run complete" else "Unexpected status: ${it.status}"
                    // Re-fetch
                    repository.getPaperTrade().fold(
                        onSuccess = { d -> data = d },
                        onFailure = { }
                    )
                },
                onFailure = { runResult = "Error: ${it.message}" }
            )
            isRunning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(T.Paper)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

        // ── Collapsed header row ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = T.screenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Paper trade",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color      = T.Ink
                )
                data?.let { d ->
                    Text(
                        text     = "${d.count} picks · £${"%.2f".format(d.totalCapitalGbp)} → £${"%.2f".format(d.totalIncomeGbp)}",
                        fontSize = 11.sp,
                        color    = T.Muted
                    )
                }
            }
            Text(
                text     = if (expanded) "▴" else "▾",
                fontSize = 11.sp,
                color    = T.Muted
            )
        }

        // ── Expanded content ──────────────────────────────────
        if (expanded) {
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    Alignment.Center
                ) {
                    CircularProgressIndicator(color = PaperGreen, strokeWidth = 1.5.dp,
                        modifier = Modifier.size(18.dp))
                }
                error != null -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    Alignment.Center
                ) {
                    Text("Could not load: $error", fontSize = 11.sp, color = T.Muted)
                }
                data != null -> {
                    val d = data!!

                    // Month header + summary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8F6F0))
                            .padding(horizontal = T.screenPadding, vertical = 8.dp)
                    ) {
                        Text(
                            text       = "Buddie paper trade · ${d.month}",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color      = T.Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("Total capital", fontSize = 9.sp, color = T.Muted)
                                Text("£${"%.2f".format(d.totalCapitalGbp)}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, color = T.Ink)
                            }
                            Column {
                                Text("Projected income", fontSize = 9.sp, color = T.Muted)
                                Text("£${"%.2f".format(d.totalIncomeGbp)}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, color = PaperGreen)
                            }
                            Column {
                                Text("Selections", fontSize = 9.sp, color = T.Muted)
                                Text("${d.count}", fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, color = T.Ink)
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = T.Rule)

                    // Selection cards
                    d.selections.forEach { sel ->
                        PaperTradeSelectionCard(sel)
                        HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
                    }

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = T.screenPadding, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text      = "Paper trade only — not executed",
                            fontSize  = 10.sp,
                            color     = T.Muted,
                            fontStyle = FontStyle.Italic
                        )
                        OutlinedButton(
                            onClick        = { runNow() },
                            enabled        = !isRunning,
                            border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                            shape          = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier       = Modifier.height(30.dp)
                        ) {
                            Text(
                                text     = if (isRunning) "Running…" else "Run now",
                                fontSize = 10.sp,
                                color    = T.Ink
                            )
                        }
                    }

                    // Run result toast
                    runResult?.let { msg ->
                        Text(
                            text     = msg,
                            fontSize = 10.sp,
                            color    = if (msg.startsWith("Error")) PaperRedFg else PaperGreen,
                            modifier = Modifier.padding(horizontal = T.screenPadding).padding(bottom = 8.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = T.Rule)
        }
    }
}

@Composable
private fun PaperTradeSelectionCard(sel: PaperTradeSelection) {
    val days       = daysUntil(sel.exDivDate)
    val annualYield = parseAnnualYield(sel.rationale)

    // Days badge colours
    val (daysBg, daysFg) = when {
        days <= 3L  -> Pair(PaperRedBg,   PaperRedFg)
        days <= 7L  -> Pair(PaperAmberBg, PaperAmberFg)
        else        -> Pair(Color(0xFFF1EFE8), T.Muted)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = T.screenPadding, vertical = 10.dp)
    ) {
        // Top row: ticker + yield badge
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = sel.ticker,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = T.Ink
            )
            annualYield?.let { yield ->
                Box(
                    modifier = Modifier
                        .background(PaperGreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text      = "$yield annual",
                        fontSize  = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color     = PaperGreen
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // Ex-div date + days badge
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text     = "Buy before ${formatShortDate(sel.exDivDate)}",
                fontSize = 11.sp,
                color    = T.Muted
            )
            if (days >= 0) {
                Box(
                    modifier = Modifier
                        .background(daysBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text      = "${days}d",
                        fontSize  = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color     = daysFg
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Capital + income
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Capital:", fontSize = 11.sp, color = T.Muted)
            Text("£${"%.2f".format(sel.capitalGbp)}", fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = T.Ink)
            Text("·", fontSize = 11.sp, color = T.Muted)
            Text("Income:", fontSize = 11.sp, color = T.Muted)
            Text("£${"%.2f".format(sel.projectedIncomeGbp)}", fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = PaperGreen)
        }

        Spacer(Modifier.height(2.dp))

        // Payment date
        Text(
            text     = "Pays ${formatShortDate(sel.paymentDate)}",
            fontSize = 10.sp,
            color    = T.Muted
        )
    }
}