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
    val holdings: List<Holding>
)

@Serializable
data class Holding(
    val ticker: String,
    val quantity: Double,
    @SerialName("value_gbp") val valueGbp: Double
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