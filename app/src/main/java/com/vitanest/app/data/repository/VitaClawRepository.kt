package com.vitanest.app.data.repository

import com.vitanest.app.data.remote.AskRequest
import com.vitanest.app.data.remote.AskResponse
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.PiesResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.WhoopResponse
import com.vitanest.app.data.remote.RetrofitClient
import com.vitanest.app.data.remote.DividendDataResponse
import com.vitanest.app.data.remote.GoalsResponse
import com.vitanest.app.data.remote.EnergyResponse
import com.vitanest.app.data.remote.ChatRequest
import com.vitanest.app.data.remote.ChatResponse

open class VitaClawRepository {

    protected val apiService = RetrofitClient.apiService

    open suspend fun getHealth(): Result<HealthResponse> {
        return try {
            val response = apiService.getHealth()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /health"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun askQuestion(query: String, context: String? = null): Result<AskResponse> {
        return try {
            val request = AskRequest(query = query, context = context)
            val response = apiService.askQuestion(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /ask"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun sendChat(message: String): Result<ChatResponse> {
        return try {
            val request = ChatRequest(message = message)
            val response = apiService.sendChat(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /chat"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getPortfolio(): Result<PortfolioResponse> {
        return try {
            val response = apiService.getPortfolio()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /portfolio"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getPortfolioPies(): Result<PiesResponse> {
        return try {
            val response = apiService.getPortfolioPies()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /portfolio/pies"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getBrief(): Result<BriefResponse> {
        return try {
            val response = apiService.getBrief()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /brief"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getWhoop(): Result<WhoopResponse> {
        return try {
            val response = apiService.getWhoop()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /whoop"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getDividendData(): Result<DividendDataResponse> {
        return try {
            val response = apiService.getDividendData()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /portfolio/dividend-data"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getGoals(): Result<GoalsResponse> {
        return try {
            val response = apiService.getGoals()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /goals"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getEnergy(): Result<EnergyResponse> {
        return try {
            val response = apiService.getEnergy()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Empty response body from /energy"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}