package com.servicehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.servicehub.data.AdminConfigRepository
import com.servicehub.data.AdminOpsRepository
import com.servicehub.data.AppConfig
import com.servicehub.data.AppSettingsDto
import com.servicehub.data.AuditDto
import com.servicehub.data.CategoryDto
import com.servicehub.data.ProfileDto
import com.servicehub.data.TicketDto
import com.servicehub.model.Money
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import com.servicehub.model.parseMoneyToMinor
import kotlinx.coroutines.launch

@Preview
@Composable
fun AdminSupportTab() {
    val repo = remember { AdminConfigRepository() }
    val ops = remember { AdminOpsRepository() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("open") }
    var tickets by remember { mutableStateOf<List<TicketDto>?>(null) }
    var people by remember { mutableStateOf<Map<String, ProfileDto>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(status, reloadKey) {
        error = null
        tickets = null
        repo.tickets(status).onSuccess { list ->
            tickets = list
            ops.profilesByIds(list.map { it.userId }.distinct()).onSuccess { ps -> people = ps.associateBy { it.id } }
        }.onFailure { error = friendlyBookingError(it.message); tickets = emptyList() }
    }

    fun sendReply(t: TicketDto, text: String, newStatus: String) {
        scope.launch {
            repo.replyTicket(t.id, text, newStatus).onFailure { error = friendlyBookingError(it.message) }
            reloadKey++
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Support queries", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { reloadKey++ },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, LuxuryGold),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LuxuryGold,
                containerColor = LuxurySurface // #1E1E1E
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Refresh",
                color = LuxuryGold,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in listOf("open", "pending", "closed")) {
            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) })
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val list = tickets
    if (list == null) Text("Loading...")
    if (list != null && list.isEmpty() && error == null) Text("No $status queries.")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list ?: emptyList()) { t ->
            var reply by remember(t.id) { mutableStateOf(t.adminReply ?: "") }
            val u = people[t.userId]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.subject, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${u?.fullName ?: "?"} (${u?.role ?: "?"}) ${u?.phone ?: ""}  |  ${formatLocalDateTime(t.createdAt)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(t.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(reply, { reply = it }, label = { Text("Your reply") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (t.status != "closed") {
                            Button(enabled = reply.isNotBlank(), onClick = { sendReply(t, reply.trim(), "closed") }) { Text("Reply and close") }
                            OutlinedButton(enabled = reply.isNotBlank(), onClick = { sendReply(t, reply.trim(), "pending") }) { Text("Reply, keep open") }
                        } else {
                            OutlinedButton(onClick = { sendReply(t, reply.trim(), "open") }) { Text("Re-open") }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ users (block / unblock)

@Composable
fun AdminUsersTab() {
    val repo = remember { AdminConfigRepository() }
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("provider") }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<ProfileDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(role, reloadKey) {
        error = null
        users = null
        repo.users(role).onSuccess { users = it }.onFailure { error = friendlyBookingError(it.message); users = emptyList() }
    }

    fun setBlocked(u: ProfileDto, blocked: Boolean) {
        scope.launch {
            repo.setBlocked(u.id, blocked).onFailure { error = friendlyBookingError(it.message) }
            reloadKey++
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Users", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { reloadKey++ },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, LuxuryGold),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LuxuryGold,
                containerColor = LuxurySurface // #1E1E1E
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Refresh",
                color = LuxuryGold,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in listOf("provider", "consumer", "admin")) {
            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
        }
    }
    OutlinedTextField(query, { query = it }, label = { Text("Search name, phone or city") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val shown = (users ?: emptyList()).filter {
        query.isBlank() ||
            it.fullName.contains(query, ignoreCase = true) ||
            (it.phone ?: "").contains(query, ignoreCase = true) ||
            (it.city ?: "").contains(query, ignoreCase = true)
    }
    if (users == null) Text("Loading...")
    if (users != null && shown.isEmpty() && error == null) Text("No users found.")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown) { u ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.fullName.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(u.phone?.takeIf { it.isNotBlank() }, u.city?.takeIf { it.isNotBlank() }, "id ${u.id.take(8)}").joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (u.isBlocked) Text("BLOCKED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    if (u.role != "admin") {
                        if (u.isBlocked) Button(onClick = { setBlocked(u, false) }) { Text("Unblock") }
                        else OutlinedButton(onClick = { setBlocked(u, true) }) { Text("Block") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings: commission %, categories, rules, audit

@Composable
fun AdminSettingsTab() {
    val repo = remember { AdminConfigRepository() }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CategoryDto>?>(null) }
    var settings by remember { mutableStateOf<AppSettingsDto?>(null) }
    var audit by remember { mutableStateOf<List<AuditDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        error = null
        repo.categories().onSuccess { categories = it }.onFailure { error = friendlyBookingError(it.message); categories = emptyList() }
        repo.settings().onSuccess { settings = it }.onFailure { error = friendlyBookingError(it.message) }
        repo.audit().onSuccess { audit = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Categories and commission", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { reloadKey++ },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, LuxuryGold),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LuxuryGold,
                    containerColor = LuxurySurface // #1E1E1E
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Refresh",
                    color = LuxuryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text("Changes apply to NEW bookings only. Existing bookings keep the price they were created with.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (categories == null) Text("Loading...")
        categories?.forEach { c ->
            CategoryEditor(c) { enabled, type, bps, minMin, fee ->
                scope.launch {
                    repo.updateCategory(c.id, enabled, type, bps, minMin, fee)
                        .onSuccess { notice = "Saved ${c.name}."; error = null; reloadKey++ }
                        .onFailure { error = friendlyBookingError(it.message); notice = null }
                }
            }
        }

        Text("Booking rules", style = MaterialTheme.typography.titleMedium)
        settings?.let { s ->
            RulesEditor(s) { cur, expiry, maxOpen, notice2, days, maxMin, maxQty ->
                scope.launch {
                    repo.updateSettings(cur, expiry, maxOpen, notice2, days, maxMin, maxQty)
                        .onSuccess { notice = "Rules saved."; error = null; reloadKey++ }
                        .onFailure { error = friendlyBookingError(it.message); notice = null }
                }
            }
        }

        Text("Recent admin actions", style = MaterialTheme.typography.titleMedium)
        if (audit.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
        audit.take(15).forEach { a ->
            Text(
                "${formatLocalDateTime(a.createdAt)}  ${a.action}  ${a.target ?: ""}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CategoryEditor(c: CategoryDto, onSave: (Boolean, String, Int, Int, Long) -> Unit) {
    var enabled by remember(c) { mutableStateOf(c.enabled) }
    var type by remember(c) { mutableStateOf(c.pricingType) }
    var pct by remember(c) { mutableStateOf(Money(c.commissionBps.toLong()).format()) }
    var minText by remember(c) { mutableStateOf(c.minMinutes.toString()) }
    var fee by remember(c) { mutableStateOf(Money(c.visitFeeMinor).format()) }
    var localError by remember(c) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "Open" else "Closed", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in listOf("hourly", "per_unit", "quote")) {
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.replace('_', ' ')) })
                }
            }
            OutlinedTextField(
                pct, { pct = it }, label = { Text("Commission % (for example 15 or 12.5)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    minText, { minText = it }, label = { Text("Minimum minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    fee, { fee = it }, label = { Text("Visit fee ${AppConfig.CURRENCY}") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                )
            }
            val bps = parseMoneyToMinor(pct)?.toInt()
            if (bps != null) Text("Customers pay the provider's rate plus ${Money(bps.toLong()).format()}%.", style = MaterialTheme.typography.bodySmall)
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val b = parseMoneyToMinor(pct)?.toInt()
                val m = minText.trim().toIntOrNull()
                val f = parseMoneyToMinor(fee)
                when {
                    b == null || b !in 0..5000 -> localError = "Commission must be between 0 and 50 percent."
                    m == null || m !in 0..480 -> localError = "Minimum minutes must be between 0 and 480."
                    f == null -> localError = "Enter a valid visit fee (0 for none)."
                    else -> { localError = null; onSave(enabled, type, b, m, f) }
                }
            }) { Text("Save ${c.name}") }
        }
    }
}

@Composable
private fun RulesEditor(
    s: AppSettingsDto,
    onSave: (String, Int, Int, Int, Int, Int, Int) -> Unit
) {
    var currency by remember(s) { mutableStateOf(s.currencyCode) }
    var expiry by remember(s) { mutableStateOf(s.requestExpiryMinutes.toString()) }
    var maxOpen by remember(s) { mutableStateOf(s.maxOpenRequests.toString()) }
    var minNotice by remember(s) { mutableStateOf(s.minNoticeMinutes.toString()) }
    var maxDays by remember(s) { mutableStateOf(s.maxDaysAhead.toString()) }
    var maxMinutes by remember(s) { mutableStateOf(s.maxMinutesPerBooking.toString()) }
    var maxQty by remember(s) { mutableStateOf(s.maxQuantity.toString()) }
    var localError by remember(s) { mutableStateOf<String?>(null) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(currency, { currency = it }, label = { Text("Currency code (for example QAR)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(expiry, { expiry = it }, label = { Text("Unanswered request expires after (minutes)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxOpen, { maxOpen = it }, label = { Text("Max open bookings per customer") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minNotice, { minNotice = it }, label = { Text("Minimum notice before a job (minutes)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxDays, { maxDays = it }, label = { Text("Book at most this many days ahead") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxMinutes, { maxMinutes = it }, label = { Text("Max minutes per booking (720 = 12 hours)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxQty, { maxQty = it }, label = { Text("Max quantity per booking") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val e = expiry.trim().toIntOrNull()
                val o = maxOpen.trim().toIntOrNull()
                val n = minNotice.trim().toIntOrNull()
                val d = maxDays.trim().toIntOrNull()
                val m = maxMinutes.trim().toIntOrNull()
                val q = maxQty.trim().toIntOrNull()
                when {
                    currency.isBlank() -> localError = "Enter a currency code."
                    e == null || e < 1 -> localError = "Expiry must be at least 1 minute."
                    o == null || o !in 1..50 -> localError = "Max open bookings must be 1 to 50."
                    n == null || n < 0 -> localError = "Minimum notice cannot be negative."
                    d == null || d !in 1..365 -> localError = "Days ahead must be 1 to 365."
                    m == null || m !in 15..1440 -> localError = "Max minutes must be 15 to 1440."
                    q == null || q !in 1..1000 -> localError = "Max quantity must be 1 to 1000."
                    else -> { localError = null; onSave(currency.trim(), e, o, n, d, m, q) }
                }
            }) { Text("Save rules") }
        }
    }
}
