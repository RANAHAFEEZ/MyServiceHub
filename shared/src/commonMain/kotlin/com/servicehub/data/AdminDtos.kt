package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComplaintDto(
    val id: String,
    @SerialName("booking_id") val bookingId: String,
    @SerialName("raised_by") val raisedBy: String,
    val reason: String,
    val details: String? = null,
    val status: String,
    @SerialName("admin_notes") val adminNotes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null
)

/** What the app sends when someone reports a problem. */
@Serializable
data class ComplaintInsert(
    @SerialName("booking_id") val bookingId: String,
    @SerialName("raised_by") val raisedBy: String,
    val reason: String,
    val details: String? = null
)

@Serializable
data class AdminStatsDto(
    @SerialName("pending_providers") val pendingProviders: Long,
    @SerialName("open_complaints") val openComplaints: Long,
    @SerialName("bookings_total") val bookingsTotal: Long,
    @SerialName("bookings_active") val bookingsActive: Long,
    @SerialName("bookings_paid") val bookingsPaid: Long,
    @SerialName("paid_total") val paidTotal: Long,
    @SerialName("paid_commission") val paidCommission: Long
)
