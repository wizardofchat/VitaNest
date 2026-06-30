package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// VitaClawApiService — all data models + Retrofit interface
// Updated: BuddieInsights model fixed (new domain-keyed shape);
//          ObservationItem + DomainMemory updated (confidence_calibrated,
//          observation_type, domain_memory); GrowthResponse + GrowthSeries
//          added for /growth endpoint;
//          WhoopPatterns + timeline/detected/correlations models added
//          for /whoop?patterns=true — extends WhoopResponse with nullable
//          patterns field; getWhoopAnalytics updated to pass patterns=true;
//          Banking domain added — models + 2 endpoints (/banking/summary,
//          /banking/transactions) appended below TradeFeedbackResponse ☘️

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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

// ── Buddie NLP Query (skill_executor — separate stack from /chat) ──

@Serializable
data class BuddieQueryRequest(
    val question: String,
    val domain: String = "finance"
)

@Serializable
data class BuddieQueryProvenanceJob(
    val model: String = "",
    val cost: String = "",
    @SerialName("latency_ms") val latencyMs: Long = 0L
)

@Serializable
data class BuddieQueryProvenance(
    val tables: List<String> = emptyList(),
    val confidence: String = "",
    val boundary: String = "",                 // IN_DOMAIN | OUT_OF_DOMAIN | AMBIGUOUS
    @SerialName("boundary_confidence") val boundaryConfidence: Double = 0.0,
    @SerialName("total_latency_ms") val totalLatencyMs: Long = 0L
    // NOTE: llm_job1, llm_job2, react, manifest, polars deliberately omitted —
    // backend returns these as either booleans (manifest/polars/react path-taken
    // flags) or structured objects (model/cost/latency_ms) depending on which
    // execution path fired. A single typed field can't safely model both shapes.
    // Provenance detail UI degrades gracefully without them rather than crashing
    // deserialization. Revisit once VitaClaw's provenance schema is stable —
    // see CLAUDE.md skill_executor section.
)

@Serializable
data class BuddieQueryResponse(
    val answer: String = "",
    val domain: String = "finance",
    val answered: Boolean = true,
    @SerialName("react_triggered") val reactTriggered: Boolean = false,
    val provenance: BuddieQueryProvenance? = null,
    @SerialName("latency_ms") val latencyMs: Long = 0L
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
// Confirmed against live API (2026-06-25): /chat/offline/pending returns
// has_file: Boolean, NOT file_path. (The original contract doc described
// file_path on the single-job-by-id endpoint; that field is absent here.)
// has_file is sufficient — downloading only needs job_id, not a path.
@Serializable
data class PendingOfflineItem(
    @SerialName("job_id")       val jobId: String,
    val message: String,
    val response: String = "",
    val provenance: String = "",
    @SerialName("elapsed_ms")   val elapsedMs: Long = 0L,
    @SerialName("completed_at") val completedAt: String = "",
    val status: String = "",
    @SerialName("has_file")     val hasFile: Boolean = false
)

@Serializable
data class PendingOfflineResponse(
    @SerialName("pending_count") val pendingCount: Int,
    val jobs: List<PendingOfflineItem> = emptyList()
)

// ── Offline report job (order_pnl_report — first of N future reports) ──
// Contract: POST /chat/offline/report. No tickers means no request —
// there is no "--all" equivalent; VitaNest must always send an explicit
// non-empty ticker list (server returns 400 otherwise).
@Serializable
data class OfflineReportRequest(
    val tickers: List<String>,
    @SerialName("date_from") val dateFrom: String? = null,
    @SerialName("date_to")   val dateTo: String? = null,
    val top5: Boolean = false,
    @SerialName("rank_by")   val rankBy: String = "dca",
    val source: String = "vitanest"
)

@Serializable
data class OfflineReportSubmitResponse(
    @SerialName("job_id") val jobId: String,
    val status: String = "queued"
)

// ── Offline health report job (whoop_health_report — second report type) ──
// Contract: POST /chat/offline/health-report. No tickers — this report has
// a disjoint param shape from order_pnl_report (date range + detail dates +
// output formats, no tickers at all). detailDates defaults server-side to
// "most recent day" when omitted.
//
// formats is sent but VitaNest only ever surfaces the single "preferred"
// file from the response (file_path) — the backend's generic
// /chat/offline/job/{id}/file endpoint can only serve one file per job
// today, even when both xlsx+pdf were generated (file_paths has both, but
// there's no way to pick which via this endpoint yet). Requesting both
// formats is harmless server-side; VitaNest just won't expose a second
// download button until the backend adds per-format selection.
@Serializable
data class OfflineHealthReportRequest(
    @SerialName("date_from")    val dateFrom: String? = null,
    @SerialName("date_to")      val dateTo: String? = null,
    @SerialName("detail_dates") val detailDates: List<String>? = null,
    val formats: List<String> = listOf("xlsx", "pdf"),
    val source: String = "vitanest"
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
data class LensBreakdownItem(
    @SerialName("value_gbp")  val valueGbp: Double,
    @SerialName("weight_pct") val weightPct: Double
)

@Serializable
data class LensThresholds(
    @SerialName("concentration_warning_pct") val concentrationWarningPct: Double = 40.0,
    @SerialName("currency_warning_pct")      val currencyWarningPct: Double      = 60.0,
    @SerialName("geography_warning_pct")     val geographyWarningPct: Double     = 60.0,
    @SerialName("income_type_warning_pct")   val incomeTypeWarningPct: Double    = 60.0,
    @SerialName("asset_class_warning_pct")   val assetClassWarningPct: Double    = 80.0
)

@Serializable
data class PiesResponse(
    @SerialName("total_value_gbp")       val totalValueGbp: Double,
    @SerialName("total_pnl_gbp")         val totalPnlGbp: Double,
    @SerialName("total_cash_gbp")        val totalCashGbp: Double,
    @SerialName("fetched_at")            val fetchedAt: String = "",
    val pies: List<PieItem>              = emptyList(),
    // ── New enriched breakdown blocks ─────────────────────────
    @SerialName("currency_breakdown")
    val currencyBreakdown: Map<String, LensBreakdownItem>?    = null,
    @SerialName("geography_breakdown")
    val geographyBreakdown: Map<String, LensBreakdownItem>?   = null,
    @SerialName("income_type_breakdown")
    val incomeTypeBreakdown: Map<String, LensBreakdownItem>?  = null,
    @SerialName("asset_class_breakdown")
    val assetClassBreakdown: Map<String, LensBreakdownItem>?  = null,
    val thresholds: LensThresholds?                           = null
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
    val tickers: List<String>           = emptyList(),
    // ── New enriched classification fields ────────────────────
    @SerialName("asset_class")          val assetClass: String? = null,   // etf|trust|reit|commodity|mixed
    @SerialName("income_type")          val incomeType: String? = null,   // covered_call|income|growth|commodity|mixed
    val geography: String?              = null,                            // us|uk|global|em|mixed
    val currency: String?               = null                             // GBP|USD|GBX
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
    @SerialName("last_workout")       val lastWorkout: String = "",
    // ── Analytics fields — null on day-view calls ──────────────
    @SerialName("series")             val series:     WhoopAnalyticsSeries? = null,
    @SerialName("baselines")          val baselines:  WhoopBaselines?       = null,
    @SerialName("alerts")             val alerts:     List<WhoopAlert>      = emptyList(),
    @SerialName("thresholds")         val thresholds: WhoopThresholds?      = null,
    @SerialName("range")              val range:      String?               = null,
    // ── Patterns — null unless patterns=true passed ────────────
    @SerialName("patterns")           val patterns:   WhoopPatterns?        = null
)

// ── Whoop analytics (GET /whoop?range=) ──────────────────────
// Nullable fields — day-view calls (/whoop?date=) return null for these.

@Serializable
data class WhoopAnalyticsSeries(
    @SerialName("dates")          val dates:        List<String>,
    @SerialName("recovery")       val recovery:     List<Double?>,
    @SerialName("hrv_ms")         val hrvMs:        List<Double?>,
    @SerialName("rhr_bpm")        val rhrBpm:       List<Double?>,
    @SerialName("spo2_pct")       val spo2Pct:      List<Double?>,
    @SerialName("strain")         val strain:       List<Double?>,
    @SerialName("sleep_debt_min") val sleepDebtMin: List<Double?>
)

@Serializable
data class WhoopBaselines(
    @SerialName("spo2_30d_avg")        val spo230dAvg:        String,
    @SerialName("hrv_30d_avg_ms")      val hrv30dAvgMs:       String,
    @SerialName("rhr_30d_avg_bpm")     val rhr30dAvgBpm:      String,
    @SerialName("recovery_30d_avg")    val recovery30dAvg:    String,
    @SerialName("respiratory_30d_avg") val respiratory30dAvg: String = ""
)

@Serializable
data class WhoopThresholds(
    @SerialName("spo2_critical_pct")    val spo2CriticalPct:   Int,
    @SerialName("spo2_warning_pct")     val spo2WarningPct:    Int,
    @SerialName("hrv_suppressed_ms")    val hrvSuppressedMs:   Int,
    @SerialName("strain_high")          val strainHigh:        Double,
    @SerialName("sleep_debt_alert_min") val sleepDebtAlertMin: Int
)

@Serializable
data class WhoopAlertContext(
    @SerialName("date")        val date:       String? = null,
    @SerialName("spo2")        val spo2:       Double? = null,
    @SerialName("hrv")         val hrv:        Double? = null,
    @SerialName("prev_strain") val prevStrain: Double? = null,
    @SerialName("days_count")  val daysCount:  Int?    = null
)

// Custom serialiser — "context" field is polymorphic (list or object)
@Serializable(with = WhoopAlertSerializer::class)
data class WhoopAlert(
    val severity:    String,
    val rule:        String,
    val message:     String,
    val contextList: List<WhoopAlertContext> = emptyList()
)

object WhoopAlertSerializer : KSerializer<WhoopAlert> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("WhoopAlert")

    override fun serialize(encoder: Encoder, value: WhoopAlert) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: throw SerializationException("WhoopAlert requires JsonEncoder")
        val obj = kotlinx.serialization.json.buildJsonObject {
            put("severity", kotlinx.serialization.json.JsonPrimitive(value.severity))
            put("rule",     kotlinx.serialization.json.JsonPrimitive(value.rule))
            put("message",  kotlinx.serialization.json.JsonPrimitive(value.message))
            put("context",  kotlinx.serialization.json.JsonArray(emptyList()))
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): WhoopAlert {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("WhoopAlert requires JsonDecoder")
        val obj      = jsonDecoder.decodeJsonElement().jsonObject
        val severity = obj["severity"]?.jsonPrimitive?.content ?: ""
        val rule     = obj["rule"]?.jsonPrimitive?.content ?: ""
        val message  = obj["message"]?.jsonPrimitive?.content ?: ""
        val ctx      = obj["context"]
        val contextList: List<WhoopAlertContext> = when {
            ctx == null || ctx is JsonNull -> emptyList()
            ctx is JsonArray -> ctx.map { parseAlertContext(it.jsonObject) }
            ctx is JsonObject -> listOf(parseAlertContext(ctx))
            else -> emptyList()
        }
        return WhoopAlert(severity, rule, message, contextList)
    }

    private fun parseAlertContext(obj: JsonObject): WhoopAlertContext {
        fun str(k: String)  = obj[k]?.jsonPrimitive?.content
        fun dbl(k: String)  = obj[k]?.jsonPrimitive?.doubleOrNull
        fun int_(k: String) = obj[k]?.jsonPrimitive?.intOrNull
        return WhoopAlertContext(
            date       = str("date"),
            spo2       = dbl("spo2"),
            hrv        = dbl("hrv"),
            prevStrain = dbl("prev_strain"),
            daysCount  = int_("days_count")
        )
    }
}

@Serializable
data class WhoopSynthesisResponse(
    @SerialName("range")       val range:      String,
    @SerialName("synthesis")   val synthesis:  String,
    @SerialName("data_points") val dataPoints: Int,
    @SerialName("cost_usd")    val costUsd:    Double
)

// ── Whoop Patterns (GET /whoop?patterns=true&range=) ──────────
// Extends WhoopResponse — null unless patterns=true is passed.
// timeline: one entry per day in the window.
// detected: cross-day pattern signals, ordered by severity.
// correlations: always 30d window regardless of range toggle.
// health_state: derived status summary — null if not enough data.

@Serializable
data class WhoopHealthState(
    @SerialName("status") val status: String,   // "green" | "suppressed" | "warning" | "critical"
    @SerialName("label")  val label:  String,   // "Recovery suppressed"
    @SerialName("reason") val reason: String,   // one-line explanation
    @SerialName("zone")   val zone:   String    // "green" | "amber" | "red"
)

@Serializable
data class WhoopPatterns(
    @SerialName("window_days")   val windowDays:   Int,
    @SerialName("timeline")      val timeline:     List<WhoopTimelineDay>,
    @SerialName("detected")      val detected:     List<WhoopDetectedPattern>,
    @SerialName("correlations")  val correlations: WhoopCorrelations,
    @SerialName("health_state")  val healthState:  WhoopHealthState? = null
)

@Serializable
data class WhoopTimelineDay(
    @SerialName("date")     val date:    String,
    @SerialName("recovery") val recovery: WhoopZoneValue? = null,
    @SerialName("hrv_ms")   val hrvMs:   WhoopZoneValue? = null,
    @SerialName("rhr_bpm")  val rhrBpm:  WhoopZoneValue? = null,
    @SerialName("spo2_pct") val spo2Pct: WhoopZoneValue? = null,
    @SerialName("strain")   val strain:  WhoopZoneValue? = null,
    @SerialName("workout")  val workout: WhoopWorkout?   = null
)

@Serializable
data class WhoopZoneValue(
    @SerialName("value") val value: Double,
    @SerialName("zone")  val zone:  String   // "green" | "amber" | "red"
)

@Serializable
data class WhoopWorkout(
    @SerialName("activity")    val activity:   String?  = null,
    @SerialName("emoji")       val emoji:      String?  = null,
    @SerialName("zone45_pct")  val zone45Pct:  Int?     = null,
    @SerialName("strain")      val strain:     Double?  = null,
    @SerialName("trigger")     val trigger:    Boolean  = false,
    @SerialName("overreach")   val overreach:  Boolean  = false,
    @SerialName("collapse")    val collapse:   Boolean  = false
)

@Serializable
data class WhoopDetectedPattern(
    @SerialName("pattern")    val pattern:   String,
    @SerialName("severity")   val severity:  String,            // "critical" | "warning"
    @SerialName("date_start") val dateStart: String? = null,    // multi-day patterns
    @SerialName("date_end")   val dateEnd:   String? = null,
    @SerialName("date")       val date:      String? = null,    // single-day patterns
    @SerialName("evidence")   val evidence:  String
)

@Serializable
data class WhoopCorrelations(
    @SerialName("window_days")              val windowDays:             Int,
    @SerialName("spo2_vs_rhr")              val spo2VsRhr:              Double,
    @SerialName("hrv_vs_recovery")          val hrvVsRecovery:          Double,
    @SerialName("strain_vs_recovery_lag1")  val strainVsRecoveryLag1:   Double
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

// ── Income Stress (POST /portfolio/income-stress) ─────────────

@Serializable
data class IncomeStressRequest(
    val scenario:             String = "vol_collapse",
    @SerialName("shock_pct") val shockPct: Double = -10.0
)

@Serializable
data class IncomeStressCurrent(
    @SerialName("total_value_gbp")    val totalValueGbp: Double,
    @SerialName("monthly_income_gbp") val monthlyIncomeGbp: Double
)

@Serializable
data class IncomeStressTypeResult(
    @SerialName("weight_pct")          val weightPct: Double         = 0.0,
    @SerialName("base_income_gbp")     val baseIncomeGbp: Double     = 0.0,
    @SerialName("stressed_income_gbp") val stressedIncomeGbp: Double = 0.0,
    @SerialName("income_change_pct")   val incomeChangePct: Double   = 0.0,
    @SerialName("base_value_gbp")      val baseValueGbp: Double      = 0.0,
    @SerialName("stressed_value_gbp")  val stressedValueGbp: Double  = 0.0
)

@Serializable
data class IncomeStressStressed(
    @SerialName("total_value_gbp")    val totalValueGbp: Double,
    @SerialName("monthly_income_gbp") val monthlyIncomeGbp: Double,
    @SerialName("income_change_pct")  val incomeChangePct: Double,
    @SerialName("income_floor_gbp")   val incomeFloorGbp: Double,
    @SerialName("stability_score")    val stabilityScore: Int,
    @SerialName("by_type")            val byType: Map<String, IncomeStressTypeResult> = emptyMap(),
    @SerialName("key_insight")        val keyInsight: String = ""
)

@Serializable
data class IncomeStressResponse(
    val scenario:                   String,
    @SerialName("scenario_label")  val scenarioLabel: String  = "",
    @SerialName("shock_pct")       val shockPct: Double,
    val estimated:                  Boolean                   = true,
    val phase:                      Int                       = 1,
    val current:                    IncomeStressCurrent,
    val stressed:                   IncomeStressStressed,
    @SerialName("calculated_at")   val calculatedAt: String   = ""
)


// ── Finance Analytics (/finance/analytics?range=) ─────────────
// Nullable series fields — gaps in snapshot data render as empty, never 0.
// alerts reuse WhoopAlert model — same shape confirmed from API.
// ok severity alerts suppressed in VitaNest — warnings/criticals only.

@Serializable
data class FinanceAnalyticsSeries(
    @SerialName("dates")          val dates:        List<String>,
    @SerialName("equity_gbp")     val equityGbp:    List<Double?>,
    @SerialName("pnl_gbp")        val pnlGbp:       List<Double?>,
    @SerialName("income_30d_gbp") val income30dGbp: List<Double?>,
    @SerialName("deposits_mtd")   val depositsMtd:  List<Double?>
)

@Serializable
data class FinanceAnalyticsThresholds(
    @SerialName("income_target_gbp")    val incomeTargetGbp:   Double,
    @SerialName("pnl_warning_drop_gbp") val pnlWarningDropGbp: Double
)


// ── Finance Patterns (embedded in FinanceAnalyticsResponse) ───
// Returned when VitaClaw computes patterns block.
// correlations shape differs from Whoop — finance-specific fields.
// min_data_points_met guards against rendering weak correlations.

@Serializable
data class FinanceCorrelations(
    @SerialName("window_days")            val windowDays:           Int,
    @SerialName("deposits_vs_income")     val depositsVsIncome:     Double?  = null,
    @SerialName("income_consistency")     val incomeConsistency:    Double?  = null,
    @SerialName("min_data_points_met")    val minDataPointsMet:     Map<String, Boolean> = emptyMap(),
    @SerialName("data_points")            val dataPoints:           Map<String, Int>     = emptyMap()
)

@Serializable
data class FinanceHealthState(
    @SerialName("status") val status: String,   // "green" | "warning" | "critical"
    @SerialName("label")  val label:  String,
    @SerialName("reason") val reason: String,
    @SerialName("zone")   val zone:   String    // "green" | "amber" | "red"
)

@Serializable
data class FinanceDetectedPattern(
    @SerialName("pattern")  val pattern:  String,
    @SerialName("severity") val severity: String,   // "critical" | "warning"
    @SerialName("evidence") val evidence: String
)

@Serializable
data class FinancePatterns(
    @SerialName("health_state")  val healthState:  FinanceHealthState?       = null,
    @SerialName("correlations")  val correlations: FinanceCorrelations?       = null,
    @SerialName("detected")      val detected:     List<FinanceDetectedPattern> = emptyList()
)

@Serializable
data class FinanceAnalyticsResponse(
    @SerialName("equity_gbp")        val equityGbp:       Double,
    @SerialName("pnl_gbp")           val pnlGbp:          Double,
    @SerialName("income_30d_gbp")    val income30dGbp:    Double,
    @SerialName("income_target_gbp") val incomeTargetGbp: Double,
    @SerialName("income_gap_gbp")    val incomeGapGbp:    Double,
    @SerialName("series")            val series:          FinanceAnalyticsSeries?,
    @SerialName("alerts")            val alerts:          List<WhoopAlert>         = emptyList(),
    @SerialName("thresholds")        val thresholds:      FinanceAnalyticsThresholds?,
    @SerialName("range")             val range:           String?                  = null,
    @SerialName("patterns")          val patterns:        FinancePatterns?         = null
)

// Finance synthesis reuses same shape as WhoopSynthesisResponse.
// Confirmed from curl: range, synthesis, data_points, cost_usd, generated_at.

@Serializable
data class FinanceSynthesisResponse(
    @SerialName("range")        val range:       String,
    @SerialName("synthesis")    val synthesis:   String,
    @SerialName("data_points")  val dataPoints:  Int,
    @SerialName("cost_usd")     val costUsd:     Double,
    @SerialName("generated_at") val generatedAt: String = ""
)


// ── Buddie Trade (/buddie/trades, /buddie/budget, /buddie/candidates) ────────
// Replaces old /buddie/paper-trade/latest + /buddie/paper-trade/run
// Three separate endpoints — fetched independently on Trade tab load

@Serializable
data class BuddieTradeItem(
    val id: Int,
    @SerialName("trade_date")            val tradeDate: String,
    val month: String,
    val ticker: String,
    val shares: Double? = null,                          // null for growth trades
    @SerialName("price_gbp")             val priceGbp: Double = 0.0,
    @SerialName("capital_gbp")           val capitalGbp: Double,
    @SerialName("ex_div_date")           val exDivDate: String? = null,   // null for growth trades
    @SerialName("payment_date")          val paymentDate: String? = null, // null for growth trades
    @SerialName("expires_at")            val expiresAt: String? = null,   // null for growth trades
    @SerialName("projected_income_gbp")  val projectedIncomeGbp: Double = 0.0,
    @SerialName("actual_income_gbp")     val actualIncomeGbp: Double? = null,
    val rationale: String = "",
    @SerialName("trade_type")            val tradeType: String = "paper_buy",
    val status: String,
    @SerialName("created_at")            val createdAt: String = ""
)

@Serializable
data class BuddieTradesResponse(
    @SerialName("trade_count")   val tradeCount: Int,
    @SerialName("active_count")  val activeCount: Int,
    @SerialName("expired_count") val expiredCount: Int,
    val trades: List<BuddieTradeItem> = emptyList()
)

@Serializable
data class BuddieBudgetMonth(
    val month: String,
    @SerialName("opening_gbp")         val openingGbp: Double,
    @SerialName("spent_gbp")           val spentGbp: Double,
    @SerialName("remaining_gbp")       val remainingGbp: Double,
    @SerialName("income_earned_gbp")   val incomeEarnedGbp: Double,
    @SerialName("income_target_gbp")   val incomeTargetGbp: Double,
    @SerialName("online_llm_cost_gbp") val onlineLlmCostGbp: Double = 0.0,
    @SerialName("target_pct")          val targetPct: Double
)

@Serializable
data class BuddieBudgetResponse(
    @SerialName("current_month")  val currentMonth: String,
    @SerialName("remaining_gbp")  val remainingGbp: Double,
    @SerialName("target_pct")     val targetPct: Double,
    val budgets: List<BuddieBudgetMonth> = emptyList()
)

@Serializable
data class BuddieCandidateItem(
    val ticker: String,
    @SerialName("capital_gbp")      val capitalGbp: Double,
    @SerialName("projected_income") val projectedIncome: Double,
    @SerialName("annual_yield")     val annualYield: Double,
    @SerialName("ex_div_date")      val exDivDate: String,
    @SerialName("days_to_exdiv")    val daysToExDiv: Int,
    val confirmed: Boolean,
    val selected: Boolean
)

@Serializable
data class BuddieExcludedItem(
    val ticker: String,
    val reason: String
)

@Serializable
data class BuddieCandidatesResponse(
    @SerialName("generated_at")    val generatedAt: String,
    val month: String,
    val selected: String,
    @SerialName("total_evaluated") val totalEvaluated: Int,
    @SerialName("passed_count")    val passedCount: Int,
    @SerialName("excluded_count")  val excludedCount: Int,
    val candidates: List<BuddieCandidateItem> = emptyList(),
    val excluded: List<BuddieExcludedItem> = emptyList()
)

// ── Buddie Growth Candidates (/buddie/candidates?track=growth) ────────────────
// Different shape to income — capital return fields, no yield/ex-div

@Serializable
data class BuddieGrowthCandidateItem(
    val ticker: String,
    val score: Double,
    @SerialName("capital_return_pct")  val capitalReturnPct: Double,
    @SerialName("momentum_proxy")      val momentumProxy: Double,
    val conviction: Double,
    @SerialName("rsi_14")              val rsi14: Double,
    @SerialName("price_vs_52w_high")   val priceVs52wHigh: Double,
    @SerialName("holding_days")        val holdingDays: Int,
    @SerialName("order_count")         val orderCount: Int,
    @SerialName("trade_approval")      val tradeApproval: String,   // "free" | "required"
    val selected: Boolean
)

@Serializable
data class BuddieGrowthCandidatesResponse(
    @SerialName("generated_at")    val generatedAt: String,
    val month: String,
    val track: String,
    val selected: String,
    @SerialName("total_evaluated") val totalEvaluated: Int,
    @SerialName("passed_count")    val passedCount: Int,
    @SerialName("last_updated")    val lastUpdated: String = "",
    @SerialName("is_stale")        val isStale: Boolean = false,
    @SerialName("stale_days")      val staleDays: Int = 0,
    val candidates: List<BuddieGrowthCandidateItem> = emptyList()
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

    @POST("buddie/query")
    suspend fun postBuddieQuery(@Body request: BuddieQueryRequest): Response<BuddieQueryResponse>

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

    // ── Reports (order_pnl_report + whoop_health_report. Both share the
    //    same job_id/poll lifecycle as Dolphin chat jobs above, extended
    //    with a downloadable file via the one generic /file endpoint) ──

    @POST("chat/offline/report")
    suspend fun submitOfflineReport(
        @Body request: OfflineReportRequest
    ): Response<OfflineReportSubmitResponse>

    @POST("chat/offline/health-report")
    suspend fun submitOfflineHealthReport(
        @Body request: OfflineHealthReportRequest
    ): Response<OfflineReportSubmitResponse>

    // Raw bytes — check PendingOfflineItem.hasFile first; a 404 here
    // means the job has no file or it no longer exists on disk.
    @Streaming
    @GET("chat/offline/job/{job_id}/file")
    suspend fun downloadOfflineJobFile(
        @Path("job_id") jobId: String
    ): Response<ResponseBody>

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

    @POST("portfolio/income-stress")
    suspend fun runIncomeStress(
        @Body request: IncomeStressRequest
    ): Response<IncomeStressResponse>

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
    suspend fun getTodayObservations(
        @Query("obs_date") obsDate: String? = null
    ): Response<ObservationsResponse>

    @POST("buddie/observations/{id}/feedback")
    suspend fun postObservationFeedback(
        @Path("id") id: Int,
        @Body body: FeedbackRequest
    ): Response<Unit>

    // patterns=true — fetches timeline, detected patterns, correlations
    // Always pass patterns=true from HealthAnalyticsScreen
    @GET("whoop")
    suspend fun getWhoopAnalytics(
        @Query("range")    range:    String,
        @Query("patterns") patterns: Boolean = true
    ): Response<WhoopResponse>

    @GET("whoop/synthesis")
    suspend fun getWhoopSynthesis(
        @Query("range") range: String
    ): Response<WhoopSynthesisResponse>

    @GET("growth")
    suspend fun getGrowth(
        @Query("days")      days: Int? = null,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date")   toDate: String? = null
    ): Response<GrowthResponse>
    @GET("finance/analytics")
    suspend fun getFinanceAnalytics(
        @Query("range") range: String
    ): Response<FinanceAnalyticsResponse>

    @GET("finance/synthesis")
    suspend fun getFinanceSynthesis(
        @Query("range") range: String
    ): Response<FinanceSynthesisResponse>


    @GET("buddie/trades")
    suspend fun getBuddieTrades(
        @Query("month")  month:  String? = null,
        @Query("status") status: String? = null
    ): Response<BuddieTradesResponse>

    @GET("buddie/budget")
    suspend fun getBuddieBudget(
        @Query("months") months: Int = 3
    ): Response<BuddieBudgetResponse>

    @GET("buddie/candidates")
    suspend fun getBuddieCandidates(
        @Query("track") track: String = "income"
    ): Response<BuddieCandidatesResponse>

    @GET("buddie/candidates")
    suspend fun getBuddieGrowthCandidates(
        @Query("track") track: String = "growth"
    ): Response<BuddieGrowthCandidatesResponse>

    @POST("buddie/trades/{id}/feedback")
    suspend fun postTradeFeedback(
        @Path("id")      id:     Int,
        @Query("action") action: String   // "executed" | "skipped"
    ): Response<TradeFeedbackResponse>

    // ── Banking endpoints ─────────────────────────────────────
    // month=null  → current month (full response with trend + net_worth)
    // month=YYYY-MM → single month flat response
    // month=all   → all months map

    @GET("banking/summary")
    suspend fun getBankingSummary(
        @Query("month") month: String? = null
    ): Response<BankingSummaryResponse>

    @GET("banking/summary")
    suspend fun getBankingAllMonths(
        @Query("month") month: String = "all"
    ): Response<BankingAllMonthsResponse>

    @GET("banking/transactions")
    suspend fun getBankingTransactions(
        @Query("month")    month:    String? = null,
        @Query("category") category: String? = null,
        @Query("view")     view:     String? = null,
        @Query("sort")     sort:     String? = null
    ): Response<BankingTransactionsResponse>

}

// ── Trade feedback response ───────────────────────────────────
// Returned on 200 from POST /buddie/trades/{id}/feedback
// status field used to update card locally — no full list refresh needed

@Serializable
data class TradeFeedbackResponse(
    val id:                             Int,
    val ticker:                         String,
    val status:                         String,   // "executed" | "skipped"
    val message:                        String,
    @SerialName("projected_income_gbp") val projectedIncomeGbp: Double
)
// ── Banking ───────────────────────────────────────────────────
// Models match live /banking/summary and /banking/transactions shapes.
// Fields with defaults handle optional API fields gracefully.
// internal_transfers_gbp deserialized but not shown in UI.

@Serializable
data class BankingAnomaly(
    val category: String,
    @SerialName("amount")    val amount: Double,
    @SerialName("avg_3m")    val avg3m: Double,
    @SerialName("delta_pct") val deltaPct: Double
)

@Serializable
data class BankingCurrent(
    @SerialName("income_gbp")             val incomeGbp: Double,
    @SerialName("expenses_gbp")           val expensesGbp: Double,
    @SerialName("investment_funding_gbp") val investmentFundingGbp: Double,
    @SerialName("tax_provision_gbp")      val taxProvisionGbp: Double = 0.0,
    @SerialName("committed_savings_gbp")  val committedSavingsGbp: Double = 0.0,
    @SerialName("internal_transfers_gbp") val internalTransfersGbp: Double = 0.0,
    @SerialName("surplus_gbp")            val surplusGbp: Double,
    @SerialName("true_discretionary_gbp") val trueDiscretionaryGbp: Double,
    @SerialName("top_categories")         val topCategories: Map<String, Double> = emptyMap(),
    val anomalies: List<BankingAnomaly> = emptyList(),
    @SerialName("transaction_count")      val transactionCount: Int = 0
)

@Serializable
data class BankingTrend(
    @SerialName("surplus_3m_avg")                 val surplus3mAvg: Double,
    @SerialName("surplus_direction")              val surplusDirection: String,
    @SerialName("surplus_by_month")               val surplusByMonth: Map<String, Double> = emptyMap(),
    @SerialName("investment_funding_ytd")         val investmentFundingYtd: Double,
    @SerialName("investment_funding_monthly_avg") val investmentFundingMonthlyAvg: Double,
    @SerialName("deployment_rate_pct")            val deploymentRatePct: Double,
    @SerialName("cash_idle_days_avg")             val cashIdleDaysAvg: Double? = null
)

@Serializable
data class BankingNetWorth(
    @SerialName("total_gbp")       val totalGbp: Double,
    @SerialName("cash_gbp")        val cashGbp: Double,
    @SerialName("portfolio_gbp")   val portfolioGbp: Double,
    @SerialName("cash_by_account") val cashByAccount: Map<String, Double> = emptyMap()
)

@Serializable
data class BankingMonthSummary(
    @SerialName("income_gbp")             val incomeGbp: Double,
    @SerialName("expenses_gbp")           val expensesGbp: Double,
    @SerialName("surplus_gbp")            val surplusGbp: Double,
    @SerialName("investment_funding_gbp") val investmentFundingGbp: Double,
    @SerialName("deployment_rate_pct")    val deploymentRatePct: Double
)

@Serializable
data class BankingSummaryResponse(
    @SerialName("generated_at")        val generatedAt: String = "",
    @SerialName("months_available")    val monthsAvailable: Int = 0,
    val accounts: List<String>         = emptyList(),
    @SerialName("current_month")       val currentMonth: String = "",
    @SerialName("expenses_incomplete") val expensesIncomplete: Boolean = false,
    @SerialName("missing_accounts")    val missingAccounts: List<String> = emptyList(),
    @SerialName("is_stale")            val isStale: Boolean = false,
    @SerialName("stale_days")          val staleDays: Int? = null,
    // Full current-month response
    val current: BankingCurrent? = null,
    val trend: BankingTrend? = null,
    @SerialName("net_worth")           val netWorth: BankingNetWorth? = null,
    // Single-month flat response (?month=YYYY-MM)
    val month: String? = null,
    @SerialName("income_gbp")             val incomeGbp: Double? = null,
    @SerialName("expenses_gbp")           val expensesGbp: Double? = null,
    @SerialName("surplus_gbp")            val surplusGbp: Double? = null,
    @SerialName("true_discretionary_gbp") val trueDiscretionaryGbp: Double? = null,
    @SerialName("investment_funding_gbp") val investmentFundingGbp: Double? = null,
    @SerialName("deployment_rate_pct")    val deploymentRatePct: Double? = null,
    @SerialName("top_categories")         val topCategories: Map<String, Double>? = null,
    @SerialName("transaction_count")      val transactionCount: Int? = null,
    // All-months response (?month=all)
    val months: Map<String, BankingMonthSummary>? = null
)

@Serializable
data class BankingAllMonthsResponse(
    val month: String = "all",
    val months: Map<String, BankingMonthSummary> = emptyMap()
)

@Serializable
data class BankingFilters(
    val month: String? = null,
    val category: String? = null,
    val view: String? = null,
    val sort: String = "desc"
)

@Serializable
data class BankingTransaction(
    val date: String,
    val description: String,
    @SerialName("amount_gbp") val amountGbp: Double,
    val category: String,
    @SerialName("account_id") val accountId: String
)

@Serializable
data class BankingTransactionsResponse(
    val filters: BankingFilters,
    @SerialName("total_gbp")         val totalGbp: Double,
    @SerialName("transaction_count") val transactionCount: Int,
    val transactions: List<BankingTransaction> = emptyList()
)