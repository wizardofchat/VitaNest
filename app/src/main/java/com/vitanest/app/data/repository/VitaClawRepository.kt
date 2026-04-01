package com.vitanest.app.data.repository

import com.vitanest.app.data.remote.AskRequest
import com.vitanest.app.data.remote.AskResponse
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.HealthResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.RetrofitClient

open class VitaClawRepository {

    protected val apiService = RetrofitClient.apiService

    open suspend fun getHealth(): Result<HealthResponse> {
        return try {
            val response = apiService.getHealth()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response body from /health"))
                }
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
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response body from /ask"))
                }
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
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response body from /portfolio"))
                }
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
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response body from /brief"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}