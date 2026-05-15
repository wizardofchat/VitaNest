package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// HomeViewModel — scoped to MainActivity, survives tab switches.
// Data loads once on app open. Manual refresh via icon only.
// Cache read → background live fetch → silent UI update. ☘️

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitanest.app.data.cache.CacheFreshness
import com.vitanest.app.data.cache.CacheKey
import com.vitanest.app.data.cache.VitaClawCache
import com.vitanest.app.data.remote.BriefResponse
import com.vitanest.app.data.remote.EnergyResponse
import com.vitanest.app.data.remote.PortfolioResponse
import com.vitanest.app.data.remote.WhoopResponse
import com.vitanest.app.data.repository.VitaClawRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val repository: VitaClawRepository
) : AndroidViewModel(application) {

    private val cache = VitaClawCache(application)

    // ── Data state ────────────────────────────────────────────
    private val _briefData     = MutableStateFlow<BriefResponse?>(null)
    private val _portfolioData = MutableStateFlow<PortfolioResponse?>(null)
    private val _energyData    = MutableStateFlow<EnergyResponse?>(null)
    private val _whoopData     = MutableStateFlow<WhoopResponse?>(null)
    private val _agenticScore  = MutableStateFlow(0)

    val briefData:     StateFlow<BriefResponse?>     = _briefData
    val portfolioData: StateFlow<PortfolioResponse?> = _portfolioData
    val energyData:    StateFlow<EnergyResponse?>    = _energyData
    val whoopData:     StateFlow<WhoopResponse?>     = _whoopData
    val agenticScore:  StateFlow<Int>                = _agenticScore

    // ── Cache / connection state ──────────────────────────────
    private val _freshness    = MutableStateFlow(CacheFreshness.NONE)
    private val _ageLabel     = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _isOffline    = MutableStateFlow(false)

    val freshness:    StateFlow<CacheFreshness> = _freshness
    val ageLabel:     StateFlow<String>         = _ageLabel
    val isRefreshing: StateFlow<Boolean>        = _isRefreshing
    val isOffline:    StateFlow<Boolean>        = _isOffline

    // ── Init: load cache then fetch live — runs once ──────────
    init {
        viewModelScope.launch {
            // Step 1: show cached data instantly
            _briefData.value     = cache.readObject<BriefResponse>(CacheKey.BRIEF)
            _portfolioData.value = cache.readObject<PortfolioResponse>(CacheKey.PORTFOLIO)
            _energyData.value    = cache.readObject<EnergyResponse>(CacheKey.ENERGY)
            _whoopData.value     = cache.readObject<WhoopResponse>(CacheKey.WHOOP)
            _freshness.value     = cache.overallFreshness()
            _ageLabel.value      = cache.newestAgeLabel()

            // Step 2: background live fetch
            fetchLive()
        }
    }

    // ── Manual refresh — called from refresh icon tap ─────────
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch { fetchLive() }
    }

    // ── Clear cache — called from long press confirm ──────────
    fun clearCache() {
        viewModelScope.launch {
            cache.clearAll()
            _briefData.value     = null
            _portfolioData.value = null
            _energyData.value    = null
            _whoopData.value     = null
            _freshness.value     = CacheFreshness.NONE
            _ageLabel.value      = ""
            fetchLive()
        }
    }

    // ── Core fetch logic ──────────────────────────────────────
    private suspend fun fetchLive() {
        _isRefreshing.value = true
        _isOffline.value    = false
        coroutineScope {
            val healthDeferred    = async { repository.getHealth() }
            val briefDeferred     = async { repository.getBrief() }
            val portfolioDeferred = async { repository.getPortfolio() }
            val energyDeferred    = async { repository.getEnergy() }
            val whoopDeferred     = async { repository.getWhoop() }

            healthDeferred.await().let { r ->
                if (r.isSuccess) {
                    _agenticScore.value = r.getOrNull()?.agenticScore ?: _agenticScore.value
                } else {
                    _isOffline.value = true
                }
            }
            briefDeferred.await().getOrNull()?.let {
                _briefData.value = it
                cache.writeObject(CacheKey.BRIEF, it)
            }
            portfolioDeferred.await().getOrNull()?.let {
                _portfolioData.value = it
                cache.writeObject(CacheKey.PORTFOLIO, it)
            }
            energyDeferred.await().getOrNull()?.let {
                _energyData.value = it
                cache.writeObject(CacheKey.ENERGY, it)
            }
            whoopDeferred.await().getOrNull()?.let {
                _whoopData.value = it
                cache.writeObject(CacheKey.WHOOP, it)
            }
        }
        _freshness.value    = cache.overallFreshness()
        _ageLabel.value     = cache.newestAgeLabel()
        _isRefreshing.value = false
    }
}