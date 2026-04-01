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
                agenticScore = 48
            )
        )
    }

    override suspend fun askQuestion(query: String, context: String?): Result<AskResponse> {
        return Result.success(
            AskResponse(
                answer = "This is a mock response from VitaClaw.\n\nYou asked: \"$query\"\n\nIn a real setup, I would analyze market data, your portfolio, and give intelligent insights.",
                sources = listOf("Mock Data", "Alpha Vantage"),
                sentiment = "positive"
            )
        )
    }

    override suspend fun getPortfolio(): Result<PortfolioResponse> {
        return Result.success(
            PortfolioResponse(
                total_value_gbp = 12485.75,
                holdings = listOf(
                    Holding("AAPL", 12.5, 2450.0),
                    Holding("NVDA", 8.0, 3120.0),
                    Holding("MSFT", 15.0, 4215.75)
                )
            )
        )
    }

    override suspend fun getBrief(): Result<BriefResponse> {
        return Result.success(
            BriefResponse(
                summary = "Market is slightly bullish today. Tech sector performing well. Your portfolio is up 0.8% since yesterday.",
                last_updated = "Just now"
            )
        )
    }
}