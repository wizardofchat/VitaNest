package com.vitanest.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Data Models
@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: String,
    @Json(name = "agentic_score") val agenticScore: Int
)

@JsonClass(generateAdapter = true)
data class AskRequest(
    val query: String,
    val context: String? = null
)

@JsonClass(generateAdapter = true)
data class AskResponse(
    val answer: String,
    val sources: List<String>? = null,
    val sentiment: String? = null
)

@JsonClass(generateAdapter = true)
data class PortfolioResponse(
    val total_value_gbp: Double,
    val holdings: List<Holding>
)

@JsonClass(generateAdapter = true)
data class Holding(
    val ticker: String,
    val quantity: Double,
    val value_gbp: Double
)

@JsonClass(generateAdapter = true)
data class BriefResponse(
    val summary: String,
    val last_updated: String
)

// Main API Service Interface
interface VitaClawApiService {

    @GET("/health")
    suspend fun getHealth(): Response<HealthResponse>

    @POST("/ask")
    suspend fun askQuestion(@Body request: AskRequest): Response<AskResponse>

    @GET("/portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponse>

    @GET("/brief")
    suspend fun getBrief(): Response<BriefResponse>

    @GET("/dividends")
    suspend fun getDividends(@Query("ticker") ticker: String? = null): Response<List<String>>
}