package com.servicehub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.servicehub.data.CatalogRepository
import com.servicehub.data.CategoryDto
import com.servicehub.data.ProfileDto
import com.servicehub.data.ProviderProfileDto
import com.servicehub.data.ProviderRepository
import com.servicehub.data.ProviderServiceDto
import com.servicehub.model.Money
import com.servicehub.model.friendlyBookingError
import com.servicehub.model.parseMoneyToMinor
import com.servicehub.ui.components.AppCard
import com.servicehub.ui.components.ErrorText
import com.servicehub.ui.components.NoticeText
import com.servicehub.ui.components.Pill
import com.servicehub.ui.components.PillKind
import com.servicehub.ui.components.SectionTitle
import kotlinx.coroutines.launch

/** Provider profile: personal details + ONE trade (category) with the provider's own hourly/unit rate. */
@Composable
fun ProviderScreen(profile: ProfileDto) {
    val repo = remember { ProviderRepository() }
    val catalog = remember { CatalogRepository() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var providerProfile by remember { mutableStateOf<ProviderProfileDto?>(null) }
    var existing by remember { mutableStateOf<ProviderServiceDto?>(null) }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var fullName by remember { mutableStateOf(profile.fullName) }
    var phone by remember { mutableStateOf(profile.phone ?: "") }
    var city by remember { mutableStateOf(profile.city ?: "") }
    var experience by remember { mutableStateOf("0") }
    var bio by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var rateText by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        catalog.categories().onSuccess { list -> categories = list.filter { it.enabled } }
        repo.myProviderProfile().onSuccess { pp ->
            providerProfile = pp
            if (pp != null) {
                experience = pp.experienceYears.toString()
                bio = pp.bio ?: ""
            }
        }
        repo.myService().onSuccess { s ->
            existing = s
            if (s != null) {
                categoryId = s.categoryId
                rateText = Money(s.rateMinor).format()
                active = s.active
            }
        }
        loading = false
    }

    val save: () -> Unit = save@{
        message = null
        val exp = experience.trim().toIntOrNull()
        val rate = parseMoneyToMinor(rateText)
        if (fullName.trim().length < 2) { message = "Please enter your full name"; messageIsError = true; return@save }
        if (city.isBlank()) { message = "Please enter your city"; messageIsError = true; return@save }
        if (exp == null || exp !in 0..60) { message = "Experience must be 0 to 60 years"; messageIsError = true; return@save }
        val cat = categoryId
        if (cat == null) { message = "Choose your service first"; messageIsError = true; return@save }
        if (rate == null || rate <= 0) { message = "Enter a valid rate"; messageIsError = true; return@save }

        scope.launch {
            busy = true
            var failure: String? = null
            repo.saveProfile(fullName.trim(), phone.trim(), city.trim(), exp, bio.trim())
                .onFailure { failure = friendlyBookingError(it.message) }
            if (failure == null) {
                repo.setMyService(cat, rate, active)
                    .onFailure { failure = friendlyBookingError(it.message) }
            }
            busy = false
            messageIsError = failure != null
            message = failure ?: "Saved"
            if (failure == null) reloadKey++
        }
    }

    if (loading) { Text("Loading..."); return }

    val chosen = categories.firstOrNull { it.id == categoryId }
    val changingTrade = existing != null && categoryId != null && existing?.categoryId != categoryId

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusBanner(providerProfile?.verification)

        SectionTitle("Your details")
        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone, { phone = it }, label = { Text("Phone (+974 ...)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(city, { city = it }, label = { Text("City / area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            experience, { experience = it }, label = { Text("Years of experience") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(bio, { bio = it }, label = { Text("About you (short)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        SectionTitle("Your trade")
        Text(
            "You work in ONE trade. You will only receive requests for it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (categories.isEmpty()) Text("No services are open yet.")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c ->
                FilterChip(selected = c.id == categoryId, onClick = { categoryId = c.id }, label = { Text(c.name) })
            }
        }
        if (changingTrade) {
            AppCard {
                Text("Changing your trade", style = MaterialTheme.typography.titleSmall)
                Text(
                    "This is only possible when you have no open requests or jobs. Your profile will go back to \"waiting for approval\" until the admin approves it again.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (chosen != null) {
            AppCard {
                val unit = if (chosen.pricingType == "per_unit") "per unit" else "per hour"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${chosen.name}: accept requests", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                OutlinedTextField(
                    rateText, { rateText = it },
                    label = { Text("Your rate ${AppConfig.CURRENCY} $unit") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                val rate = parseMoneyToMinor(rateText)
                if (rate != null && rate > 0) {
                    val customer = rate + (rate * chosen.commissionBps + 5000) / 10000
                    Text(
                        "Customers will see ${Money(customer).format()} ${AppConfig.CURRENCY} $unit (your rate + ${chosen.commissionBps / 100}% platform commission). You receive ${Money(rate).format()} ${AppConfig.CURRENCY}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        ErrorText(if (messageIsError) message else null)
        NoticeText(if (!messageIsError) message else null)
        Button(onClick = save, enabled = !busy, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving..." else "Save")
        }
        Text(
            "ID and selfie upload comes in a later step. For now the admin verifies you directly.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusBanner(verification: String?) {
    val (title, body, kind) = when (verification) {
        "approved" -> Triple("You are live", "Approved providers appear to customers.", PillKind.Good)
        "rejected" -> Triple("Not approved", "The admin did not approve your profile. Contact support.", PillKind.Bad)
        "suspended" -> Triple("Suspended", "Your profile is hidden. Contact support.", PillKind.Bad)
        else -> Triple("Waiting for approval", "Fill in your details and rate. The admin will review your profile.", PillKind.Warn)
    }
    AppCard(highlight = verification == "approved") {
        Pill(title, kind)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}
