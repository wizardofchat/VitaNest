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
    val context: String? = ""
)

@Serializable
data class AskResponse(
    val answer: String,
    val sources: List<String>? = emptyList(),
    val sentiment: String? = null
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
    @SerialName("recovery_score") val recoveryScore: Float = 0f,
    @SerialName("resting_heart_rate") val restingHeartRate: Float = 0f,
    @SerialName("hrv_rmssd_milli") val hrvRmssdMilli: Float = 0f,
    @SerialName("spo2_percentage") val spo2Percentage: Float = 0f,
    @SerialName("skin_temp_celsius") val skinTempCelsius: Float = 0f,
    @SerialName("last_updated") val lastUpdated: String = ""
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
}