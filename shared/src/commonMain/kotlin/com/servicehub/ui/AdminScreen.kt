package com.servicehub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.servicehub.booking.Actor
import com.servicehub.booking.BookingStateMachine
import com.servicehub.booking.BookingStatus
import com.servicehub.data.AdminOpsRepository
import com.servicehub.data.AdminProviderRow
import com.servicehub.data.AdminRepository
import com.servicehub.data.AdminStatsDto
import com.servicehub.data.AppConfig
import com.servicehub.data.BookingDto
import com.servicehub.data.BookingRepository
import com.servicehub.data.CatalogRepository
import com.servicehub.data.ComplaintDto
import com.servicehub.data.ComplaintRepository
import com.servicehub.data.ProfileDto
import com.servicehub.model.Money
import com.servicehub.ui.components.AppTabs
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import kotlinx.coroutines.launch

/** The admin area. */
@Composable
fun AdminScreen() {
    var tab by remember { mutableStateOf(0) }
    AppTabs(
        names = listOf("Dashboard", "Providers", "Ledger", "Bookings", "Complaints", "Support", "Users", "Settings"),
        selected = tab,
        onSelect = { tab = it }
    )
    when (tab) {
        0 -> AdminDashboardTab()
        1 -> AdminProvidersTab()
        2 -> AdminLedgerTab()
        3 -> AdminBookingsTab()
        4 -> AdminComplaintsTab()
        5 -> AdminSupportTab()
        6 -> AdminUsersTab()
        else -> AdminSettingsTab()
    }
}

private fun money(minor: Long) = "${Money(minor).format()} ${AppConfig.CURRENCY}"

// ------------------------------------------------------------------ dashboard

@Composable
private fun AdminDashboardTab() {
    val repo = remember { AdminOpsRepository() }
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<AdminStatsDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        error = null
        repo.stats().onSuccess { stats = it }.onFailure { error = it.message }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Dashboard", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    val s = stats
    if (s == null && error == null) Text("Loading...")
    if (s != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Providers waiting for approval", s.pendingProviders.toString())
            StatCard("Open complaints", s.openComplaints.toString())
            StatCard("Active bookings", s.bookingsActive.toString())
            StatCard("All bookings", s.bookingsTotal.toString())
            StatCard("Paid bookings", s.bookingsPaid.toString())
            StatCard("Paid by customers (total)", money(s.paidTotal))
            StatCard("Platform commission on paid bookings", money(s.paidCommission))
            Text(
                "Commission is counted when the booking is paid. Providers collect cash from customers and pay the commission to the platform; track it in the Ledger tab.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = {
                scope.launch {
                    repo.expireStaleRequests()
                        .onSuccess { notice = "$it old request(s) expired."; reloadKey++ }
                        .onFailure { error = it.message }
                }
            }) { Text("Expire old unanswered requests") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ------------------------------------------------------------------ providers (approval)

@Composable
private fun AdminProvidersTab() {
    val repo = remember { AdminRepository() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("pending") }
    var reloadKey by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<AdminProviderRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status, reloadKey) {
        rows = null
        error = null
        repo.providersByStatus(status)
            .onSuccess { rows = it }
            .onFailure { error = it.message; rows = emptyList() }
    }

    fun act(providerId: String, newStatus: String) {
        scope.launch {
            repo.setVerification(providerId, newStatus).onFailure { error = it.message }
            reloadKey++
        }
    }

    Text("Provider approvals", style = MaterialTheme.typography.titleMedium)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in listOf("pending", "approved", "rejected", "suspended")) {
            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) })
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val list = rows
    if (list == null) Text("Loading...")
    if (list != null && list.isEmpty() && error == null) Text("No $status providers.")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list ?: emptyList()) { row ->
            val p = row.profile
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p?.fullName?.ifBlank { "(no name yet)" } ?: "(no profile)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(p?.phone?.takeIf { it.isNotBlank() }, p?.city?.takeIf { it.isNotBlank() },
                            "${row.provider.experienceYears} yrs").joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    row.provider.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (status) {
                            "pending" -> {
                                Button(onClick = { act(row.provider.userId, "approved") }) { Text("Approve") }
                                OutlinedButton(onClick = { act(row.provider.userId, "rejected") }) { Text("Reject") }
                            }
                            "approved" -> OutlinedButton(onClick = { act(row.provider.userId, "suspended") }) { Text("Suspend") }
                            else -> Button(onClick = { act(row.provider.userId, "approved") }) { Text("Approve") }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ all bookings

private fun adminLabel(s: BookingStatus): String = when (s) {
    BookingStatus.CANCELLED -> "Cancel booking"
    BookingStatus.DISPUTED -> "Mark disputed"
    BookingStatus.COMPLETED -> "Set completed"
    BookingStatus.PAID -> "Set paid"
    else -> s.name.lowercase().replace('_', ' ')
}

@Composable
private fun AdminBookingsTab() {
    val ops = remember { AdminOpsRepository() }
    val bookingsRepo = remember { BookingRepository() }
    val catalog = remember { CatalogRepository() }
    val scope = rememberCoroutineScope()

    var filter by remember { mutableStateOf("active") }
    var all by remember { mutableStateOf<List<BookingDto>?>(null) }
    var names by remember { mutableStateOf<Map<String, ProfileDto>>(emptyMap()) }
    var categories by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        error = null
        catalog.categories().onSuccess { list -> categories = list.associate { it.id to it.name } }
        ops.allBookings().onSuccess { list ->
            all = list
            val ids = list.flatMap { listOf(it.consumerId, it.providerId) }.distinct()
            ops.profilesByIds(ids).onSuccess { ps -> names = ps.associateBy { it.id } }
        }.onFailure { error = it.message; all = emptyList() }
    }

    val active = setOf("requested", "accepted", "on_the_way", "in_progress")
    val shown = (all ?: emptyList()).filter {
        when (filter) {
            "active" -> it.status in active
            "completed" -> it.status == "completed"
            "paid" -> it.status == "paid" || it.status == "reviewed"
            "disputed" -> it.status == "disputed"
            "closed" -> it.status in setOf("cancelled", "declined", "expired")
            else -> true
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("All bookings", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
    }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (f in listOf("active", "completed", "paid", "disputed", "closed", "all")) {
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (all == null) Text("Loading...")
    if (all != null && shown.isEmpty() && error == null) Text("Nothing here.")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown) { b ->
            val st = runCatching { BookingStatus.valueOf(b.status.uppercase()) }.getOrNull()
            val actions = if (st == null) emptyList() else
                BookingStateMachine.allowedNext(st, Actor.ADMIN).filter { it != BookingStatus.REVIEWED && it != BookingStatus.EXPIRED }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(categories[b.categoryId] ?: b.categoryId, style = MaterialTheme.typography.titleMedium)
                        Text(b.status.replace('_', ' '), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(formatLocalDateTime(b.scheduledAt) + " (Qatar time)", style = MaterialTheme.typography.bodySmall)
                    val c = names[b.consumerId]
                    val p = names[b.providerId]
                    Text("Customer: ${c?.fullName ?: "?"} ${c?.phone ?: ""}", style = MaterialTheme.typography.bodySmall)
                    Text("Provider: ${p?.fullName ?: "?"} ${p?.phone ?: ""}", style = MaterialTheme.typography.bodySmall)
                    Text(b.addressText, style = MaterialTheme.typography.bodySmall)
                    Text("Total ${money(b.totalMinor)}  |  provider ${money(b.providerEarningsMinor)}  |  commission ${money(b.commissionMinor)}", style = MaterialTheme.typography.bodySmall)
                    if (actions.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.forEach { next ->
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        bookingsRepo.changeStatus(b.id, next.name.lowercase())
                                            .onFailure { error = friendlyBookingError(it.message) }
                                        reloadKey++
                                    }
                                }) { Text(adminLabel(next)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ complaints

@Composable
private fun AdminComplaintsTab() {
    val complaintsRepo = remember { ComplaintRepository() }
    val ops = remember { AdminOpsRepository() }
    val catalog = remember { CatalogRepository() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("open") }
    var list by remember { mutableStateOf<List<ComplaintDto>?>(null) }
    var bookings by remember { mutableStateOf<Map<String, BookingDto>>(emptyMap()) }
    var people by remember { mutableStateOf<Map<String, ProfileDto>>(emptyMap()) }
    var categories by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(status, reloadKey) {
        error = null
        list = null
        catalog.categories().onSuccess { cs -> categories = cs.associate { it.id to it.name } }
        complaintsRepo.byStatus(status).onSuccess { cs ->
            list = cs
            ops.bookingsByIds(cs.map { it.bookingId }.distinct()).onSuccess { bs ->
                bookings = bs.associateBy { it.id }
                val ids = (cs.map { it.raisedBy } + bs.flatMap { listOf(it.consumerId, it.providerId) }).distinct()
                ops.profilesByIds(ids).onSuccess { ps -> people = ps.associateBy { it.id } }
            }
        }.onFailure { error = it.message; list = emptyList() }
    }

    fun update(c: ComplaintDto, newStatus: String, notes: String) {
        scope.launch {
            complaintsRepo.resolve(c.id, newStatus, notes).onFailure { error = it.message }
            reloadKey++
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Complaints", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
    }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in listOf("open", "in_review", "resolved", "rejected")) {
            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.replace('_', ' ')) })
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val cs = list
    if (cs == null) Text("Loading...")
    if (cs != null && cs.isEmpty() && error == null) Text("No ${status.replace('_', ' ')} complaints.")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cs ?: emptyList()) { c ->
            ComplaintCard(
                c = c,
                booking = bookings[c.bookingId],
                raisedBy = people[c.raisedBy],
                customer = bookings[c.bookingId]?.let { people[it.consumerId] },
                provider = bookings[c.bookingId]?.let { people[it.providerId] },
                categoryName = bookings[c.bookingId]?.let { categories[it.categoryId] ?: it.categoryId },
                onUpdate = { newStatus, notes -> update(c, newStatus, notes) }
            )
        }
    }
}

@Composable
private fun ComplaintCard(
    c: ComplaintDto,
    booking: BookingDto?,
    raisedBy: ProfileDto?,
    customer: ProfileDto?,
    provider: ProfileDto?,
    categoryName: String?,
    onUpdate: (String, String) -> Unit
) {
    var notes by remember(c.id) { mutableStateOf(c.adminNotes ?: "") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.reason, style = MaterialTheme.typography.titleMedium)
            c.details?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Reported by ${raisedBy?.fullName ?: "?"} (${raisedBy?.role ?: "?"}) ${raisedBy?.phone ?: ""} on ${formatLocalDateTime(c.createdAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (booking != null) {
                Text(
                    "Booking: ${categoryName ?: booking.categoryId} | ${booking.status.replace('_', ' ')} | ${money(booking.totalMinor)} | ${formatLocalDateTime(booking.scheduledAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Customer: ${customer?.fullName ?: "?"} ${customer?.phone ?: ""}", style = MaterialTheme.typography.bodySmall)
                Text("Provider: ${provider?.fullName ?: "?"} ${provider?.phone ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Admin notes") },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (c.status) {
                    "open" -> {
                        OutlinedButton(onClick = { onUpdate("in_review", notes) }) { Text("Start review") }
                        Button(onClick = { onUpdate("resolved", notes) }) { Text("Resolve") }
                        OutlinedButton(onClick = { onUpdate("rejected", notes) }) { Text("Reject") }
                    }
                    "in_review" -> {
                        Button(onClick = { onUpdate("resolved", notes) }) { Text("Resolve") }
                        OutlinedButton(onClick = { onUpdate("rejected", notes) }) { Text("Reject") }
                    }
                    else -> OutlinedButton(onClick = { onUpdate("open", notes) }) { Text("Re-open") }
                }
            }
        }
    }
}
