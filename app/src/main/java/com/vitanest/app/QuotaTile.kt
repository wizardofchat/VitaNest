package com.vitanest.app

// © 2026 Sumeet Garg — VitaNest
// QuotaTile — shared quota display, used by AskScreen ☘️

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitanest.app.data.remote.QuotaResponse
import com.vitanest.app.ui.theme.VitaNestTheme as T

@Composable
fun QuotaTile(
    quota: QuotaResponse?,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val isExceeded  = quota?.status == "quota_exceeded"
    val borderColor = if (isExceeded) Color(0xFFC0392B) else T.Rule

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .animateContentSize()
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(text = "QUOTA", style = T.sectionHead)
                if (isExceeded) {
                    Text(
                        text          = "EXCEEDED",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = Color(0xFFC0392B),
                        letterSpacing = 1.sp
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val geminiText = quota?.gemini?.let {
                    "${it.remaining} / ${it.limit}"
                } ?: "—"
                Text(
                    text  = "Gemini $geminiText",
                    style = T.meta,
                    color = if (isExceeded) Color(0xFFC0392B) else T.Muted
                )
                Text(
                    text  = if (isExpanded) "▲" else "▼",
                    style = T.meta
                )
            }
        }
        HorizontalDivider(thickness = T.ruleThickness, color = borderColor)

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            quota?.gemini?.let { g ->
                QuotaDetailRow(
                    label     = "Gemini",
                    used      = "${g.used} used",
                    remaining = "${g.remaining} remaining",
                    pctUsed   = g.pctUsed.toFloat(),
                    warn      = g.pctUsed >= 80.0
                )
            } ?: QuotaDetailRow(label = "Gemini", used = "—", remaining = "—", pctUsed = 0f)

            Spacer(modifier = Modifier.height(10.dp))

            quota?.claude?.let { c ->
                QuotaDetailRow(
                    label     = "Claude",
                    used      = "£${"%.2f".format(c.spentGbp)} spent",
                    remaining = "£${"%.2f".format(c.remainingGbp)} remaining",
                    pctUsed   = c.pctUsed.toFloat(),
                    warn      = c.pctUsed >= 80.0
                )
            } ?: QuotaDetailRow(label = "Claude", used = "—", remaining = "—", pctUsed = 0f)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = T.ruleThickness, color = T.Rule)
        }
    }
}

@Composable
fun QuotaDetailRow(
    label: String,
    used: String,
    remaining: String,
    pctUsed: Float,
    warn: Boolean = false
) {
    val barColor = when {
        pctUsed >= 90f -> Color(0xFFC0392B)
        pctUsed >= 70f -> Color(0xFFD4A017)
        else           -> T.Ink
    }

    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = T.meta, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = used,      style = T.meta, color = T.Muted)
                Text(text = "·",       style = T.meta, color = T.Muted)
                Text(
                    text  = remaining,
                    style = T.meta,
                    color = if (warn) Color(0xFFD4A017) else T.Muted
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(T.Rule)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((pctUsed / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
    }
}