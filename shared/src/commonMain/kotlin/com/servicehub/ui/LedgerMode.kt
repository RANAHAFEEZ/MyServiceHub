package com.servicehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.servicehub.data.AppConfig
import com.servicehub.data.LedgerEntryDto
import com.servicehub.data.LedgerRepository
import com.servicehub.data.ProviderBalanceDto
import com.servicehub.model.Money
import com.servicehub.model.friendlyBookingError
import com.servicehub.model.parseMoneyToMinor
import com.servicehub.ui.components.AppCard
import com.servicehub.ui.components.ErrorText
import com.servicehub.ui.components.MetricTile
import com.servicehub.ui.components.NoticeText
import com.servicehub.ui.components.Pill
import com.servicehub.ui.components.PillKind
import com.servicehub.ui.components.SectionTitle
import com.servicehub.ui.components.qar
import kotlinx.coroutines.launch

private enum class LedgerMode { None, Payment, Adjust, History }

/** Admin "Ledger" tab: who owes what, record cash payments, adjust, and change the credit limit. */
@Composable
fun AdminLedgerTab() {
    val repo = remember { LedgerRepository() }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ProviderBalanceDto>?>(null) }
    var limit by remember { mutableStateOf<Long?>(null) }
    var limitText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    var openProvider by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(LedgerMode.None) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<LedgerEntryDto>>(emptyList()) }

    LaunchedEffect(reloadKey) {
        error = null
        repo.adminBalances().onSuccess { rows = it }.onFailure { error = friendlyBookingError(it.message); rows = emptyList() }
        repo.creditLimit().onSuccess { limit = it; limitText = Money(it).format() }
    }

    fun close() { openProvider = null; mode = LedgerMode.None; amountText = ""; noteText = "" }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Commission ledger")
            TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
        }
        ErrorText(error)
        NoticeText(notice)

        val list = rows
        if (list != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Total owed to platform", qar(list.sumOf { it.owedMinor.coerceAtLeast(0) }), Modifier.weight(1f), accent = true)
                MetricTile("Paused (over limit)", list.count { it.overLimit }.toString(), Modifier.weight(1f))
            }
        }

        AppCard {
            Text("Credit limit per provider", style = MaterialTheme.typography.titleSmall)
            Text(
                "When a provider owes this much or more, they cannot accept new requests and customers stop seeing them until you record a payment. 0 means no limit.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                limitText, { limitText = it }, label = { Text("Limit ${AppConfig.CURRENCY}") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val v = parseMoneyToMinor(limitText)
                    if (v == null || v < 0) { error = "Enter a valid limit"; notice = null; return@Button }
                    scope.launch {
                        repo.setCreditLimit(v)
                            .onSuccess { notice = "Limit saved."; error = null; reloadKey++ }
                            .onFailure { error = friendlyBookingError(it.message); notice = null }
                    }
                }
            ) { Text("Save limit") }
        }

        SectionTitle("Providers")
        if (list == null && error == null) Text("Loading...")
        if (list != null && list.isEmpty()) Text("No providers yet.", style = MaterialTheme.typography.bodySmall)
        list?.forEach { r ->
            AppCard(highlight = r.overLimit) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.fullName?.ifBlank { "Provider" } ?: "Provider", style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(r.categoryId, r.phone?.takeIf { it.isNotBlank() }, "${r.paidJobs} paid jobs").joinToString("  |  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(qar(r.owedMinor), style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.overLimit) Pill("Paused", PillKind.Bad)
                    Pill(r.verification, if (r.verification == "approved") PillKind.Good else PillKind.Warn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { openProvider = r.providerId; mode = LedgerMode.Payment; amountText = ""; noteText = "" }) { Text("Payment") }
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { openProvider = r.providerId; mode = LedgerMode.Adjust; amountText = ""; noteText = "" }) { Text("Adjust") }
                    OutlinedButton(shape = MaterialTheme.shapes.small, onClick = {
                        openProvider = r.providerId; mode = LedgerMode.History
                        scope.launch { repo.entriesFor(r.providerId).onSuccess { history = it }.onFailure { error = friendlyBookingError(it.message) } }
                    }) { Text("History") }
                }

                if (openProvider == r.providerId && mode != LedgerMode.None) {
                    when (mode) {
                        LedgerMode.History -> {
                            if (history.isEmpty()) Text("No entries yet.", style = MaterialTheme.typography.bodySmall)
                            history.take(30).forEach { LedgerRow(it) }
                            TextButton(onClick = { close() }) { Text("Close") }
                        }
                        else -> {
                            val isPayment = mode == LedgerMode.Payment
                            Text(
                                if (isPayment) "Record cash received from this provider" else "Adjustment (use a minus sign to reduce what they owe)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            OutlinedTextField(
                                amountText, { amountText = it }, label = { Text("Amount ${AppConfig.CURRENCY}") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                noteText, { noteText = it },
                                label = { Text(if (isPayment) "Note (optional, e.g. cash week 12)" else "Reason (required)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(shape = MaterialTheme.shapes.small, onClick = {
                                    val negative = amountText.trim().startsWith("-")
                                    val raw = parseMoneyToMinor(amountText.trim().removePrefix("-"))
                                    if (raw == null || raw <= 0) { error = "Enter a valid amount"; notice = null; return@Button }
                                    scope.launch {
                                        val result = if (isPayment) {
                                            repo.recordPayment(r.providerId, raw, noteText.trim())
                                        } else {
                                            repo.adjust(r.providerId, if (negative) -raw else raw, noteText.trim())
                                        }
                                        result
                                            .onSuccess { notice = if (isPayment) "Payment recorded." else "Adjustment saved."; error = null; close(); reloadKey++ }
                                            .onFailure { error = friendlyBookingError(it.message); notice = null }
                                    }
                                }) { Text("Confirm") }
                                TextButton(onClick = { close() }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }
}
