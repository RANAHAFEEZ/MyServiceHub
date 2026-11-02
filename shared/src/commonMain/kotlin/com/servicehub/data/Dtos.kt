package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These classes mirror the database columns exactly (names come from the SQL files).

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    @SerialName("pricing_type") val pricingType: String,
    @SerialName("commission_bps") val commissionBps: Int,
    @SerialName("min_minutes") val minMinutes: Int,
    @SerialName("visit_fee_minor") val visitFeeMinor: Long,
    @SerialName("sort_order") val sortOrder: Int
)

@Serializable
data class ProfileDto(
    val id: String,
    val role: String,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_blocked") val isBlocked: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

/** View provider_directory: approved providers, safe public fields only. */
@Serializable
data class ProviderDirectoryDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val city: String? = null,
    @SerialName("experience_years") val experienceYears: Int,
    val bio: String? = null,
    @SerialName("rating_avg") val ratingAvg: Double,
    @SerialName("rating_count") val ratingCount: Int
)

/** View provider_offers: what each provider charges the CUSTOMER (rate + commission already included). */
@Serializable
data class ProviderOfferDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("pricing_type") val pricingType: String,
    @SerialName("customer_rate_minor") val customerRateMinor: Long,
    @SerialName("min_minutes") val minMinutes: Int,
    @SerialName("visit_fee_minor") val visitFeeMinor: Long
)
