package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// VitaClawApiService — all data models + Retrofit interface
// Updated: BuddieInsights model fixed (new domain-keyed shape);
//          ObservationItem + DomainMemory updated (confidence_calibrated,
//          observation_type, domain_memory); GrowthResponse + GrowthSeries
//          added for /growth endpoint ☘️

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ── System ────────────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: String,
    @SerialName("agentic_score") val agenticScore: Int
)

@Serializable
data class QuotaGemini(
    val used: Int,
    val limit: Int,
    val remaining: Int,
    @SerialName("pct_used") val pctUsed: Double
)

@Serializable
data class QuotaClaude(
    @SerialName("spent_gbp")     val spentGbp: Double,
    @SerialName("budget_gbp")    val budgetGbp: Double,
    @SerialName("remaining_gbp") val remainingGbp: Double,
    @SerialName("pct_used")      val pctUsed: Double
)

@Serializable
data class QuotaResponse(
    val gemini: QuotaGemini,
    val claude: QuotaClaude,
    val status: String          // "ok" | "quota_exceeded"
)

// ── Ask (legacy — kept for backward compat) ───────────────────

@Serializable
data class AskRequest(
    val query: String,
    val source: String = "vitanest",
    val context: String? = ""
)

@Serializable
data class AskResponse(
    val answer: String,
    val sources: List<String>? = emptyList(),
    val sentiment: String? = null,
    val tier: String? = null
)

// ── Chat ──────────────────────────────────────────────────────

@Serializable
data class ChatRequest(
    val message: String,
    val source: String = "vitanest",
    val offline: Boolean = false
)

@Serializable
data class ChatResponse(
    val response: String,
    val tier: String,
    val provenance: String,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L,
    @SerialName("job_id")     val jobId: String = "",
    @SerialName("async_mode") val asyncMode: Boolean = false
)

@Serializable
data class ChatOpeningResponse(
    val summary: String,
    @SerialName("recovery_score")  val recoveryScore: Float,
    @SerialName("recovery_colour") val recoveryColour: String,
    @SerialName("proactive_flags") val proactiveFlags: List<String> = emptyList(),
    val brief: String = "",
    val provenance: String = ""
)

@Serializable
data class ChatHistoryEntry(
    val role: String,           // "user" | "buddy"
    val message: String,
    val provenance: String = "",
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L,
    val ts: String = ""         // "2026-05-05 15:27:22.543285+00:00"
)

// API returns a flat array — deserialise as list directly
// Repository wraps it: if response is List<ChatHistoryEntry>, use ChatHistoryResponse as wrapper
@Serializable
data class ChatHistoryResponse(
    val exchanges: List<ChatHistoryEntry> = emptyList()
)

// Flat array deserialiser — used when /chat/history returns [] not {"exchanges":[]}
typealias ChatHistoryList = List<ChatHistoryEntry>

// ── Intents ───────────────────────────────────────────────────

@Serializable
data class IntentItem(
    val id: String,
    val enabled: Boolean,
    @SerialName("test_query")      val testQuery: String,
    @SerialName("example_queries") val exampleQueries: List<String> = emptyList(),
    val domain: String = "",
    val tier: String = "polars"
) {
    // Derive chip label from id — replace underscores, capitalise
    val label: String get() = id
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
}

@Serializable
data class IntentsResponse(
    val intents: List<IntentItem> = emptyList()
)

// ── Offline pending ───────────────────────────────────────────
@Serializable
data class PendingOfflineItem(
    @SerialName("job_id")       val jobId: String,
    val message: String,
    val response: String = "",
    val provenance: String = "",
    @SerialName("elapsed_ms")   val elapsedMs: Long = 0L,
    @SerialName("completed_at") val completedAt: String = "",
    val status: String = ""
)

@Serializable
data class PendingOfflineResponse(
    @SerialName("pending_count") val pendingCount: Int,
    val jobs: List<PendingOfflineItem> = emptyList()
)

// ── Portfolio ─────────────────────────────────────────────────

@Serializable
data class PortfolioResponse(
    @SerialName("total_value_gbp") val totalValueGbp: Double,
    @SerialName("daily_pnl_gbp")   val dailyPnLGbp: Double? = 0.0,
    @SerialName("holdings")        val positions: List<Position> = emptyList()
)

@Serializable
data class Position(
    val ticker: String,
    val name: String = "",
    @SerialName("quantity")  val quantity: Double = 0.0,
    @SerialName("value_gbp") val marketValue: Double,
    @SerialName("pnl_gbp")   val pnlGbp: Double = 0.0,
    @SerialName("pnl_pct")   val pnlPct: Double = 0.0
)

@Serializable
data class PiesResponse(
    @SerialName("total_value_gbp") val totalValueGbp: Double,
    @SerialName("total_pnl_gbp")   val totalPnlGbp: Double,
    @SerialName("total_cash_gbp")  val totalCashGbp: Double,
    @SerialName("fetched_at")      val fetchedAt: String = "",
    val pies: List<PieItem> = emptyList()
)

@Serializable
data class PieItem(
    val id: Int? = null,
    val name: String,
    @SerialName("value_gbp")             val valueGbp: Double,
    @SerialName("pnl_gbp")              val pnlGbp: Double,
    @SerialName("pnl_pct")              val pnlPct: Double,
    @SerialName("cash_gbp")             val cashGbp: Double,
    @SerialName("weight_pct")           val weightPct: Double,
    @SerialName("holdings_count")       val holdingsCount: Int,
    val status: String? = null,
    @SerialName("dividends_gained_gbp") val dividendsGainedGbp: Double = 0.0,
    val tickers: List<String> = emptyList()
)

// ── DCA ───────────────────────────────────────────────────────

@Serializable
data class DcaOverview(
    @SerialName("first_order")          val firstOrder: String = "",
    @SerialName("first_price_gbp")      val firstPriceGbp: Double = 0.0,
    @SerialName("last_order")           val lastOrder: String = "",
    @SerialName("last_price_gbp")       val lastPriceGbp: Double = 0.0,
    @SerialName("total_orders")         val totalOrders: Int = 0,
    @SerialName("cash_orders")          val cashOrders: Int = 0,
    @SerialName("reinvestment_orders")  val reinvestmentOrders: Int = 0,
    @SerialName("total_invested_gbp")   val totalInvestedGbp: Double = 0.0,
    @SerialName("total_quantity")       val totalQuantity: Double = 0.0,
    @SerialName("reinvested_quantity")  val reinvestedQuantity: Double = 0.0
)

@Serializable
data class DcaPerformance(
    @SerialName("blended_avg_gbp")    val blendedAvgGbp: Double = 0.0,
    @SerialName("current_price_gbp")  val currentPriceGbp: Double = 0.0,
    @SerialName("current_value_gbp")  val currentValueGbp: Double = 0.0,
    @SerialName("capital_gain_gbp")   val capitalGainGbp: Double = 0.0,
    @SerialName("capital_return_pct") val capitalReturnPct: Double = 0.0
)

@Serializable
data class DcaEffectiveness(
    @SerialName("buys_above_avg") val buysAboveAvg: Int = 0,
    @SerialName("buys_above_pct") val buysAbovePct: Double = 0.0,
    @SerialName("buys_below_avg") val buysBelowAvg: Int = 0,
    @SerialName("buys_below_pct") val buysBelowPct: Double = 0.0,
    val verdict: String = ""
)

// API returns monthly as a list — above_avg_qty is a boolean from VitaClaw
@Serializable
data class DcaMonthlyBuy(
    val month: String = "",
    @SerialName("avg_price_gbp")  val avgPriceGbp: Double = 0.0,
    val orders: Int = 0,
    @SerialName("above_avg_qty")  val aboveAvgQty: Boolean = false,
    @SerialName("price_vs_prev")  val priceVsPrev: String = ""
)

// API returns price_distribution as a flat list of bucket objects
@Serializable
data class DcaPriceBucket(
    @SerialName("range_low")  val rangeLow: Double = 0.0,
    @SerialName("range_high") val rangeHigh: Double = 0.0,
    val count: Int = 0
)

@Serializable
data class DcaDividendPayment(
    val date: String = "",
    val quantity: Double = 0.0,
    @SerialName("amount_gbp") val amountGbp: Double = 0.0,
    @SerialName("per_share")  val perShare: Double = 0.0
)

@Serializable
data class DcaDividend(
    @SerialName("num_payments")        val numPayments: Int = 0,
    val frequency: String = "",
    @SerialName("total_received_gbp")  val totalReceivedGbp: Double = 0.0,
    @SerialName("dividend_return_pct") val dividendReturnPct: Double = 0.0,
    @SerialName("last_date")           val lastDate: String = "",
    @SerialName("last_amount_gbp")     val lastAmountGbp: Double = 0.0,
    @SerialName("last_shares")         val lastShares: Double = 0.0,
    @SerialName("last_per_share_gbp")  val lastPerShareGbp: Double = 0.0,
    val payments: List<DcaDividendPayment> = emptyList()
)

// verdict is top-level — nullable until we confirm full shape
@Serializable
data class DcaVerdict(
    val rating: String = "",
    @SerialName("capital_return_pct")  val capitalReturnPct: Double = 0.0,
    @SerialName("dividend_return_pct") val dividendReturnPct: Double = 0.0,
    @SerialName("total_return_pct")    val totalReturnPct: Double = 0.0
)

@Serializable
data class DcaDetailResponse(
    val ticker: String,
    val name: String = "",
    val currency: String = "",
    val isin: String = "",
    val overview: DcaOverview = DcaOverview(),
    val performance: DcaPerformance = DcaPerformance(),
    @SerialName("dca_effectiveness")   val dcaEffectiveness: DcaEffectiveness = DcaEffectiveness(),
    @SerialName("price_distribution")  val priceDistribution: List<DcaPriceBucket> = emptyList(),
    val monthly: List<DcaMonthlyBuy> = emptyList(),
    val dividends: DcaDividend? = null,
    val verdict: DcaVerdict? = null
)

@Serializable
data class RecentOrder(
    val date: String = "",
    val quantity: Double = 0.0,
    @SerialName("price_gbp") val priceGbp: Double = 0.0,
    val type: String = ""
)

@Serializable
data class OrderItem(
    val date: String = "",
    val ticker: String = "",
    val type: String = "",
    val quantity: Double = 0.0,
    @SerialName("price_gbp") val priceGbp: Double = 0.0,
    @SerialName("value_gbp") val valueGbp: Double = 0.0
)

@Serializable
data class OrdersSummaryResponse(
    val tickers: String = "ALL",
    val days: Int = 90,
    val count: Int = 0,
    val orders: List<OrderItem> = emptyList(),
    @SerialName("recent_orders") val recentOrders: List<RecentOrder> = emptyList()
)

// ── Brief ─────────────────────────────────────────────────────

@Serializable
data class ExDivAlert(
    val tickers: String,
    @SerialName("days_away") val daysAway: Int,
    val date: String,
    @SerialName("is_alert") val isAlert: Boolean
)

// ── Buddie Insights (embedded in /brief structured) ───────────
// API shape: { "finance": { "label": "Money", "observations": [...] }, ... }
// Previous shape (whoop/t212/myenergi strings) was wrong — replaced entirely.

@Serializable
data class BuddieInsightObservation(
    val domain: String,
    val content: String,
    @SerialName("confidence_raw")    val confidenceRaw: Double,
    @SerialName("observation_type") val observationType: String = "observation"
)

@Serializable
data class BuddieInsightDomain(
    val label: String,                                          // "Money" | "Health" | "Energy"
    val observations: List<BuddieInsightObservation> = emptyList()
)

@Serializable
data class BuddieInsights(
    val finance: BuddieInsightDomain? = null,
    val health:  BuddieInsightDomain? = null,
    val energy:  BuddieInsightDomain? = null
)

// ── Brief structured ──────────────────────────────────────────

@Serializable
data class BriefStructured(
    @SerialName("recovery_score")      val recoveryScore: Float? = null,
    @SerialName("recovery_status")     val recoveryStatus: String? = null,
    @SerialName("recovery_trend")      val recoveryTrend: String? = null,
    @SerialName("hrv_ms")              val hrvMs: Float? = null,
    @SerialName("rhr_bpm")             val rhrBpm: Float? = null,
    @SerialName("spo2_pct")            val spo2Pct: Float? = null,
    @SerialName("training_advice")     val trainingAdvice: String? = null,
    @SerialName("portfolio_value_gbp") val portfolioValueGbp: Float? = null,
    @SerialName("pnl_gbp")             val pnlGbp: Float? = null,
    @SerialName("pnl_pct")             val pnlPct: Float? = null,
    @SerialName("divs_this_month_gbp") val divsThisMonthGbp: Float? = null,
    @SerialName("income_target_gbp")   val incomeTargetGbp: Float? = null,
    @SerialName("income_gap_gbp")      val incomeGapGbp: Float? = null,
    @SerialName("ex_div_alert")        val exDivAlert: ExDivAlert? = null,
    @SerialName("weather")             val weather: String? = null,
    @SerialName("buddie_insights")     val buddieInsights: BuddieInsights? = null,
    @SerialName("agents_healthy")      val agentsHealthy: Boolean? = null,
    @SerialName("quote")               val quote: String? = null,
    @SerialName("quote_author")        val quoteAuthor: String? = null
)

@Serializable
data class BriefResponse(
    val summary: String,                    // Telegram only — never render in VitaNest
    @SerialName("last_updated") val lastUpdated: String,
    val structured: BriefStructured? = null
)

// ── Whoop ─────────────────────────────────────────────────────

@Serializable
data class WhoopResponse(
    @SerialName("recovery_score")     val recoveryScore: Float = 0f,
    @SerialName("resting_heart_rate") val restingHeartRate: Float = 0f,
    @SerialName("hrv_rmssd_milli")    val hrvRmssdMilli: Float = 0f,
    @SerialName("spo2_percentage")    val spo2Percentage: Float = 0f,
    @SerialName("skin_temp_celsius")  val skinTempCelsius: Float = 0f,
    @SerialName("last_updated")       val lastUpdated: String = "",
    @SerialName("strain")             val strain: Float = 0f,
    @SerialName("sleep_performance")  val sleepPerformance: Float = 0f,
    @SerialName("sleep_efficiency")   val sleepEfficiency: Float = 0f,
    @SerialName("rem_min")            val remMin: Float = 0f,
    @SerialName("deep_min")           val deepMin: Float = 0f,
    @SerialName("disturbances")       val disturbances: Int = 0,
    @SerialName("last_workout")       val lastWorkout: String = ""
)

// ── Dividends ─────────────────────────────────────────────────

@Serializable
data class DividendNextUpcoming(
    @SerialName("ex_div_date_earliest")  val exDivDateEarliest: String?,
    @SerialName("ex_div_date_latest")    val exDivDateLatest: String?,
    @SerialName("payment_date_earliest") val paymentDateEarliest: String?,
    @SerialName("confirmed")             val confirmed: Boolean
)

@Serializable
data class DividendHolding(
    @SerialName("ticker")              val ticker: String,
    @SerialName("name")                val name: String,
    @SerialName("currency")            val currency: String,
    @SerialName("frequency")           val frequency: String?,
    @SerialName("avg_per_share")       val avgPerShare: Double?,
    @SerialName("avg_calculated_from") val avgCalculatedFrom: Int,
    @SerialName("data_quality")        val dataQuality: String,
    @SerialName("data_quality_notes")  val dataQualityNotes: String?,
    @SerialName("days_until_ex_div")   val daysUntilExDiv: Int?,
    @SerialName("is_dividend_payer")   val isDividendPayer: Boolean,
    @SerialName("next_upcoming")       val nextUpcoming: DividendNextUpcoming?,
    @SerialName("price_gbp")           val priceGbp: Double? = null,
    @SerialName("quantity")            val quantity: Double? = null,
    @SerialName("value_gbp")           val valueGbp: Double? = null
)

@Serializable
data class DividendDataResponse(
    @SerialName("fetched_at")            val fetchedAt: String,
    @SerialName("total_tickers_tracked") val totalTickersTracked: Int,
    @SerialName("tickers")              val tickers: List<DividendHolding>
)

// ── Goals ─────────────────────────────────────────────────────

@Serializable
data class GoalsResponse(
    @SerialName("income_target_gbp")      val incomeTargetGbp: Double,
    @SerialName("income_target_date")     val incomeTargetDate: String,
    @SerialName("withholding_tax_factor") val withholdingTaxFactor: Double,
    @SerialName("current_gbp")            val currentGbp: Double
)

// ── Energy ────────────────────────────────────────────────────

@Serializable
data class EnergyResponse(
    val date: String,
    @SerialName("solar_generated_kwh")     val solarGeneratedKwh: Double?,
    @SerialName("self_consumed_kwh")       val selfConsumedKwh: Double?,
    @SerialName("solar_exported_kwh")      val solarExportedKwh: Double?,
    @SerialName("grid_imported_kwh")       val gridImportedKwh: Double?,
    @SerialName("ev_total_kwh")            val evTotalKwh: Double?,
    @SerialName("ev_solar_kwh")            val evSolarKwh: Double?,
    @SerialName("ev_grid_kwh")             val evGridKwh: Double?,
    @SerialName("eddi_solar_kwh")          val eddiSolarKwh: Double?,
    @SerialName("eddi_boosted_kwh")        val eddiBoostedKwh: Double?,
    @SerialName("home_consumption_kwh")    val homeConsumptionKwh: Double?,
    @SerialName("ev_charging_cost_gbp")    val evChargingCostGbp: Double?,
    @SerialName("home_import_cost_gbp")    val homeImportCostGbp: Double?,
    @SerialName("export_earnings_gbp")     val exportEarningsGbp: Double?,
    @SerialName("solar_savings_gbp")       val solarSavingsGbp: Double?,
    @SerialName("energy_cost_savings_gbp") val energyCostSavingsGbp: Double?,
    @SerialName("total_cost_gbp")          val totalCostGbp: Double?,
    @SerialName("tariff_peak_pence")       val tariffPeakPence: Double?,
    @SerialName("tariff_cheap_pence")      val tariffCheapPence: Double?,
    @SerialName("tariff_export_pence")     val tariffExportPence: Double?,
    @SerialName("charge_mode")             val chargeMode: String?,
    @SerialName("last_updated")            val lastUpdated: String
)

// ── Buddie Observations (/buddie/observations/today) ──────────
// domain_memory: cached per parent_domain — don't re-fetch per card.
// v0 · 0 obs = new domain, render in muted grey.

@Serializable
data class DomainMemory(
    @SerialName("parent_domain")      val parentDomain: String,   // "finance" | "health" | "energy"
    val version: Int = 0,
    @SerialName("total_observations") val totalObservations: Int = 0
)

@Serializable
data class ObservationItem(
    val id: Int,
    val domain: String,
    val action: String,                                             // "OBSERVE: claim_id" — strip prefix for display
    val content: String,
    val confidence: Double,
    @SerialName("confidence_calibrated") val confidenceCalibrated: Double? = null,  // null → show "—"
    @SerialName("observation_type")      val observationType: String = "observation", // "observation" | "hypothesis"
    val rating: String? = null,
    @SerialName("created_at")            val createdAt: String = "",
    @SerialName("domain_memory")         val domainMemory: DomainMemory? = null
) {
    // Strip "OBSERVE: " prefix — display claim_id only
    val claimId: String get() = action.removePrefix("OBSERVE: ").trim()
}

@Serializable
data class ObservationsResponse(
    val date: String,
    val count: Int,
    val observations: List<ObservationItem> = emptyList()
)

@Serializable
data class FeedbackRequest(val rating: String)

// ── Growth (/growth) ──────────────────────────────────────────
// Series fields are all nullable — new columns appear as VitaClaw adds them.
// Never hardcode column positions — always access by key name.
// date is "YYYY-MM-DD" string — sort as string (ISO format is safe).

@Serializable
data class GrowthSummary(
    @SerialName("portfolio_start_gbp")      val portfolioStartGbp: Double? = null,
    @SerialName("portfolio_end_gbp")        val portfolioEndGbp: Double? = null,
    @SerialName("portfolio_change_gbp")     val portfolioChangeGbp: Double? = null,
    @SerialName("portfolio_change_pct")     val portfolioChangePct: Double? = null,
    @SerialName("avg_recovery")             val avgRecovery: Double? = null,
    @SerialName("avg_spo2_pct")             val avgSpo2Pct: Double? = null,
    @SerialName("spo2_below_threshold")     val spo2BelowThreshold: Int? = null,
    @SerialName("income_30d_gbp")           val income30dGbp: Double? = null,
    @SerialName("income_gap_to_target_gbp") val incomeGapToTargetGbp: Double? = null
)

@Serializable
data class GrowthSeries(
    val date: String,
    // Portfolio
    @SerialName("equity_gbp")              val equityGbp: Double? = null,
    @SerialName("pnl_gbp")                val pnlGbp: Double? = null,
    @SerialName("deposits_mtd")            val depositsMtd: Double? = null,
    // Health
    @SerialName("recovery_score")          val recoveryScore: Double? = null,
    @SerialName("recovery_zone")           val recoveryZone: String? = null,  // "green"|"yellow"|"red"
    @SerialName("hrv_ms")                  val hrvMs: Double? = null,
    @SerialName("rhr_bpm")                 val rhrBpm: Double? = null,
    @SerialName("spo2_pct")               val spo2Pct: Double? = null,       // null for older rows — skip in chart
    // Energy
    @SerialName("solar_kwh")              val solarKwh: Double? = null,
    @SerialName("self_sufficiency_pct")   val selfSufficiencyPct: Double? = null,
    @SerialName("energy_savings_gbp")     val energySavingsGbp: Double? = null,
    // Income
    @SerialName("income_mtd_gbp")         val incomeMtdGbp: Double? = null,
    @SerialName("income_30d_gbp")         val income30dGbp: Double? = null,
    @SerialName("income_gap_to_target_gbp") val incomeGapToTargetGbp: Double? = null,
    // Buddie
    @SerialName("ghost_actions_total")    val ghostActionsTotal: Int? = null,
    @SerialName("llm_cost_usd")           val llmCostUsd: Double? = null,
    @SerialName("calibrated_domains_count") val calibratedDomainsCount: Int? = null
)

@Serializable
data class GrowthResponse(
    val days: Int,
    val from: String,
    val to: String,
    val summary: GrowthSummary,
    val series: List<GrowthSeries> = emptyList()
)

// ── Retrofit interface ────────────────────────────────────────

interface VitaClawApiService {

    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("quota")
    suspend fun getQuota(): Response<QuotaResponse>

    @POST("ask")
    suspend fun askQuestion(@Body request: AskRequest): Response<AskResponse>

    @POST("chat")
    suspend fun sendChat(@Body request: ChatRequest): Response<ChatResponse>

    @GET("chat/opening")
    suspend fun getChatOpening(): Response<ChatOpeningResponse>

    @GET("chat/history")
    suspend fun getChatHistory(): Response<List<ChatHistoryEntry>>

    @GET("chat/offline/pending")
    suspend fun getChatOfflinePending(): Response<PendingOfflineResponse>

    @POST("chat/offline/ack/{job_id}")
    suspend fun ackOfflineMessage(
        @Path("job_id") jobId: String
    ): Response<Unit>

    @GET("intents")
    suspend fun getIntents(): Response<IntentsResponse>

    @GET("portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponse>

    @GET("portfolio/pies")
    suspend fun getPortfolioPies(): Response<PiesResponse>

    @GET("portfolio/dividend-data")
    suspend fun getDividendData(): Response<DividendDataResponse>

    @GET("portfolio/dca/{ticker}")
    suspend fun getDcaDetail(
        @Path("ticker") ticker: String
    ): Response<DcaDetailResponse>

    @GET("portfolio/orders")
    suspend fun getPortfolioOrders(
        @Query("tickers") tickers: String = "ALL",
        @Query("days")    days: Int = 90,
        @Query("limit")   limit: Int = 50
    ): Response<OrdersSummaryResponse>

    @GET("brief")
    suspend fun getBrief(): Response<BriefResponse>

    @GET("whoop")
    suspend fun getWhoop(
        @Query("date") date: String? = null
    ): Response<WhoopResponse>

    @GET("goals")
    suspend fun getGoals(): Response<GoalsResponse>

    @GET("energy")
    suspend fun getEnergy(): Response<EnergyResponse>

    @GET("buddie/observations/today")
    suspend fun getTodayObservations(): Response<ObservationsResponse>

    @POST("buddie/observations/{id}/feedback")
    suspend fun postObservationFeedback(
        @Path("id") id: Int,
        @Body body: FeedbackRequest
    ): Response<Unit>

    @GET("growth")
    suspend fun getGrowth(
        @Query("days")      days: Int? = null,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date")   toDate: String? = null
    ): Response<GrowthResponse>
}