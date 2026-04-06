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

// Inside VitaClawApiService.kt

@Serializable
data class PortfolioResponse(
    @SerialName("total_value_gbp") val totalValueGbp: Double,
    @SerialName("daily_pnl_gbp") val dailyPnLGbp: Double? = 0.0,
    @SerialName("holdings") val positions: List<Position> = emptyList()
    // This tells the app: "Look for 'holdings' in JSON, but call it 'positions' in code"
)

@Serializable
data class Position(
    val ticker: String,
    val name: String = "",
    @SerialName("value_gbp") val marketValue: Double,
    // Maps 'value_gbp' from your curl to 'marketValue' used in PortfolioDetailScreen
    @SerialName("pnl_gbp") val pnlPercent: Double = 0.0
    // Maps 'pnl_gbp' to the second column in your UI
)

@Serializable
data class BriefResponse(
    val summary: String,
    @SerialName("last_updated") val lastUpdated: String
)

interface VitaClawApiService {
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @POST("ask")
    suspend fun askQuestion(@Body request: AskRequest): Response<AskResponse>

    @GET("portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponse>

    @GET("brief")
    suspend fun getBrief(): Response<BriefResponse>
}