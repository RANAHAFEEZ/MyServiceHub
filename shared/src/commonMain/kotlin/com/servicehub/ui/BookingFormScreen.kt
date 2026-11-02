package com.servicehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.servicehub.data.BookingRepository
import com.servicehub.data.CategoryDto
import com.servicehub.data.ProviderDirectoryDto
import com.servicehub.data.ProviderOfferDto
import com.servicehub.data.QuoteDto
import com.servicehub.model.Money
import com.servicehub.model.buildQatarTimestamp
import com.servicehub.model.friendlyBookingError
import com.servicehub.model.parseMoneyToMinor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BookingTarget(
    val offer: ProviderOfferDto,
    val provider: ProviderDirectoryDto?,
    val category: CategoryDto
)

@Composable
fun BookingFormScreen(target: BookingTarget, onBack: () -> Unit, onBooked: () -> Unit) {
    val repo = remember { BookingRepository() }
    val scope = rememberCoroutineScope()
    val offer = target.offer
    val hourly = offer.pricingType == "hourly"
    val perUnit = offer.pricingType == "per_unit"

    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("2") }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    var quote by remember { mutableStateOf<QuoteDto?>(null) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // hours typed as "2" or "2.5"  ->  minutes (150)
    val minutes: Int = if (hourly) {
        val h = parseMoneyToMinor(hoursText)   // hundredths of an hour
        if (h == null || h <= 0) 0 else (h * 60 / 100).toInt()
    } else 0
    val quantity: Int = if (perUnit) (qtyText.trim().toIntOrNull() ?: 0) else 1

    // Ask the server for the price whenever the amount changes (waits a moment while typing)
    LaunchedEffect(minutes, quantity) {
        quote = null
        quoteError = null
        val ready = (hourly && minutes > 0) || (perUnit && quantity > 0)
        if (ready) {
            delay(400)
            repo.quote(offer.providerId, offer.categoryId, minutes, quantity)
                .onSuccess { quote = it }
                .onFailure { quoteError = friendlyBookingError(it.message) }
        }
    }

    val confirm: () -> Unit = confirm@{
        error = null
        val ts = buildQatarTimestamp(date, time)
        if (ts == null) { error = "Enter a valid date (YYYY-MM-DD) and time (HH:MM, 24-hour)."; return@confirm }
        if (zone.isBlank() || street.isBlank() || building.isBlank()) {
            error = "Please enter your Zone, Street and Building number."; return@confirm
        }
        if (hourly && minutes <= 0) { error = "Enter how many hours you need."; return@confirm }
        if (perUnit && quantity <= 0) { error = "Enter a valid quantity."; return@confirm }

        val address = buildString {
            append("Zone ${zone.trim()}, Street ${street.trim()}, Building ${building.trim()}")
            if (landmark.isNotBlank()) append(" - ${landmark.trim()}")
        }
        scope.launch {
            busy = true
            repo.createBooking(offer.providerId, offer.categoryId, ts, address, notes.trim(), minutes, quantity)
                .onSuccess { onBooked() }
                .onFailure { error = friendlyBookingError(it.message) }
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text("Book ${target.category.name}", style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(target.provider?.fullName ?: "Provider", style = MaterialTheme.typography.titleSmall)
                val unit = if (perUnit) "per unit" else "per hour"
                Text("${Money(offer.customerRateMinor).format()} ${AppConfig.CURRENCY} $unit", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text("When", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(time, { time = it }, label = { Text("Time HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Text("Time is Qatar time (24-hour, for example 18:30).", style = MaterialTheme.typography.bodySmall)

        if (hourly) {
            OutlinedTextField(
                hoursText, { hoursText = it }, label = { Text("How many hours? (minimum ${offer.minMinutes} min)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
        }
        if (perUnit) {
            OutlinedTextField(
                qtyText, { qtyText = it }, label = { Text("Quantity (for example number of cars)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Where", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(zone, { zone = it }, label = { Text("Zone") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(street, { street = it }, label = { Text("Street") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(building, { building = it }, label = { Text("Building") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(landmark, { landmark = it }, label = { Text("Area / landmark (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes for the provider (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Price", style = MaterialTheme.typography.titleSmall)
                val q = quote
                when {
                    q != null -> {
                        Text("Total ${Money(q.totalMinor).format()} ${AppConfig.CURRENCY}", style = MaterialTheme.typography.titleMedium)
                        q.billableMinutes?.let { Text("Billed for $it minutes", style = MaterialTheme.typography.bodySmall) }
                        if (q.visitFeeMinor > 0) Text("Includes visit fee ${Money(q.visitFeeMinor).format()} ${AppConfig.CURRENCY}", style = MaterialTheme.typography.bodySmall)
                        Text("Final price is fixed when you send the request.", style = MaterialTheme.typography.bodySmall)
                    }
                    quoteError != null -> Text(quoteError!!, color = MaterialTheme.colorScheme.error)
                    else -> Text("Enter the details to see the price.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = confirm, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Sending..." else "Send booking request")
        }
        Text("You pay the provider directly (cash) after the job is done.", style = MaterialTheme.typography.bodySmall)
    }
}
