package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AppSettingsDto(
    val id: Int = 1,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("currency_decimals") val currencyDecimals: Int = 2,
    @SerialName("request_expiry_minutes") val requestExpiryMinutes: Int,
    @SerialName("max_open_requests") val maxOpenRequests: Int = 5,
    @SerialName("min_notice_minutes") val minNoticeMinutes: Int = 30,
    @SerialName("max_days_ahead") val maxDaysAhead: Int = 60,
    @SerialName("max_minutes_per_booking") val maxMinutesPerBooking: Int = 720,
    @SerialName("max_quantity") val maxQuantity: Int = 20
)

@Serializable
data class TicketDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val subject: String,
    val message: String,
    val status: String,
    @SerialName("admin_reply") val adminReply: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TicketInsert(
    @SerialName("user_id") val userId: String,
    val subject: String,
    val message: String
)

@Serializable
data class AuditDto(
    val id: Long,
    @SerialName("admin_id") val adminId: String? = null,
    val action: String,
    val target: String? = null,
    val details: JsonElement? = null,
    @SerialName("created_at") val createdAt: String
)
