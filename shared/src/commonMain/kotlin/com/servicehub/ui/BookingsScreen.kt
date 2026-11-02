package com.servicehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.servicehub.booking.Actor
import com.servicehub.booking.BookingStateMachine
import com.servicehub.booking.BookingStatus
import com.servicehub.data.AppConfig
import com.servicehub.data.BookingDto
import com.servicehub.data.BookingRepository
import com.servicehub.data.CatalogRepository
import com.servicehub.data.ComplaintRepository
import com.servicehub.data.ProfileDto
import com.servicehub.model.Money
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import kotlinx.coroutines.launch

/** "My bookings" for a consumer, "Requests" for a provider. Same screen, different buttons. */
@Composable
fun BookingsScreen(profile: ProfileDto) {
    val isProvider = profile.role == "provider"
    val actor = if (isProvider) Actor.PROVIDER else Actor.CONSUMER
    val repo = remember { BookingRepository() }
    val catalog = remember { CatalogRepository() }
    val complaintRepo = remember { ComplaintRepository() }
    val scope = rememberCoroutineScope()

    var bookings by remember { mutableStateOf<List<BookingDto>?>(null) }
    var categoryNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var people by remember { mutableStateOf<Map<String, ProfileDto>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        error = null
        catalog.categories().onSuccess { list -> categoryNames = list.associate { it.id to it.name } }
        repo.myBookings()
            .onSuccess { list ->
                bookings = list
                val otherIds = list.map { if (isProvider) it.consumerId else it.providerId }.distinct()
                if (otherIds.isNotEmpty()) {
                    repo.profilesByIds(otherIds).onSuccess { ps -> people = ps.associateBy { it.id } }
                }
            }
            .onFailure { error = friendlyBookingError(it.message); bookings = emptyList() }
    }

    fun changeStatus(b: BookingDto, next: BookingStatus) {
        scope.launch {
            busyId = b.id
            repo.changeStatus(b.id, next.name.lowercase()).onFailure { error = friendlyBookingError(it.message) }
            busyId = null
            reloadKey++
        }
    }

    fun review(b: BookingDto, rating: Int, comment: String) {
        scope.launch {
            busyId = b.id
            repo.submitReview(b.id, rating, comment).onFailure { error = friendlyBookingError(it.message) }
            busyId = null
            reloadKey++
        }
    }

    fun report(b: BookingDto, reason: String, details: String) {
        scope.launch {
            busyId = b.id
            complaintRepo.raise(b.id, reason, details)
                .onSuccess { notice = "Your complaint was sent. Our team will contact you." }
                .onFailure { error = friendlyBookingError(it.message) }
            busyId = null
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isProvider) "Booking requests" else "My bookings", style = MaterialTheme.typography.titleMedium)
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
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    val list = bookings
    if (list == null) Text("Loading...")
    if (list != null && list.isEmpty() && error == null) {
        Text(if (isProvider) "No requests yet. Make sure you are approved and your rates are saved." else "You have no bookings yet.")
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list ?: emptyList()) { b ->
            BookingCard(
                b = b,
                isProvider = isProvider,
                actor = actor,
                categoryName = categoryNames[b.categoryId] ?: b.categoryId,
                other = people[if (isProvider) b.consumerId else b.providerId],
                busy = busyId == b.id,
                onAction = { next -> changeStatus(b, next) },
                onReview = { rating, comment -> review(b, rating, comment) },
                onReport = { reason, details -> report(b, reason, details) }
            )
        }
    }
}

private fun actionLabel(s: BookingStatus): String = when (s) {
    BookingStatus.ACCEPTED -> "Accept"
    BookingStatus.DECLINED -> "Decline"
    BookingStatus.ON_THE_WAY -> "I am on the way"
    BookingStatus.IN_PROGRESS -> "Start job"
    BookingStatus.COMPLETED -> "Mark completed"
    BookingStatus.PAID -> "Cash received"
    BookingStatus.CANCELLED -> "Cancel"
    else -> s.name.lowercase().replace('_', ' ')
}

private fun statusText(status: String): String = status.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun BookingCard(
    b: BookingDto,
    isProvider: Boolean,
    actor: Actor,
    categoryName: String,
    other: ProfileDto?,
    busy: Boolean,
    onAction: (BookingStatus) -> Unit,
    onReview: (Int, String) -> Unit,
    onReport: (String, String) -> Unit
) {
    val status = runCatching { BookingStatus.valueOf(b.status.uppercase()) }.getOrNull()
    // Buttons come from the SAME rules as the server (BookingStateMachine). Some steps have their own screens.
    val actions = if (status == null) emptyList() else
        BookingStateMachine.allowedNext(status, actor).filter {
            it != BookingStatus.REVIEWED && it != BookingStatus.DISPUTED && it != BookingStatus.EXPIRED &&
                    !(it == BookingStatus.PAID && !isProvider)   // only the provider confirms the cash
        }
    val contactVisible = status != null && status != BookingStatus.REQUESTED &&
            status != BookingStatus.DECLINED && status != BookingStatus.EXPIRED && status != BookingStatus.CANCELLED

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryName, style = MaterialTheme.typography.titleMedium)
                Text(statusText(b.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(formatLocalDateTime(b.scheduledAt) + " (Qatar time)", style = MaterialTheme.typography.bodyMedium)
            Text(b.addressText, style = MaterialTheme.typography.bodySmall)
            if (!b.notes.isNullOrBlank()) Text("Notes: ${b.notes}", style = MaterialTheme.typography.bodySmall)

            if (isProvider) {
                Text("You earn ${Money(b.providerEarningsMinor).format()} ${AppConfig.CURRENCY}", style = MaterialTheme.typography.titleSmall)
            } else {
                Text("Total ${Money(b.totalMinor).format()} ${AppConfig.CURRENCY}", style = MaterialTheme.typography.titleSmall)
            }

            if (contactVisible && other != null) {
                Text(
                    (if (isProvider) "Customer: " else "Provider: ") + other.fullName +
                            (other.phone?.takeIf { it.isNotBlank() }?.let { "  |  $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!contactVisible && status == BookingStatus.REQUESTED) {
                Text(
                    if (isProvider) "Accept to see the customer's phone number." else "Waiting for the provider to accept.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (status == BookingStatus.COMPLETED && !isProvider) {
                Text("Pay the provider in cash. They will confirm payment.", style = MaterialTheme.typography.bodySmall)
            }

            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEachIndexed { index, next ->
                        val isCancel = next == BookingStatus.CANCELLED || next == BookingStatus.DECLINED
                        if (index == 0 && !isCancel) {
                            Button(onClick = { onAction(next) }, enabled = !busy) { Text(actionLabel(next)) }
                        } else {
                            OutlinedButton(onClick = { onAction(next) }, enabled = !busy) { Text(actionLabel(next)) }
                        }
                    }
                }
            }

            if (status == BookingStatus.PAID && !isProvider) ReviewBox(busy = busy, onSubmit = onReview)
            val canReport = status != null && status != BookingStatus.REQUESTED && status != BookingStatus.DECLINED &&
                    status != BookingStatus.EXPIRED && status != BookingStatus.CANCELLED
            if (canReport) ReportBox(isProvider = isProvider, busy = busy, onSubmit = onReport)
            if (status == BookingStatus.REVIEWED) Text("Thank you for your review.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReviewBox(busy: Boolean, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("How was the service?", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 1..5) FilterChip(selected = rating == r, onClick = { rating = r }, label = { Text("$r") })
        }
        OutlinedTextField(comment, { comment = it }, label = { Text("Comment (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSubmit(rating, comment.trim()) }, enabled = !busy) { Text("Submit review") }
    }
}

@Composable
private fun ReportBox(isProvider: Boolean, busy: Boolean, onSubmit: (String, String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val reasons = if (isProvider)
        listOf("Customer not available", "Customer did not pay", "Wrong address", "Rude behaviour", "Other")
    else
        listOf("Provider did not come", "Poor quality of work", "Price disagreement", "Rude behaviour", "Other")

    if (!open) {
        TextButton(onClick = { open = true }) { Text("Report a problem") }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What went wrong?", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            reasons.forEach { r ->
                FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) })
            }
        }
        OutlinedTextField(details, { details = it }, label = { Text("Details (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSubmit(reason, details.trim()); open = false; reason = ""; details = "" },
                enabled = !busy && reason.isNotBlank()
            ) { Text("Send complaint") }
            OutlinedButton(onClick = { open = false }) { Text("Cancel") }
        }
    }
}
