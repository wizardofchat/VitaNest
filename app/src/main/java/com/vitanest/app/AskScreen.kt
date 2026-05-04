package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// AskScreen — Buddie chat interface, replaces FinanceAskSurface
// Endpoints: /chat/opening · /chat · /chat/history · /intents · /chat/offline/pending
// Rule: POST /chat on Send tap ONLY — never on load, resume, or scroll ☘️

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitanest.app.data.remote.ChatOpeningResponse
import com.vitanest.app.data.remote.IntentItem
import com.vitanest.app.data.remote.PendingOfflineItem
import com.vitanest.app.data.remote.QuotaResponse
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch

private enum class ChatMode { AUTO, OFFLINE }

private data class BubbleMsg(
    val role: String,
    val text: String,
    val provenance: String = "",
    val elapsedMs: Long = 0L,
    val isLoading: Boolean = false,
    val isQueued: Boolean = false
)

private val BuddyGreen = Color(0xFF2D6A4F)
private val AmberDash  = Color(0xFFF59E0B)

@Composable
fun AskScreen(
    navController: NavController,
    repository: VitaClawRepository
) {
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var quotaData     by remember { mutableStateOf<QuotaResponse?>(null) }
    var quotaExpanded by remember { mutableStateOf(false) }
    var opening       by remember { mutableStateOf<ChatOpeningResponse?>(null) }
    var intents       by remember { mutableStateOf<List<IntentItem>>(emptyList()) }
    var pending       by remember { mutableStateOf<List<PendingOfflineItem>>(emptyList()) }
    var bubbles       by remember { mutableStateOf<List<BubbleMsg>>(emptyList()) }
    var inputText     by remember { mutableStateOf("") }
    var chatMode      by remember { mutableStateOf(ChatMode.AUTO) }
    var modeExpanded  by remember { mutableStateOf(false) }
    var tilesExpanded by remember { mutableStateOf(false) }
    var isSending     by remember { mutableStateOf(false) }
    var quotaExceeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.getQuota().onSuccess { q ->
            quotaData = q
            quotaExceeded = q.status == "quota_exceeded"
        }
        repository.getChatOpening().onSuccess { o -> opening = o }
        repository.getChatHistory().onSuccess { h ->
            bubbles = h.exchanges.map { e ->
                BubbleMsg(role = e.role, text = e.message,
                    provenance = e.provenance, elapsedMs = e.elapsedMs)
            }
        }
        repository.getChatOfflinePending().onSuccess { p -> pending = p.items }
        repository.getIntents().onSuccess { r ->
            intents = r.intents.filter { it.enabled }
        }
    }

    // RULE: only called from Send tap or intent tile tap — never on load
    fun sendMessage(msg: String, offline: Boolean = false) {
        if (msg.isBlank() || isSending) return
        val trimmed = msg.trim()
        inputText = ""
        isSending = true
        bubbles = bubbles + BubbleMsg(role = "user", text = trimmed)
        bubbles = bubbles + if (offline)
            BubbleMsg(role = "buddy", text = "Queued offline — Buddy will notify you via Telegram", isQueued = true)
        else
            BubbleMsg(role = "buddy", text = "…", isLoading = true)

        scope.launch {
            listState.animateScrollToItem(bubbles.size - 1)
            repository.sendChat(trimmed, offline).fold(
                onSuccess = { resp ->
                    bubbles = bubbles.dropLast(1) + BubbleMsg(
                        role       = "buddy",
                        text       = resp.response,
                        provenance = resp.provenance,
                        elapsedMs  = resp.elapsedMs,
                        isQueued   = resp.asyncMode
                    )
                    repository.getQuota().onSuccess { q ->
                        quotaData = q
                        quotaExceeded = q.status == "quota_exceeded"
                    }
                },
                onFailure = { err ->
                    val errText = if (err.message?.contains("50/day") == true)
                        "Daily limit reached (50/day). Resets at midnight."
                    else
                        "Could not reach VitaClaw — check Tailscale"
                    bubbles = bubbles.dropLast(1) + BubbleMsg(role = "buddy", text = errText)
                }
            )
            isSending = false
            listState.animateScrollToItem(bubbles.size - 1)
        }
    }

    // ── Root ──────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Paper)
    ) {
        // Main content column — padded above nav bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
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
                    opening?.let { o ->
                        val pillColor = when (o.recoveryColour) {
                            "green" -> Color(0xFF2D6A4F)
                            "red"   -> Color(0xFFC0392B)
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
                    quota      = quotaData,
                    isExpanded = quotaExpanded,
                    onToggle   = { quotaExpanded = !quotaExpanded }
                )
            }

            // ── Chat area ─────────────────────────────────────
            if (opening == null && bubbles.isEmpty()) {
                // Loading state — centered while brief loads
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
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    opening?.let { o ->
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            BuddyBubble(text = o.summary, provenance = o.provenance)
                        }
                    }
                    items(bubbles) { b ->
                        Spacer(modifier = Modifier.height(8.dp))
                        when (b.role) {
                            "user"  -> UserBubble(b.text)
                            "buddy" -> BuddyBubble(
                                text       = b.text,
                                provenance = b.provenance,
                                elapsedMs  = b.elapsedMs,
                                isLoading  = b.isLoading,
                                isQueued   = b.isQueued
                            )
                            else -> SystemCard(b.text)
                        }
                    }
                    if (pending.isNotEmpty()) {
                        items(pending) { p ->
                            Spacer(modifier = Modifier.height(8.dp))
                            PendingOfflineTile(
                                item  = p,
                                onAck = {
                                    scope.launch {
                                        repository.ackOfflineMessage(p.jobId)
                                        pending = pending.filter { it.jobId != p.jobId }
                                    }
                                }
                            )
                        }
                    }
                }
            } // end chat area

            // ── Quick tiles row ───────────────────────────────
            if (intents.isNotEmpty()) {
                HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
                Spacer(modifier = Modifier.height(8.dp))
                val visible = if (tilesExpanded) intents else intents.take(6)
                LazyRow(
                    modifier            = Modifier.padding(horizontal = T.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visible) { intent ->
                        IntentTile(
                            label = intent.label,
                            onTap = { sendMessage(intent.testQuery) }
                        )
                    }
                    if (intents.size > 6) {
                        item {
                            IntentExpandChip(
                                expanded = tilesExpanded,
                                count    = intents.size - 6,
                                onTap    = { tilesExpanded = !tilesExpanded }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Input row ─────────────────────────────────────
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.screenPadding, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = {
                        Text(
                            text      = if (quotaExceeded) "Quota exceeded" else "Ask Buddy…",
                            style     = T.meta,
                            fontStyle = FontStyle.Italic
                        )
                    },
                    enabled         = !quotaExceeded && !isSending,
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { sendMessage(inputText, chatMode == ChatMode.OFFLINE) }
                    ),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = T.Ink,
                        unfocusedBorderColor = T.Rule,
                        focusedTextColor     = T.Ink,
                        unfocusedTextColor   = T.Ink,
                        cursorColor          = T.Ink,
                        disabledBorderColor  = T.Rule,
                        disabledTextColor    = T.Muted
                    ),
                    textStyle = T.meta,
                    modifier  = Modifier.weight(1f)
                )

                Box {
                    OutlinedButton(
                        onClick        = { modeExpanded = true },
                        border         = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp),
                        shape          = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier       = Modifier.height(48.dp)
                    ) {
                        Text(
                            text  = if (chatMode == ChatMode.OFFLINE) "🔒" else "Auto",
                            style = T.meta,
                            color = T.Ink
                        )
                        Text(" ▾", style = T.meta, color = T.Muted)
                    }
                    DropdownMenu(
                        expanded         = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        modifier         = Modifier.background(T.Paper)
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Auto", style = T.meta) },
                            onClick = { chatMode = ChatMode.AUTO; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text    = { Text("🔒 Offline", style = T.meta) },
                            onClick = { chatMode = ChatMode.OFFLINE; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            enabled = false,
                            text    = { Text("Council — soon", style = T.meta, color = T.Muted) },
                            onClick = {}
                        )
                    }
                }

                Button(
                    onClick        = { sendMessage(inputText, chatMode == ChatMode.OFFLINE) },
                    enabled        = inputText.trim().isNotEmpty() && !isSending && !quotaExceeded,
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = T.Ink,
                        contentColor           = T.Paper,
                        disabledContainerColor = T.Rule,
                        disabledContentColor   = T.Muted
                    ),
                    shape          = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier       = Modifier.height(48.dp)
                ) {
                    Text(
                        text          = if (isSending) "…" else "Send",
                        fontSize      = 12.sp,
                        letterSpacing = 0.5.sp,
                        fontWeight    = FontWeight.Medium
                    )
                }
            }
        } // end Column

        // Nav bar — overlay at bottom of Box
        InkBottomNav(
            current       = "ask",
            navController = navController,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    } // end Box
}

// ── Buddy bubble (green, left) ────────────────────────────────
@Composable
private fun BuddyBubble(
    text: String,
    provenance: String = "",
    elapsedMs: Long = 0L,
    isLoading: Boolean = false,
    isQueued: Boolean = false
) {
    Column(
        modifier            = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .background(BuddyGreen, RoundedCornerShape(
                    topStart = 4.dp, topEnd = 12.dp,
                    bottomEnd = 12.dp, bottomStart = 12.dp
                ))
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
        if (provenance.isNotEmpty() && !isLoading) {
            Spacer(modifier = Modifier.height(3.dp))
            val footer = if (elapsedMs > 0) "$provenance · ${elapsedMs}ms" else provenance
            Text(text = footer, style = T.meta, color = T.Muted, fontSize = 10.sp)
        }
    }
}

// ── User bubble (cream, right) ────────────────────────────────
@Composable
private fun UserBubble(text: String) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp,
        bottomEnd = 12.dp, bottomStart = 12.dp)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(Color.White, shape)
                .border(0.5.dp, T.Rule, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(text = text, fontSize = 13.sp, color = T.Ink, lineHeight = 18.sp)
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

// ── Pending offline tile ──────────────────────────────────────
@Composable
private fun PendingOfflineTile(item: PendingOfflineItem, onAck: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberDash, RoundedCornerShape(8.dp))
            .clickable { expanded = true; onAck() }
            .padding(12.dp)
            .animateContentSize()
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(text = "📥 Offline response ready", style = T.meta,
                fontWeight = FontWeight.Medium, color = T.Ink)
            Text(text = if (expanded) "▲" else "▼", style = T.meta, color = T.Muted)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = item.query, style = T.meta, color = T.Muted,
            maxLines = if (expanded) Int.MAX_VALUE else 1)
        if (expanded && item.response.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = T.ruleThickness, color = AmberDash.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.response, fontSize = 13.sp, color = T.Ink, lineHeight = 18.sp)
            if (item.provenance.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val footer = if (item.elapsedMs > 0)
                    "${item.provenance} · ${item.elapsedMs}ms" else item.provenance
                Text(text = footer, style = T.meta, color = T.Muted, fontSize = 10.sp)
            }
        } else if (!expanded) {
            Text(text = "Tap to read full response", style = T.meta,
                color = AmberDash, fontSize = 10.sp)
        }
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