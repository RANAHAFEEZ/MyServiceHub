package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of the bookings table (all columns). */
@Serializable
data class BookingDto(
    val id: String,
    @SerialName("consumer_id") val consumerId: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("category_id") val categoryId: String,
    val status: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("address_text") val addressText: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    val quantity: Int = 1,
    @SerialName("provider_rate_minor") val providerRateMinor: Long,
    @SerialName("commission_bps") val commissionBps: Int,
    @SerialName("visit_fee_minor") val visitFeeMinor: Long = 0,
    @SerialName("billable_minutes") val billableMinutes: Int? = null,
    @SerialName("provider_earnings_minor") val providerEarningsMinor: Long,
    @SerialName("commission_minor") val commissionMinor: Long,
    @SerialName("total_minor") val totalMinor: Long,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** Answer of the server function get_quote (the price is always calculated on the server). */
@Serializable
data class QuoteDto(
    @SerialName("provider_earnings_minor") val providerEarningsMinor: Long,
    @SerialName("commission_minor") val commissionMinor: Long,
    @SerialName("total_minor") val totalMinor: Long,
    @SerialName("billable_minutes") val billableMinutes: Int? = null,
    @SerialName("provider_rate_minor") val providerRateMinor: Long,
    @SerialName("commission_bps") val commissionBps: Int,
    @SerialName("visit_fee_minor") val visitFeeMinor: Long = 0
)
