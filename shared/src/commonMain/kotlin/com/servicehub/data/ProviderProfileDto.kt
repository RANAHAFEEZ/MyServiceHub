package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("experience_years") val experienceYears: Int = 0,
    val bio: String? = null,
    val verification: String,
    @SerialName("id_document_path") val idDocumentPath: String? = null,
    @SerialName("selfie_path") val selfiePath: String? = null,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ProviderServiceDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("rate_minor") val rateMinor: Long,
    val active: Boolean
)

/** One row on the admin screen: the provider's details + their basic profile. */
data class AdminProviderRow(val provider: ProviderProfileDto, val profile: ProfileDto?)
