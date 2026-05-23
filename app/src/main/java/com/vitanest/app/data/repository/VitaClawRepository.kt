package com.vitanest.app.data.repository

// © 2026 Sumeet Garg — VitaNest
// VitaClawRepository — all API calls, single source of truth
// Updated: getDcaDetail + getPortfolioOrders added;
//          getTodayObservations + postObservationFeedback added;
//          getGrowth added for /growth endpoint;
//          getWhoopAnalytics + getWhoopSynthesis added for health analytics;
//          getWhoopAnalytics now passes patterns=true via Retrofit default ☘️

import com.vitanest.app.data.remote.AskRequest
import com.vitanest.app.data.remote.AskResponse
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.ChatHistoryResponse
import com.vitanest.app.data.remote.ChatOpeningResponse
import com.vitanest.app.data.remote.ChatRequest
import com.vitanest.app.data.remote.ChatResponse
import com.vitanest.app.data.remote.DcaDetailResponse
import com.vitanest.app.data.remote.DividendDataResponse
import com.vitanest.app.data.remote.EnergyResponse
import com.vitanest.app.data.remote.FeedbackRequest
import com.vitanest.app.data.remote.GoalsResponse
import com.vitanest.app.data.remote.GrowthResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.IncomeStressRequest
import com.vitanest.app.data.remote.IncomeStressResponse
import com.vitanest.app.data.remote.IntentsResponse
import com.vitanest.app.data.remote.ObservationsResponse
import com.vitanest.app.data.remote.OrdersSummaryResponse
import com.vitanest.app.data.remote.PendingOfflineResponse
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.QuotaResponse
import com.vitanest.app.data.remote.RetrofitClient
import com.vitanest.app.data.remote.WhoopResponse
import com.vitanest.app.data.remote.FinanceAnalyticsResponse
import com.vitanest.app.data.remote.FinanceSynthesisResponse
import com.vitanest.app.data.remote.PaperTradeResponse
import com.vitanest.app.data.remote.PaperTradeRunResponse
import com.vitanest.app.data.remote.WhoopSynthesisResponse

open class VitaClawRepository {

    protected val apiService = RetrofitClient.apiService

    // ── System ────────────────────────────────────────────────

    open suspend fun getHealth(): Result<HealthResponse> {
        return try {
            val r = apiService.getHealth()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /health"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getQuota(): Result<QuotaResponse> {
        return try {
            val r = apiService.getQuota()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /quota"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Ask (legacy) ──────────────────────────────────────────

    open suspend fun askQuestion(query: String, context: String? = null): Result<AskResponse> {
        return try {
            val r = apiService.askQuestion(AskRequest(query = query, context = context))
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /ask"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Chat ──────────────────────────────────────────────────

    open suspend fun sendChat(
        message: String,
        offline: Boolean = false
    ): Result<ChatResponse> {
        return try {
            val r = apiService.sendChat(ChatRequest(message = message, offline = offline))
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /chat"))
            } else if (r.code() == 429) {
                Result.failure(Exception("Daily chat limit reached (50/day). Resets at midnight."))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getChatOpening(): Result<ChatOpeningResponse> {
        return try {
            val r = apiService.getChatOpening()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /chat/opening"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getChatHistory(): Result<ChatHistoryResponse> {
        return try {
            val r = apiService.getChatHistory()
            if (r.isSuccessful) {
                val list = r.body() ?: emptyList()
                Result.success(ChatHistoryResponse(exchanges = list))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getChatOfflinePending(): Result<PendingOfflineResponse> {
        return try {
            val r = apiService.getChatOfflinePending()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /chat/offline/pending"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun ackOfflineMessage(jobId: String): Result<Unit> {
        return try {
            val r = apiService.ackOfflineMessage(jobId)
            if (r.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Intents ───────────────────────────────────────────────

    open suspend fun getIntents(): Result<IntentsResponse> {
        return try {
            val r = apiService.getIntents()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /intents"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Portfolio ─────────────────────────────────────────────

    open suspend fun getPortfolio(): Result<PortfolioResponse> {
        return try {
            val r = apiService.getPortfolio()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getPortfolioPies(): Result<PiesResponse> {
        return try {
            val r = apiService.getPortfolioPies()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /portfolio/pies"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getDividendData(): Result<DividendDataResponse> {
        return try {
            val r = apiService.getDividendData()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /portfolio/dividend-data"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getDcaDetail(ticker: String): Result<DcaDetailResponse> {
        return try {
            val r = apiService.getDcaDetail(ticker)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /portfolio/dca/$ticker"))
            } else if (r.code() == 422) {
                Result.failure(Exception("'$ticker' not found"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getPortfolioOrders(
        tickers: String = "ALL",
        days: Int = 90,
        limit: Int = 50
    ): Result<OrdersSummaryResponse> {
        return try {
            val r = apiService.getPortfolioOrders(tickers, days, limit)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /portfolio/orders"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun runIncomeStress(
        request: IncomeStressRequest
    ): Result<IncomeStressResponse> {
        return try {
            val r = apiService.runIncomeStress(request)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /portfolio/income-stress"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Brief ─────────────────────────────────────────────────

    open suspend fun getBrief(): Result<BriefResponse> {
        return try {
            val r = apiService.getBrief()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /brief"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Whoop ─────────────────────────────────────────────────

    open suspend fun getWhoop(date: String? = null): Result<WhoopResponse> {
        return try {
            val r = apiService.getWhoop(date)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /whoop"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // patterns=true is the Retrofit default — no extra param needed here
    open suspend fun getWhoopAnalytics(range: String): Result<WhoopResponse> {
        return try {
            val r = apiService.getWhoopAnalytics(range)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /whoop?range=$range&patterns=true"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getWhoopSynthesis(range: String): Result<WhoopSynthesisResponse> {
        return try {
            val r = apiService.getWhoopSynthesis(range)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /whoop/synthesis?range=$range"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Goals ─────────────────────────────────────────────────

    open suspend fun getGoals(): Result<GoalsResponse> {
        return try {
            val r = apiService.getGoals()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /goals"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Energy ────────────────────────────────────────────────

    open suspend fun getEnergy(): Result<EnergyResponse> {
        return try {
            val r = apiService.getEnergy()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /energy"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Buddie Observations ───────────────────────────────────

    open suspend fun getTodayObservations(): Result<ObservationsResponse> {
        return try {
            val r = apiService.getTodayObservations()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /buddie/observations/today"))
            } else if (r.code() == 404) {
                Result.success(ObservationsResponse(date = "", count = 0, observations = emptyList()))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun postObservationFeedback(id: Int, rating: String): Result<Unit> {
        return try {
            val r = apiService.postObservationFeedback(id, FeedbackRequest(rating = rating))
            if (r.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Growth ───────────────────────────────────────────────

    open suspend fun getGrowth(
        days: Int? = 30,
        fromDate: String? = null,
        toDate: String? = null
    ): Result<GrowthResponse> {
        return try {
            val effectiveDays = if (fromDate != null) null else days
            val r = apiService.getGrowth(
                days     = effectiveDays,
                fromDate = fromDate,
                toDate   = toDate
            )
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /growth"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }
    // ── Finance Analytics ─────────────────────────────────────

    open suspend fun getFinanceAnalytics(range: String): Result<FinanceAnalyticsResponse> {
        return try {
            val r = apiService.getFinanceAnalytics(range)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /finance/analytics?range=$range"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun getFinanceSynthesis(range: String): Result<FinanceSynthesisResponse> {
        return try {
            val r = apiService.getFinanceSynthesis(range)
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /finance/synthesis?range=$range"))
            } else Result.failure(Exception("HTTP ${r.code()}: ${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Paper Trade ──────────────────────────────────────────

    open suspend fun getPaperTrade(): Result<PaperTradeResponse> {
        return try {
            val r = apiService.getPaperTrade()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /buddie/paper-trade/latest"))
            } else Result.failure(Exception("HTTP \${r.code()}: \${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    open suspend fun runPaperTrade(): Result<PaperTradeRunResponse> {
        return try {
            val r = apiService.runPaperTrade()
            if (r.isSuccessful) {
                val body = r.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response from /buddie/paper-trade/run"))
            } else Result.failure(Exception("HTTP \${r.code()}: \${r.message()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

}