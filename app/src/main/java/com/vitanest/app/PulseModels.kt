package com.vitanest.app

import android.util.Log

data class PulseMetrics(
    var recovery: Float = 0f,
    var hrv: Float = 0f,
    var rhr: Float = 0f,
    var spo2: Float = 0f,
    var skinTemp: Float = 34.3f,
    var strain: Float = 0f,
    var sleepPerformance: Float = 0f,
    var sleepEfficiency: Float = 0f,
    var remMin: Float = 0f,
    var deepMin: Float = 0f,
    var disturbances: Int = 0,
    var lastWorkout: String = "No recent workout"
)

data class PulseResponse(
    val status: String = "",
    val metrics: PulseMetrics = PulseMetrics()
)

// Helper function to extract data from the Agent's text
// Replace the function in PulseModels.kt
fun parsePulseResponse(response: String): PulseMetrics {
    val metrics = PulseMetrics()
    // Clean up markdown and emojis to simplify matching
    val clean = response.replace("*", "")
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")

    try {
        // This regex handles "Label", followed by optional spaces, an optional colon,
        // more spaces, and finally the number.
        metrics.recovery = Regex("""Recovery\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.hrv = Regex("""HRV\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.rhr = Regex("""RHR\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.spo2 = Regex("""SpO2\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.strain = Regex("""Strain\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        //metrics.sleepPerformance = Regex("""Sleep\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        // Sleep performance — match "Sleep : 75.0% performance"
        metrics.sleepPerformance = Regex("""Sleep\s*:\s*([\d.]+)%\s*performance""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        // Sleep efficiency — match "Sleep eff  : 91.8%"
        metrics.sleepEfficiency = Regex("""Sleep\s*eff\s*:\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        // Skin temp — match "Skin temp  : 34.3°C" (missing from current regex)
        metrics.skinTemp = Regex("""Skin\s*temp\s*:\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f

        // Deep and REM metrics
        metrics.remMin = Regex("""REM\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.deepMin = Regex("""Deep\s*:?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toFloat() ?: 0f
        metrics.disturbances = Regex("""Disturb\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toInt() ?: 0

        // Grab the workout string
        metrics.lastWorkout = Regex("""Workout\s*:?\s*(.*)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.trim() ?: "No recent workout"

    } catch (e: Exception) {
        android.util.Log.e("PulseParser", "Failed to parse: ${e.message}")
    }
    return metrics
}