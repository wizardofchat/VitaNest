package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// RiskViewModel — Portfolio Lens intelligence layer.
// Loads enriched /portfolio/pies once. Computes 4 concentration slices
// client-side from breakdown blocks. Shock arithmetic is pure Kotlin —
// no LLM calls on slider drag. ☘️

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.remote.LensBreakdownItem
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.LensThresholds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Slice definition ──────────────────────────────────────────

enum class LensSlice(val label: String) {
    INCOME_TYPE("Income type"),
    ASSET_CLASS("Asset class"),
    GEOGRAPHY("Geography"),
    CURRENCY("Currency")
}

// ── Bar item — one row in the concentration view ──────────────

data class ConcentrationBar(
    val label: String,           // "Covered call", "ETF", "mixed", "GBP"
    val valueGbp: Double,
    val weightPct: Double,       // base weight — not shock-adjusted (concentration doesn't change)
    val color: Int               // index into palette
)

// ── UI state ──────────────────────────────────────────────────

data class RiskUiState(
    val isLoading: Boolean               = true,
    val error: String?                   = null,
    val piesData: PiesResponse?          = null,
    val activeSlice: LensSlice           = LensSlice.INCOME_TYPE,
    val shockPct: Float                  = 0f,           // −30 to +30
    val bars: List<ConcentrationBar>     = emptyList(),
    val thresholdPct: Double             = 40.0,
    val portfolioValueGbp: Double        = 0.0,
    val portfolioShockedGbp: Double      = 0.0,
    val incomeBaseGbp: Double            = 38.32,        // from /growth summary — placeholder
    val incomeShockedGbp: Double         = 38.32,
    val resilienceScore: Int             = 74,
    val resilienceShocked: Int           = 74,
    val buddieObservation: String?       = null          // from /buddie/observations — Phase 2
)

class RiskViewModel(
    private val repository: com.vitanest.app.data.repository.VitaClawRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RiskUiState())
    val state: StateFlow<RiskUiState> = _state

    // Concentration is income-weighted — higher-income slices are more sensitive to shocks
    // Factor represents fraction of income tied to that slice type
    private val INCOME_SENSITIVITY = mapOf(
        "covered_call" to 0.90,   // covered call income tracks closely with asset value
        "income"       to 0.75,
        "growth"       to 0.30,   // growth pies pay little income
        "commodity"    to 0.20,
        "mixed"        to 0.50
    )

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getPortfolioPies().fold(
                onSuccess = { pies ->
                    _state.value = _state.value.copy(
                        piesData         = pies,
                        portfolioValueGbp = pies.totalValueGbp,
                        isLoading        = false
                    )
                    applySlice(_state.value.activeSlice)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error     = e.message
                    )
                }
            )
        }
    }

    fun setSlice(slice: LensSlice) {
        _state.value = _state.value.copy(activeSlice = slice)
        applySlice(slice)
    }

    fun setShock(pct: Float) {
        _state.value = _state.value.copy(shockPct = pct)
        recomputeShock(pct)
    }

    // ── Slice computation ─────────────────────────────────────
    // Reads the pre-computed breakdown blocks from the API response.
    // Never derives concentration from raw positions — API owns that logic.

    private fun applySlice(slice: LensSlice) {
        val pies = _state.value.piesData ?: return
        val (bars, threshold) = when (slice) {
            LensSlice.INCOME_TYPE -> Pair(
                barsFromBreakdown(pies.incomeTypeBreakdown),
                pies.thresholds?.incomeTypeWarningPct ?: 60.0
            )
            LensSlice.ASSET_CLASS -> Pair(
                barsFromBreakdown(pies.assetClassBreakdown),
                pies.thresholds?.assetClassWarningPct ?: 80.0
            )
            LensSlice.GEOGRAPHY -> Pair(
                barsFromBreakdown(pies.geographyBreakdown),
                pies.thresholds?.geographyWarningPct ?: 60.0
            )
            LensSlice.CURRENCY -> Pair(
                barsFromBreakdown(pies.currencyBreakdown),
                pies.thresholds?.currencyWarningPct ?: 60.0
            )
        }
        _state.value = _state.value.copy(
            bars         = bars,
            thresholdPct = threshold
        )
        recomputeShock(_state.value.shockPct)
    }

    private fun barsFromBreakdown(breakdown: Map<String, LensBreakdownItem>?): List<ConcentrationBar> {
        if (breakdown == null) return emptyList()
        return breakdown.entries
            .sortedByDescending { it.value.weightPct }
            .mapIndexed { idx, (key, item) ->
                ConcentrationBar(
                    label      = formatLabel(key),
                    valueGbp   = item.valueGbp,
                    weightPct  = item.weightPct,
                    color      = idx
                )
            }
    }

    // ── Shock arithmetic — pure client-side ───────────────────
    // Portfolio value scales linearly with shock.
    // Income is more sensitive for high-yield concentrated slices.
    // Resilience decreases as shock deepens.

    private fun recomputeShock(pct: Float) {
        val base      = _state.value.portfolioValueGbp
        val shocked   = base * (1.0 + pct / 100.0)
        val incBase   = _state.value.incomeBaseGbp

        // Income sensitivity — weighted by top bar's income type
        val topBar    = _state.value.bars.firstOrNull()
        val topKey    = topBar?.label?.lowercase()?.replace(" ", "_") ?: "mixed"
        val sens      = INCOME_SENSITIVITY[topKey] ?: 0.50
        val incShock  = incBase + (incBase * (pct / 100.0) * sens * topBar?.weightPct?.div(100.0)!!)

        val resBase   = 74
        val resShocked = (resBase + pct * 0.65).toInt().coerceIn(10, 95)

        _state.value = _state.value.copy(
            portfolioShockedGbp = shocked,
            incomeShockedGbp    = incShock.coerceAtLeast(0.0),
            resilienceShocked   = resShocked,
            resilienceScore     = resBase
        )
    }

    // ── Label formatting ──────────────────────────────────────
    // "covered_call" → "Covered call", "us" → "US", "GBP" → "GBP"

    private fun formatLabel(key: String): String = when (key.lowercase()) {
        "us"           -> "US"
        "uk"           -> "UK"
        "em"           -> "EM"
        "gbp"          -> "GBP"
        "usd"          -> "USD"
        "gbx"          -> "GBX"
        "etf"          -> "ETF"
        "reit"         -> "REIT"
        "covered_call" -> "Covered call"
        else           -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}