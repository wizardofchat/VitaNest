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

interface VitaClawApiService {
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @POST("ask")
    suspend fun askQuestion(@Body request: AskRequest): Response<AskResponse>

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
}