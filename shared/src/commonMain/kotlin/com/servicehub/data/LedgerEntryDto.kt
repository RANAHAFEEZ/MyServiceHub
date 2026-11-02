package com.servicehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One line in a provider's commission account. + = owes more, - = owes less. */
@Serializable
data class LedgerEntryDto(
    val id: Long,
    @SerialName("provider_id") val providerId: String,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("entry_type") val entryType: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String
)

/** The provider's own numbers (RPC my_ledger_summary). */
@Serializable
data class LedgerSummaryDto(
    @SerialName("owed_minor") val owedMinor: Long,
    @SerialName("limit_minor") val limitMinor: Long,
    @SerialName("earned_minor") val earnedMinor: Long,
    @SerialName("paid_jobs") val paidJobs: Long,
    @SerialName("commission_total_minor") val commissionTotalMinor: Long
)

/** One provider row on the admin Ledger tab (RPC admin_provider_balances). */
@Serializable
data class ProviderBalanceDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val verification: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("owed_minor") val owedMinor: Long,
    @SerialName("paid_jobs") val paidJobs: Long,
    @SerialName("over_limit") val overLimit: Boolean
)
