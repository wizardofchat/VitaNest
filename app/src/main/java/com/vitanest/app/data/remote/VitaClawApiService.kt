package com.vitanest.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: String,
    @SerialName("agentic_score") val agenticScore: Int
)

@Serializable
data class AskRequest(
    val query: String,
    val source: String = "vitanest",   // mandatory per API contract 2026-04-22
    val context: String? = ""
)

@Serializable
data class AskResponse(
    val answer: String,
    val sources: List<String>? = emptyList(),
    val sentiment: String? = null,
    val tier: String? = null           // "polars" | "vitanest_blocked" etc
)

@Serializable
data class ChatRequest(
    val message: String,
    val source: String = "vitanest"
)

@Serializable
data class ChatResponse(
    val response: String,
    val tier: String,
    val provenance: String,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L
)


// ── ADD to VitaClawApiService.kt — after ChatResponse ────────

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

@Serializable
data class PortfolioResponse(
    @SerialName("total_value_gbp") val totalValueGbp: Double,
    @SerialName("daily_pnl_gbp") val dailyPnLGbp: Double? = 0.0,
    @SerialName("holdings") val positions: List<Position> = emptyList()
)

@Serializable
data class Position(
    val ticker: String,
    val name: String = "",
    @SerialName("quantity") val quantity: Double = 0.0,
    @SerialName("value_gbp") val marketValue: Double,
    @SerialName("pnl_gbp") val pnlPercent: Double = 0.0
)

@Serializable
data class PiesResponse(
    @SerialName("total_value_gbp") val totalValueGbp: Double,
    @SerialName("total_pnl_gbp") val totalPnlGbp: Double,
    @SerialName("total_cash_gbp") val totalCashGbp: Double,
    @SerialName("fetched_at") val fetchedAt: String = "",
    val pies: List<PieItem> = emptyList()
)

@Serializable
data class PieItem(
    val id: Int? = null,
    val name: String,
    @SerialName("value_gbp") val valueGbp: Double,
    @SerialName("pnl_gbp") val pnlGbp: Double,
    @SerialName("pnl_pct") val pnlPct: Double,
    @SerialName("cash_gbp") val cashGbp: Double,
    @SerialName("weight_pct") val weightPct: Double,
    @SerialName("holdings_count") val holdingsCount: Int,
    val status: String? = null,
    @SerialName("dividends_gained_gbp") val dividendsGainedGbp: Double = 0.0,
    val tickers: List<String> = emptyList()
)

@Serializable
data class BriefResponse(
    val summary: String,
    @SerialName("last_updated") val lastUpdated: String
)

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

@Serializable
data class DividendNextUpcoming(
    @SerialName("ex_div_date_earliest") val exDivDateEarliest: String?,
    @SerialName("ex_div_date_latest") val exDivDateLatest: String?,
    @SerialName("payment_date_earliest") val paymentDateEarliest: String?,
    @SerialName("confirmed") val confirmed: Boolean
)

@Serializable
data class DividendHolding(
    @SerialName("ticker") val ticker: String,
    @SerialName("name") val name: String,
    @SerialName("currency") val currency: String,
    @SerialName("frequency") val frequency: String?,
    @SerialName("avg_per_share") val avgPerShare: Double?,
    @SerialName("avg_calculated_from") val avgCalculatedFrom: Int,
    @SerialName("data_quality") val dataQuality: String,
    @SerialName("data_quality_notes") val dataQualityNotes: String?,
    @SerialName("days_until_ex_div") val daysUntilExDiv: Int?,
    @SerialName("is_dividend_payer") val isDividendPayer: Boolean,
    @SerialName("next_upcoming") val nextUpcoming: DividendNextUpcoming?,
    @SerialName("price_gbp") val priceGbp: Double? = null,
    @SerialName("quantity") val quantity: Double? = null,
    @SerialName("value_gbp") val valueGbp: Double? = null
)

@Serializable
data class DividendDataResponse(
    @SerialName("fetched_at") val fetchedAt: String,
    @SerialName("total_tickers_tracked") val totalTickersTracked: Int,
    @SerialName("tickers") val tickers: List<DividendHolding>
)

@Serializable
data class GoalsResponse(
    @SerialName("income_target_gbp")       val incomeTargetGbp: Double,
    @SerialName("income_target_date")      val incomeTargetDate: String,
    @SerialName("withholding_tax_factor")  val withholdingTaxFactor: Double,
    @SerialName("current_gbp")             val currentGbp: Double
)

interface VitaClawApiService {
    @GET("health")

    suspend fun getHealth(): Response<HealthResponse>

    @POST("ask")
    suspend fun askQuestion(@Body request: AskRequest): Response<AskResponse>

    @POST("chat")
    suspend fun sendChat(@Body request: ChatRequest): Response<ChatResponse>

    @GET("portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponse>

    @GET("portfolio/pies")
    suspend fun getPortfolioPies(): Response<PiesResponse>

    @GET("brief")
    suspend fun getBrief(): Response<BriefResponse>

    @GET("whoop")
    suspend fun getWhoop(): Response<WhoopResponse>

    @GET("portfolio/dividend-data")
    suspend fun getDividendData(): Response<DividendDataResponse>

    @GET("goals")
    suspend fun getGoals(): Response<GoalsResponse>

    @GET("energy")
    suspend fun getEnergy(): Response<EnergyResponse>

    // ADD to VitaClawApiService interface:
    @GET("quota")
    suspend fun getQuota(): Response<QuotaResponse>
}



@Serializable
data class EnergyResponse(
    val date: String,
    @SerialName("solar_generated_kwh")    val solarGeneratedKwh: Double?,
    @SerialName("self_consumed_kwh")      val selfConsumedKwh: Double?,
    @SerialName("solar_exported_kwh")     val solarExportedKwh: Double?,
    @SerialName("grid_imported_kwh")      val gridImportedKwh: Double?,
    @SerialName("ev_total_kwh")           val evTotalKwh: Double?,
    @SerialName("ev_solar_kwh")           val evSolarKwh: Double?,
    @SerialName("ev_grid_kwh")            val evGridKwh: Double?,
    @SerialName("eddi_solar_kwh")         val eddiSolarKwh: Double?,
    @SerialName("eddi_boosted_kwh")       val eddiBoostedKwh: Double?,
    @SerialName("home_consumption_kwh")   val homeConsumptionKwh: Double?,
    @SerialName("ev_charging_cost_gbp")   val evChargingCostGbp: Double?,
    @SerialName("home_import_cost_gbp")   val homeImportCostGbp: Double?,
    @SerialName("export_earnings_gbp")    val exportEarningsGbp: Double?,
    @SerialName("solar_savings_gbp")      val solarSavingsGbp: Double?,
    @SerialName("energy_cost_savings_gbp") val energyCostSavingsGbp: Double?,
    @SerialName("total_cost_gbp")         val totalCostGbp: Double?,
    @SerialName("tariff_peak_pence")      val tariffPeakPence: Double?,
    @SerialName("tariff_cheap_pence")     val tariffCheapPence: Double?,
    @SerialName("tariff_export_pence")    val tariffExportPence: Double?,
    @SerialName("charge_mode")            val chargeMode: String?,
    @SerialName("last_updated")           val lastUpdated: String
    // plug_state not yet in API — add when VitaClaw captures PLUG_STATES
)