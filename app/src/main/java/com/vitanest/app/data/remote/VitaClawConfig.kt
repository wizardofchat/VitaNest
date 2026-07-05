package com.vitanest.app.data.remote

// © 2026 Sumeet Garg — VitaNest
// Single source of truth for backend base URL. Tailscale-only per
// session decision (2026-07-05) — manual JSON export is the fallback
// if Tailscale is unreachable during Norway, not a second base URL.
// Compiled constant, not user-editable — no settings screen needed.
object VitaClawConfig {
    const val BASE_URL = "http://100.69.38.81:8000/"
}