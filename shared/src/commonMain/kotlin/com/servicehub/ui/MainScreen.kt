package com.servicehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.servicehub.data.AppConfig
import com.servicehub.data.AuthRepository
import com.servicehub.data.CatalogRepository
import com.servicehub.data.CategoryDto
import com.servicehub.data.ProfileDto
import com.servicehub.data.ProviderDirectoryDto
import com.servicehub.data.ProviderOfferDto
import com.servicehub.model.Money
import com.servicehub.ui.components.AppCard
import com.servicehub.ui.components.AppTabs
import com.servicehub.ui.components.BrandHeader
import com.servicehub.ui.components.ErrorText
import kotlinx.coroutines.launch

@Composable
fun MainScreen(auth: AuthRepository) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<ProfileDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        auth.myProfile()
            .onSuccess { profile = it }
            .onFailure { error = it.message }
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader(
            title = "Hello, ${profile?.fullName?.ifBlank { "there" } ?: "..."}",
            subtitle = auth.currentEmail() ?: "",
            roleLabel = (profile?.role ?: "...").replaceFirstChar { it.uppercase() },
            onSignOut = { scope.launch { auth.signOut().onFailure { error = it.message } } }
        )

        ErrorText(error)

        val p = profile
        when {
            !loaded -> Text("Loading...")
            p == null -> Text("Profile not found. Check the sign-up trigger from 01_schema.sql.")
            p.role == "provider" -> ProviderHome(p)
            p.role == "admin" -> AdminScreen()
            else -> ConsumerHome(p)
        }
    }
}

// ------------------------------------------------------------------ provider: 4 tabs

@Composable
private fun ProviderHome(profile: ProfileDto) {
    var tab by remember { mutableStateOf(0) }
    AppTabs(listOf("Profile", "Requests", "Earnings", "Support"), tab) { tab = it }
    when (tab) {
        0 -> ProviderScreen(profile)
        1 -> BookingsScreen(profile)
        2 -> EarningsScreen()
        else -> SupportScreen()
    }
}

// ------------------------------------------------------------------ consumer: services -> providers -> booking form, + my bookings

@Composable
private fun ConsumerHome(profile: ProfileDto) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<CategoryDto?>(null) }
    var target by remember { mutableStateOf<BookingTarget?>(null) }

    AppTabs(listOf("Services", "My bookings", "Support"), tab) { tab = it }

    val t = target
    val c = selected
    when {
        tab == 2 -> SupportScreen()
        tab == 1 -> BookingsScreen(profile)
        t != null -> BookingFormScreen(
            target = t,
            onBack = { target = null },
            onBooked = { target = null; selected = null; tab = 1 }
        )
        c == null -> CategoriesList(onPick = { selected = it })
        else -> ProvidersList(
            category = c,
            onBack = { selected = null },
            onBook = { offer, person -> target = BookingTarget(offer, person, c) }
        )
    }
}

@Composable
private fun CategoriesList(onPick: (CategoryDto) -> Unit) {
    val repo = remember { CatalogRepository() }
    var items by remember { mutableStateOf<List<CategoryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        repo.categories().onSuccess { items = it }.onFailure { error = it.message }
    }

    Text("What do you need help with?", style = MaterialTheme.typography.titleMedium)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val list = items
    if (list == null && error == null) Text("Loading...")
    if (list != null && list.isEmpty()) Text("No categories are available yet.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list ?: emptyList()) { c ->
            Box(modifier = Modifier.clickable { onPick(c) }) { AppCard {
                Column {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (c.pricingType) {
                            "hourly" -> "Priced per hour (minimum ${c.minMinutes} min)"
                            "per_unit" -> "Priced per unit"
                            else -> "Price on quote"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } }
        }
    }
}

@Composable
private fun ProvidersList(
    category: CategoryDto,
    onBack: () -> Unit,
    onBook: (ProviderOfferDto, ProviderDirectoryDto?) -> Unit
) {
    val repo = remember { CatalogRepository() }
    var offers by remember { mutableStateOf<List<ProviderOfferDto>?>(null) }
    var people by remember { mutableStateOf<Map<String, ProviderDirectoryDto>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(category.id) {
        repo.offers(category.id).onSuccess { offers = it }.onFailure { error = it.message }
        repo.providerDirectory().onSuccess { list -> people = list.associateBy { it.providerId } }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text(category.name, style = MaterialTheme.typography.titleMedium)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val list = offers
    if (list == null && error == null) Text("Loading...")
    if (list != null && list.isEmpty()) Text("No approved providers for ${category.name} yet.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list ?: emptyList()) { o ->
            val p = people[o.providerId]
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p?.fullName ?: "Provider", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            p?.city,
                            p?.let { "${it.experienceYears} yrs experience" },
                            p?.let { if (it.ratingCount > 0) "rating ${it.ratingAvg} (${it.ratingCount})" else "no reviews yet" }
                        ).joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    val unit = if (o.pricingType == "per_unit") "per unit" else "per hour"
                    Text("${Money(o.customerRateMinor).format()} ${AppConfig.CURRENCY} $unit", style = MaterialTheme.typography.titleSmall)
                    if (o.visitFeeMinor > 0) Text("Visit fee ${Money(o.visitFeeMinor).format()} ${AppConfig.CURRENCY}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onBook(o, p) }) { Text("Book") }
                }
            }
        }
    }
}
