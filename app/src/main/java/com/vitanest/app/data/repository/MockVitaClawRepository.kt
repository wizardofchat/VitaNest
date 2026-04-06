package com.vitanest.app.data.repository

import com.vitanest.app.data.remote.AskResponse
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.PieItem
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.Position

class MockVitaClawRepository : VitaClawRepository() {

    override suspend fun getHealth(): Result<HealthResponse> {
        return Result.success(
            HealthResponse(
                status = "healthy",
                version = "0.1.0-mock",
                uptime = "2 days, 14 hours",
                agenticScore = 60
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
                totalValueGbp = 12368.47,
                dailyPnLGbp = 509.27,
                positions = listOf(
                    Position("SGLN", "Gold ETC", 1040.88, 120.83),
                    Position("VHYL", "Vanguard All-World High Yield", 793.20, 74.60),
                    Position("VUKE", "Vanguard FTSE 100", 792.48, 74.48),
                    Position("JEPQ", "JPMorgan Nasdaq Equity Premium", 708.58, 6.40)
                )
            )
        )
    }

    override suspend fun getPortfolioPies(): Result<PiesResponse> {
        return Result.success(
            PiesResponse(
                totalValueGbp = 12368.47,
                totalPnlGbp = 509.27,
                totalCashGbp = 104.54,
                fetchedAt = "2026-04-06 19:08 GMT",
                pies = listOf(
                    PieItem(4177292, "ETF", 3859.08, 277.54, 7.75, 40.03, 31.20, 5, "ON_TRACK", 46.57, listOf("IUKD","VHYL","VUKE","VUSA","VWRL")),
                    PieItem(4177296, "Whole", 3161.16, 56.37, 1.82, 32.03, 25.56, 0, "AHEAD", 106.39, emptyList()),
                    PieItem(4177298, "Inv Trust", 1050.94, 17.19, 1.66, 0.01, 8.50, 0, null, 13.32, emptyList()),
                    PieItem(4597193, "SiddhiPie", 942.91, 84.61, 9.86, 8.01, 7.62, 0, "AHEAD", 0.0, emptyList()),
                    PieItem(4267711, "Dhanteras", 802.87, 80.80, 11.19, 20.0, 6.49, 0, "AHEAD", 0.0, emptyList()),
                    PieItem(4597306, "InnovETF", 620.0, 30.0, 5.10, 1.0, 5.01, 0, "ON_TRACK", 0.0, emptyList()),
                    PieItem(4614727, "Monthly", 580.0, 22.0, 3.94, 1.5, 4.69, 0, "ON_TRACK", 0.0, emptyList()),
                    PieItem(6709356, "REIT", 420.0, 15.0, 3.70, 1.0, 3.40, 0, null, 0.0, emptyList()),
                    PieItem(6604753, "SectorETF", 380.0, 10.0, 2.70, 0.5, 3.07, 0, null, 0.0, emptyList()),
                    PieItem(7040008, "Monv1", 320.0, 8.0, 2.56, 0.5, 2.59, 0, null, 0.0, emptyList()),
                    PieItem(6996349, "MonthlyAgg", 280.0, 5.0, 1.82, 0.5, 2.26, 0, null, 0.0, emptyList()),
                    PieItem(7450608, "Renewables", 250.0, 3.0, 1.21, 0.5, 2.02, 0, null, 0.0, emptyList()),
                    PieItem(null, "VitaWatch", 180.0, 2.0, 1.12, 0.5, 1.46, 0, null, 0.0, emptyList())
                )
            )
        )
    }

    override suspend fun getBrief(): Result<BriefResponse> {
        return Result.success(
            BriefResponse(
                summary = "Good morning Sumeet. Recovery 82% green. Portfolio £12,368.",
                lastUpdated = "Just now"
            )
        )
    }
}