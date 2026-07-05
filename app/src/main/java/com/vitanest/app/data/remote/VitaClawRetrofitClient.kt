package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// RetrofitClient — single OkHttp client, caching bug fixed 2026-05-11 ☘️
// Removed: ConnectionPool(0, 1, NANOSECONDS) — was forcing fresh TCP connection
// on every request, causing stale portfolio values from proxy layer.
// OkHttp defaults: 5 idle connections, 5 min keepalive — correct for VitaNest.

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = VitaClawConfig.BASE_URL

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        // encodeDefaults=true is required: without it, any field carrying
        // its Kotlin default value (source="manual", deleted=false, etc.)
        // is silently omitted from the outgoing JSON. VitaClaw's Pydantic
        // models mark several of those fields as required, causing 422s
        // that look like missing data when the data was actually just
        // never sent. Root cause of the 2026-07-05 trip/sync 422 failures.
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // ConnectionPool deliberately omitted — OkHttp defaults are correct:
        // 5 idle connections, 5 minute keepalive. DO NOT re-add the
        // ConnectionPool(0, 1, NANOSECONDS) override — it caused portfolio
        // value mismatches (£13,956 vs £14,275) by forcing stale connections.
        .build()

    val apiService: VitaClawApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VitaClawApiService::class.java)
    }
}