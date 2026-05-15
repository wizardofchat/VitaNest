package com.vitanest.app.data.cache

// © 2026 Sumeet Garg — VitaNest
// VitaClawCache — DataStore-backed cache, one key per endpoint.
// Reads are instant (disk, no network). Writes happen after every
// successful API call. Clear is explicit only (long press refresh).
// TTL thresholds drive the freshness pill colour on HomeScreen. ☘️

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── DataStore singleton ───────────────────────────────────────
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vitaclaw_cache"
)

// ── JSON at file level — required for inline reified access ──
val cacheJson = Json { ignoreUnknownKeys = true; isLenient = true }

// ── Freshness thresholds ──────────────────────────────────────
object CacheTtl {
    const val GREEN_MS = 60L * 60 * 1000        // < 1h   → green
    const val AMBER_MS = 24L * 60 * 60 * 1000   // 1–24h  → amber
    // > AMBER_MS → red
}

enum class CacheFreshness { GREEN, AMBER, RED, NONE }

// ── Cache keys ────────────────────────────────────────────────
object CacheKey {
    const val BRIEF        = "brief"
    const val PORTFOLIO    = "portfolio"
    const val PIES         = "pies"
    const val WHOOP        = "whoop"
    const val ENERGY       = "energy"
    const val GOALS        = "goals"
    const val INTENTS      = "intents"
    const val HEALTH       = "health"
    const val OBSERVATIONS = "observations"

    fun tsKey(key: String) = "ts_$key"
}

// ── Cache class ───────────────────────────────────────────────

class VitaClawCache(private val context: Context) {

    // ── Write ─────────────────────────────────────────────────

    suspend fun write(key: String, value: String) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)]               = value
            prefs[longPreferencesKey(CacheKey.tsKey(key))] = System.currentTimeMillis()
        }
    }

    suspend inline fun <reified T> writeObject(key: String, value: T) {
        write(key, cacheJson.encodeToString(value))
    }

    // ── Read ──────────────────────────────────────────────────

    suspend fun read(key: String): String? =
        context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)]
        }.first()

    suspend inline fun <reified T> readObject(key: String): T? {
        val raw = read(key) ?: return null
        return try { cacheJson.decodeFromString<T>(raw) } catch (e: Exception) { null }
    }

    // ── Timestamp ─────────────────────────────────────────────

    suspend fun timestamp(key: String): Long? =
        context.dataStore.data.map { prefs ->
            prefs[longPreferencesKey(CacheKey.tsKey(key))]
        }.first()

    suspend fun ageMs(key: String): Long? {
        val ts = timestamp(key) ?: return null
        return System.currentTimeMillis() - ts
    }

    // ── Freshness ─────────────────────────────────────────────

    suspend fun freshness(key: String): CacheFreshness {
        val age = ageMs(key) ?: return CacheFreshness.NONE
        return when {
            age < CacheTtl.GREEN_MS -> CacheFreshness.GREEN
            age < CacheTtl.AMBER_MS -> CacheFreshness.AMBER
            else                    -> CacheFreshness.RED
        }
    }

    suspend fun overallFreshness(): CacheFreshness {
        val keys   = listOf(CacheKey.BRIEF, CacheKey.PORTFOLIO, CacheKey.WHOOP, CacheKey.ENERGY)
        val states = keys.map { freshness(it) }
        return when {
            states.any { it == CacheFreshness.NONE }  -> CacheFreshness.NONE
            states.any { it == CacheFreshness.RED }   -> CacheFreshness.RED
            states.any { it == CacheFreshness.AMBER } -> CacheFreshness.AMBER
            else                                       -> CacheFreshness.GREEN
        }
    }

    suspend fun newestAgeLabel(): String {
        val keys   = listOf(CacheKey.BRIEF, CacheKey.PORTFOLIO, CacheKey.WHOOP, CacheKey.ENERGY)
        val ages   = keys.mapNotNull { ageMs(it) }
        val newest = ages.minOrNull() ?: return ""
        val mins   = newest / 60_000
        val hrs    = newest / 3_600_000
        return when {
            mins < 1  -> "just now"
            mins < 60 -> "$mins mins ago"
            else      -> "${hrs}h ago"
        }
    }

    suspend fun ageLabel(key: String): String {
        val ms   = ageMs(key) ?: return ""
        val mins = ms / 60_000
        val hrs  = ms / 3_600_000
        return when {
            mins < 1  -> "just now"
            mins < 60 -> "$mins mins ago"
            else      -> "${hrs}h ago"
        }
    }

    // ── Clear — explicit only, never on refresh ───────────────

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clear(key: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(key))
            prefs.remove(longPreferencesKey(CacheKey.tsKey(key)))
        }
    }
}