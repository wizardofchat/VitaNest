package com.vitanest.app.data.repository

import com.vitanest.app.data.remote.AskResponse
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.Holding

class MockVitaClawRepository : VitaClawRepository() {

    override suspend fun getHealth(): Result<HealthResponse> {
        return Result.success(
            HealthResponse(
                status = "healthy",
                version = "0.1.0-mock",
                uptime = "2 days, 14 hours",
                agenticScore = 48 // Matches updated HealthResponse
            )
        )
    }

    override suspend fun askQuestion(query: String, context: String?): Result<AskResponse> {
        return Result.success(
            AskResponse(
                answer = "This is a mock response from VitaClaw.\n\nYou asked: \"$query\"",
                sources = listOf("Mock Data"),
                sentiment = "positive"
            )
        )
    }

    override suspend fun getPortfolio(): Result<PortfolioResponse> {
        return Result.success(
            PortfolioResponse(
                totalValueGbp = 12485.75, // Updated parameter name
                holdings = listOf(
                    Holding("AAPL", 12.5, 2450.0),
                    Holding("NVDA", 8.0, 3120.0)
                )
            )
        )
    }

    override suspend fun getBrief(): Result<BriefResponse> {
        return Result.success(
            BriefResponse(
                summary = "Market is slightly bullish today.",
                lastUpdated = "Just now" // Updated parameter name
            )
        )
    }
}