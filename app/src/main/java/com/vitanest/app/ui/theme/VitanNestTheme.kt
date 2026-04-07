package com.vitanest.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// © 2026 Sumeet Garg — VitaNest
// E-ink monochrome design tokens — locked 2026-04-06 ☘️
// Single source of truth. Never hardcode colours in screens.

object VitaNestTheme {

    // ── Colours ──────────────────────────────────────────────
    val Paper       = Color(0xFFF2EFE8)   // background — paper cream
    val Ink         = Color(0xFF111111)   // primary text — near black
    val Muted       = Color(0xFF888888)   // secondary text — mid grey
    val Rule        = Color(0xFFC8C4BB)   // dividers — light rule
    val InkInverted = Color(0xFFFFFFFF)   // text on inverted stamp

    // One colour exception — recovery ring only
    val RecoveryGreen = Color(0xFF4CAF50)
    val RecoveryAmber = Color(0xFFEF9F27)
    val RecoveryRed   = Color(0xFFE24B4A)

    // Greyscale ink ramp — donut chart segments
    val InkRamp = listOf(
        Color(0xFF111111),
        Color(0xFF2A2A2A),
        Color(0xFF444444),
        Color(0xFF5E5E5E),
        Color(0xFF777777),
        Color(0xFF888888),
        Color(0xFF999999),
        Color(0xFFAAAAAA),
        Color(0xFFBBBBBB),
        Color(0xFFC8C4BB),
        Color(0xFFD4D0C8),
        Color(0xFFDDDAD3),
        Color(0xFFE5E2DB),
    )

    // ── Typography ───────────────────────────────────────────
    val Serif = FontFamily.Serif   // Noto Serif on Android — Georgia equivalent

    // Hero numbers — 40sp Georgia 700
    val heroNumber = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        color = Ink
    )

    // Section headings — 8sp sans 500 uppercase tracked
    val sectionHead = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        letterSpacing = 1.5.sp,
        color = Muted
    )

    // Body values — 13sp sans 600
    val bodyValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Ink
    )

    // Metadata — 9-10sp sans
    val meta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = Muted
    )

    // Inverted stamp label
    val stampLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = InkInverted
    )

    // ── Spacing ──────────────────────────────────────────────
    val screenPadding: Dp  = 20.dp
    val sectionGap: Dp     = 24.dp
    val rowHeight: Dp      = 44.dp
    val ruleThickness: Dp  = 1.dp
    val heavyRule: Dp      = 2.dp
    val leftBarWidth: Dp   = 3.dp

    // ── Helpers ──────────────────────────────────────────────
    fun recoveryColor(pct: Float): Color = when {
        pct >= 67f -> RecoveryGreen
        pct >= 34f -> RecoveryAmber
        else       -> RecoveryRed
    }

    fun inkRampColor(index: Int): Color =
        InkRamp.getOrElse(index) { Muted }
}