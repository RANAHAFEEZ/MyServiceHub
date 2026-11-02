package com.servicehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.servicehub.data.LedgerEntryDto
import com.servicehub.data.LedgerRepository
import com.servicehub.data.LedgerSummaryDto
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import com.servicehub.ui.components.AppCard
import com.servicehub.ui.components.ErrorText
import com.servicehub.ui.components.MetricTile
import com.servicehub.ui.components.Pill
import com.servicehub.ui.components.PillKind
import com.servicehub.ui.components.SectionTitle
import com.servicehub.ui.components.qar
import com.servicehub.ui.theme.Brand

/** Provider "Earnings" tab: what I earned, what I owe the platform, and how close I am to the limit. */
@Composable
fun EarningsScreen() {
    val repo = remember { LedgerRepository() }
    var summary by remember { mutableStateOf<LedgerSummaryDto?>(null) }
    var entries by remember { mutableStateOf<List<LedgerEntryDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        error = null
        repo.mySummary().onSuccess { summary = it }.onFailure { error = friendlyBookingError(it.message) }
        repo.myEntries().onSuccess { entries = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Earnings and balance")
            TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
        }
        ErrorText(error)

        val s = summary
        if (s == null && error == null) Text("Loading...")
        if (s != null) {
            val limited = s.limitMinor > 0
            val ratio = if (limited) (s.owedMinor.toFloat() / s.limitMinor.toFloat()).coerceIn(0f, 1f) else 0f
            val over = limited && s.owedMinor >= s.limitMinor
            val near = limited && !over && ratio >= 0.8f

            AppCard(highlight = over) {
                Text("You owe the platform", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(qar(s.owedMinor.coerceAtLeast(0)), style = MaterialTheme.typography.headlineMedium)
                if (limited) {
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Brand.Line)) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .clip(RoundedCornerShape(50))
                                .background(if (over) Brand.Bad else if (near) Brand.Warn else Brand.Gold)
                        )
                    }
                    Text("Limit ${qar(s.limitMinor)}", style = MaterialTheme.typography.bodySmall)
                }
                when {
                    over -> {
                        Pill("New requests paused", PillKind.Bad)
                        Text("Pay the platform to continue. After the admin records your payment you can accept requests again.", style = MaterialTheme.typography.bodySmall)
                    }
                    near -> Pill("Close to your limit", PillKind.Warn)
                    s.owedMinor < 0 -> Pill("You have a credit", PillKind.Good)
                    else -> Pill("All good", PillKind.Good)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Earned (your share)", qar(s.earnedMinor), Modifier.weight(1f), accent = true)
                MetricTile("Paid jobs", s.paidJobs.toString(), Modifier.weight(1f))
            }
            MetricTile("Platform commission on your jobs", qar(s.commissionTotalMinor))

            AppCard {
                Text("How it works", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Customers pay you directly. When a job is marked paid, the platform commission is added to your balance. " +
                        "Pay the platform weekly (cash or transfer); the admin records it here. " +
                        "If your balance reaches the limit, new requests pause until you pay.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SectionTitle("History")
        if (entries.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
        entries.take(40).forEach { e -> LedgerRow(e) }
    }
}

@Composable
fun LedgerRow(e: LedgerEntryDto) {
    val label = when (e.entryType) {
        "commission" -> "Commission"
        "reversal" -> "Commission reversed"
        "payment" -> "Payment to platform"
        else -> "Adjustment"
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(formatLocalDateTime(e.createdAt), e.note?.takeIf { it.isNotBlank() }).joinToString("  |  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val sign = if (e.amountMinor > 0) "+" else "-"
        val abs = if (e.amountMinor < 0) -e.amountMinor else e.amountMinor
        Text(
            "$sign${qar(abs)}",
            style = MaterialTheme.typography.titleSmall,
            color = if (e.amountMinor > 0) Brand.Warn else Brand.Good
        )
    }
}
