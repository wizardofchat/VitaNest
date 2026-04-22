package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// FinanceAskSurface — intent chip query surface for Finance screen
// Intents hardcoded from /capabilities contract 2026-04-22
// Swap to live /capabilities call when VitaClaw endpoint is ready ☘️

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.repository.VitaClawRepository
import com.vitanest.app.ui.theme.VitaNestTheme as T
import kotlinx.coroutines.launch

// ── Intent model ─────────────────────────────────────────────
private data class FinanceIntent(
    val id: String,
    val label: String,              // chip display text
    val query: String,              // sent to POST /ask (may contain {input})
    val needsInput: Boolean = false,
    val inputHint: String = "",     // placeholder in InputCard
    val inputLabel: String = ""     // label above the text field
)

// ── Intent catalogue — hardcoded from /capabilities 2026-04-22
// When VitaClaw /capabilities is live, replace this list with the
// parsed screens.portfolio.intents array. Nothing else changes.
private val INCOME_INTENTS = listOf(
    FinanceIntent(
        id = "top_dividends",
        label = "top dividend payers",
        query = "what are my top dividend payers"
    ),
    FinanceIntent(
        id = "bottom_dividends",
        label = "worst dividend payers",
        query = "what are my worst dividend payers"
    ),
    FinanceIntent(
        id = "monthly_income",
        label = "monthly income",
        query = "what is my monthly dividend income"
    ),
    FinanceIntent(
        id = "income_projection",
        label = "when will I hit £150",
        query = "when will I hit £150 per month"
    ),
    FinanceIntent(
        id = "ticker_dividends",
        label = "earned from ___",
        query = "how much did I earn from {input}",
        needsInput = true,
        inputLabel = "Which ticker?",
        inputHint = "e.g. JEPQ"
    ),
    FinanceIntent(
        id = "monthly_income_by_ticker",
        label = "who paid in ___",
        query = "who paid me dividends in {input}",
        needsInput = true,
        inputLabel = "Which month?",
        inputHint = "e.g. March"
    )
)

private val PERFORMANCE_INTENTS = listOf(
    FinanceIntent(
        id = "top_positions_by_pnl",
        label = "winners & losers",
        query = "what are my biggest winners and losers"
    ),
    FinanceIntent(
        id = "portfolio_summary",
        label = "portfolio summary",
        query = "show me my portfolio summary"
    ),
    FinanceIntent(
        id = "portfolio_filter",
        label = "worst performers",
        query = "show me my worst performers"
    ),
    FinanceIntent(
        id = "ticker_comparison",
        label = "compare ___ vs ___",
        query = "compare {input}",
        needsInput = true,
        inputLabel = "Compare which tickers?",
        inputHint = "e.g. JEPQ vs CTY"
    ),
    FinanceIntent(
        id = "dividends_breakdown",
        label = "dividends in ___",
        query = "show all {input} dividends",
        needsInput = true,
        inputLabel = "Which month?",
        inputHint = "e.g. March"
    )
)

private val GOALS_INTENTS = listOf(
    FinanceIntent(
        id = "dividend_calendar",
        label = "next dividend",
        query = "when is my next dividend payment"
    ),
    FinanceIntent(
        id = "tax_status",
        label = "tax status",
        query = "what is my tax status"
    ),
    FinanceIntent(
        id = "cross_domain",
        label = "dividends + recovery ___",
        query = "dividends and recovery for {input}",
        needsInput = true,
        inputLabel = "Which month?",
        inputHint = "e.g. March"
    )
)

// ── Result state ─────────────────────────────────────────────
private data class AskResult(
    val answer: String,
    val tier: String,
    val sources: List<String>
)

// ── Surface root ─────────────────────────────────────────────
@Composable
fun FinanceAskSurface(repository: VitaClawRepository) {
    val scope          = rememberCoroutineScope()
    val keyboard       = LocalSoftwareKeyboardController.current

    var activeIntent   by remember { mutableStateOf<FinanceIntent?>(null) }
    var inputValue     by remember { mutableStateOf("") }
    var isQuerying     by remember { mutableStateOf(false) }
    var result         by remember { mutableStateOf<AskResult?>(null) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    fun fireQuery(intent: FinanceIntent, input: String = "") {
        val query = if (intent.needsInput) {
            intent.query.replace("{input}", input.trim())
        } else {
            intent.query
        }
        if (query.contains("{input}")) return   // input not filled yet

        keyboard?.hide()
        isQuerying = true
        result     = null
        errorMsg   = null

        scope.launch {
            val response = repository.askQuestion(query)
            isQuerying = false
            if (response.isSuccess) {
                val body = response.getOrNull()
                if (body != null) {
                    // vitanest_blocked → show the message, not an error state
                    result = AskResult(
                        answer  = body.answer,
                        tier    = body.tier ?: "polars",
                        sources = body.sources ?: emptyList()
                    )
                } else {
                    errorMsg = "No response from VitaClaw"
                }
            } else {
                errorMsg = "Could not reach VitaClaw — check Tailscale"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = T.screenPadding),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Income ──────────────────────────────────────────
        item {
            IntentCategoryLabel("Income")
            IntentChipRow(
                intents        = INCOME_INTENTS,
                activeIntent   = activeIntent,
                onChipTap      = { intent ->
                    activeIntent = if (activeIntent?.id == intent.id) null else intent
                    inputValue   = ""
                    result       = null
                    errorMsg     = null
                    if (!intent.needsInput) fireQuery(intent)
                }
            )
        }

        // ── Performance ─────────────────────────────────────
        item {
            IntentCategoryLabel("Performance")
            IntentChipRow(
                intents        = PERFORMANCE_INTENTS,
                activeIntent   = activeIntent,
                onChipTap      = { intent ->
                    activeIntent = if (activeIntent?.id == intent.id) null else intent
                    inputValue   = ""
                    result       = null
                    errorMsg     = null
                    if (!intent.needsInput) fireQuery(intent)
                }
            )
        }

        // ── Goals ────────────────────────────────────────────
        item {
            IntentCategoryLabel("Goals")
            IntentChipRow(
                intents        = GOALS_INTENTS,
                activeIntent   = activeIntent,
                onChipTap      = { intent ->
                    activeIntent = if (activeIntent?.id == intent.id) null else intent
                    inputValue   = ""
                    result       = null
                    errorMsg     = null
                    if (!intent.needsInput) fireQuery(intent)
                }
            )
        }

        // ── Input card (dashed chips only) ───────────────────
        activeIntent?.let { intent ->
            if (intent.needsInput) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    InputCard(
                        label          = intent.inputLabel,
                        placeholder    = intent.inputHint,
                        value          = inputValue,
                        onValueChange  = { inputValue = it },
                        focusRequester = focusRequester,
                        onGo           = { fireQuery(intent, inputValue) }
                    )
                    LaunchedEffect(intent.id) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }

        // ── Loading ──────────────────────────────────────────
        if (isQuerying) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color       = T.Ink,
                        strokeWidth = 1.5.dp,
                        modifier    = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "querying…", style = T.meta)
                }
            }
        }

        // ── Result card ──────────────────────────────────────
        result?.let { r ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
                ResultCard(result = r)
            }
        }

        // ── Error state ──────────────────────────────────────
        errorMsg?.let { msg ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text     = msg,
                    style    = T.meta,
                    color    = T.Muted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Category label ────────────────────────────────────────────
@Composable
private fun IntentCategoryLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = T.sectionHead,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

// ── Chip row ─────────────────────────────────────────────────
@Composable
private fun IntentChipRow(
    intents: List<FinanceIntent>,
    activeIntent: FinanceIntent?,
    onChipTap: (FinanceIntent) -> Unit
) {
    // Flow layout via wrapping rows — no external library needed
    var rowStart = 0
    val rows     = mutableListOf<List<FinanceIntent>>()
    // Build logical rows of ≤3 — chip wrapping handled by FlowRow below
    // Using simple Column + Row wrapping pattern
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var remaining = intents.toMutableList()
        while (remaining.isNotEmpty()) {
            val rowItems = remaining.take(3)
            remaining    = remaining.drop(3).toMutableList()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { intent ->
                    IntentChip(
                        intent      = intent,
                        isActive    = activeIntent?.id == intent.id,
                        onTap       = { onChipTap(intent) }
                    )
                }
            }
        }
    }
}

// ── Single chip ───────────────────────────────────────────────
@Composable
private fun IntentChip(
    intent: FinanceIntent,
    isActive: Boolean,
    onTap: () -> Unit
) {
    val chipShape = RoundedCornerShape(16.dp)
    val isDashed  = intent.needsInput    // visual distinction

    val bgColor     = when {
        isActive && !isDashed -> T.Ink
        else                  -> Color.White
    }
    val textColor   = when {
        isActive && !isDashed -> T.Paper
        isDashed              -> Color(0xFF2D5A3D)   // Shamrock
        else                  -> T.Ink
    }
    val borderColor = when {
        isActive && !isDashed -> T.Ink
        isDashed              -> Color(0xFF2D5A3D)   // Shamrock
        else                  -> T.Rule
    }

    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(bgColor)
            .border(
                width  = if (isDashed) 0.5.dp else 0.5.dp,
                color  = borderColor,
                shape  = chipShape
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text      = intent.label,
            fontSize  = 12.sp,
            fontStyle = if (isDashed) FontStyle.Italic else FontStyle.Normal,
            color     = textColor
        )
    }
}

// ── Input card ────────────────────────────────────────────────
@Composable
private fun InputCard(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onGo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, T.Ink, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text     = label.uppercase(),
            style    = T.sectionHead,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            placeholder   = {
                Text(text = placeholder, style = T.meta, fontStyle = FontStyle.Italic)
            },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction      = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = T.Ink,
                unfocusedBorderColor = T.Rule,
                focusedTextColor     = T.Ink,
                unfocusedTextColor   = T.Ink,
                cursorColor          = T.Ink
            ),
            textStyle = T.bodyValue.copy(
                fontFamily = FontFamily.Monospace,
                fontSize   = 14.sp
            ),
            modifier  = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick  = onGo,
            enabled  = value.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = T.Ink,
                contentColor           = T.Paper,
                disabledContainerColor = T.Rule,
                disabledContentColor   = T.Muted
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text         = "RUN QUERY",
                fontSize     = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight   = FontWeight.Medium
            )
        }
    }
}

// ── Result card ───────────────────────────────────────────────
@Composable
private fun ResultCard(result: AskResult) {
    val lines = parseAnswerLines(result.answer)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, T.Rule, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            when (line) {
                is AnswerLine.Header   -> AnswerHeaderRow(line.text)
                is AnswerLine.SubHead  -> AnswerSubHeadRow(line.text)
                is AnswerLine.DataRow  -> AnswerDataRow(line.label, line.value)
                is AnswerLine.Plain    -> AnswerPlainRow(line.text)
                is AnswerLine.Divider  -> HorizontalDivider(
                    thickness = T.ruleThickness,
                    color     = T.Rule,
                    modifier  = Modifier.padding(vertical = 4.dp)
                )
                is AnswerLine.Empty    -> Spacer(modifier = Modifier.height(2.dp))
            }
        }

        if (result.sources.isNotEmpty()) {
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule,
                modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "tier: ${result.tier} · ${result.sources.joinToString(" · ")}",
                style = T.meta,
                color = T.Muted
            )
        }
    }
}

// ── Answer line types ─────────────────────────────────────────
private sealed class AnswerLine {
    data class Header(val text: String)                     : AnswerLine()
    data class SubHead(val text: String)                    : AnswerLine()
    data class DataRow(val label: String, val value: String): AnswerLine()
    data class Plain(val text: String)                      : AnswerLine()
    object Divider                                          : AnswerLine()
    object Empty                                            : AnswerLine()
}

// ── Answer parser ─────────────────────────────────────────────
// Strips HTML tags, emoji, classifies each line by its content shape
private fun parseAnswerLines(raw: String): List<AnswerLine> {
    // Common emoji to strip — add more as VitaClaw responses evolve
    val emojiPattern = Regex(
        "[\uD83C-\uDBFF\uDC00-\uDFFF]|" +   // surrogate pairs
                "[\\u2600-\\u27FF]|" +                // misc symbols
                "[\\u2300-\\u23FF]|" +                // misc technical
                "[\\u2700-\\u27BF]|" +                // dingbats
                "[\u00A9\u00AE\u2122\u2139]"          // ©®™ℹ
    )

    fun String.clean(): String = this
        .replace(Regex("<b>|</b>|<i>|</i>|<br>|<br/>"), "")
        .replace(emojiPattern, "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    fun String.isBold(): Boolean =
        this.contains("<b>") || this.contains("</b>")

    fun String.isSeparator(): Boolean =
        this.clean().matches(Regex("[-—]{3,}.*"))

    fun String.isEmptyOrDashes(): Boolean =
        this.clean().isEmpty() || this.clean().matches(Regex("[-—]+"))

    val lines = raw.split("\n")
    val result = mutableListOf<AnswerLine>()

    for (line in lines) {
        val cleaned = line.clean()

        when {
            // Skip pure empty/dash lines (the trailing dashes bug)
            cleaned.isEmpty() || cleaned.matches(Regex("[-—]+")) -> {
                if (result.lastOrNull() !is AnswerLine.Empty) {
                    result.add(AnswerLine.Empty)
                }
            }

            // Separator line
            cleaned.matches(Regex("[-—]{3,}.*")) -> {
                result.add(AnswerLine.Divider)
            }

            // Bold header — <b>Some Title</b> or line ends with </b>
            line.isBold() && (cleaned.endsWith(":") || !cleaned.contains(":")) -> {
                val headerText = cleaned.trimEnd(':')
                result.add(AnswerLine.Header(headerText))
            }

            // Bold sub-header with content after colon
            line.isBold() && cleaned.contains(":") -> {
                val parts    = cleaned.split(":", limit = 2)
                val subLabel = parts[0].trim()
                val subVal   = parts.getOrNull(1)?.trim() ?: ""
                if (subVal.isEmpty()) {
                    result.add(AnswerLine.Header(subLabel))
                } else {
                    result.add(AnswerLine.SubHead("$subLabel: $subVal"))
                }
            }

            // Bullet data line — "• JEPQ: £27.76 (11 payments)" or "JEPQ: £27.76"
            cleaned.startsWith("•") || cleaned.startsWith("·") -> {
                val content = cleaned.trimStart('•', '·', ' ')
                if (content.contains(":")) {
                    val parts = content.split(":", limit = 2)
                    result.add(AnswerLine.DataRow(parts[0].trim(), parts[1].trim()))
                } else {
                    result.add(AnswerLine.Plain(content))
                }
            }

            // Indented data line — "  Total:   £27.76 (11 payments)"
            line.startsWith("  ") && cleaned.contains(":") -> {
                val parts = cleaned.split(":", limit = 2)
                val label = parts[0].trim()
                val value = parts.getOrNull(1)?.trim() ?: ""
                // Skip if label looks like a ticker with £0 invested (data bug)
                if (value.isEmpty()) {
                    result.add(AnswerLine.Plain(cleaned))
                } else {
                    result.add(AnswerLine.DataRow(label, value))
                }
            }

            // Numbered line — "4. <b>IUKD</b>"
            cleaned.matches(Regex("\\d+\\..*")) -> {
                val content = cleaned.replace(Regex("^\\d+\\.\\s*"), "")
                result.add(AnswerLine.SubHead(content))
            }

            // Winner/result line — "Winner: JEPQ (£27.76 total)"
            cleaned.contains(":") && cleaned.length < 80 -> {
                val parts = cleaned.split(":", limit = 2)
                result.add(AnswerLine.DataRow(parts[0].trim(), parts[1].trim()))
            }

            // Plain text
            else -> result.add(AnswerLine.Plain(cleaned))
        }
    }

    // Trim trailing empty lines
    return result.dropLastWhile { it is AnswerLine.Empty }
}

// ── Row composables ───────────────────────────────────────────
@Composable
private fun AnswerHeaderRow(text: String) {
    Text(
        text       = text,
        fontFamily = T.Serif,
        fontWeight = FontWeight.Bold,
        fontSize   = 15.sp,
        color      = T.Ink,
        lineHeight = 20.sp
    )
}

@Composable
private fun AnswerSubHeadRow(text: String) {
    Text(
        text       = text,
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        color      = T.Ink,
        lineHeight = 18.sp,
        modifier   = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun AnswerDataRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Text(
            text      = label,
            fontSize  = 12.sp,
            color     = T.Muted,
            modifier  = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text       = value,
            fontSize   = 12.sp,
            fontFamily = T.Serif,
            color      = T.Ink,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnswerPlainRow(text: String) {
    Text(
        text       = text,
        fontSize   = 13.sp,
        color      = T.Ink,
        lineHeight = 19.sp
    )
}