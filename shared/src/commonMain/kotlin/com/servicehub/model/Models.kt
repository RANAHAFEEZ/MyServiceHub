package com.servicehub.model

enum class Role { CONSUMER, PROVIDER, ADMIN }

enum class ServiceCategory(val displayName: String) {
    CLEANER("Cleaner"),
    PLUMBER("Plumber"),
    ELECTRICIAN("Electrician"),
    PAINTER("Painter"),
    MASON("Mason")
}

/** How a category is priced. Admin chooses this per category. */
enum class PricingType {
    HOURLY,    // provider rate is per hour (cleaner, mason by the hour)
    PER_UNIT,  // provider rate is per unit, e.g. per car washed, per room
    QUOTE      // no automatic price: provider sends a quote after seeing the job (painter, big mason jobs)
}

enum class VerificationStatus { PENDING, APPROVED, REJECTED, SUSPENDED }

/**
 * Admin-controlled settings for one category. Nothing here is hard-coded in the app:
 * the values come from the backend so the admin panel can change them.
 */
data class CategoryConfig(
    val category: ServiceCategory,
    val enabled: Boolean,
    val pricingType: PricingType,
    val commissionBps: Int,        // basis points: 1500 = 15.00 %
    val minMinutes: Int = 60,      // minimum billable time for HOURLY jobs
    val visitFee: Money = Money.ZERO // optional call-out fee (plumber/electrician)
) {
    init {
        require(commissionBps in 0..10_000) { "commission must be 0..100 %" }
        require(minMinutes >= 0)
    }
}

data class ProviderProfile(
    val id: String,
    val name: String,
    val phone: String,
    val experienceYears: Int,
    val categories: Set<ServiceCategory>,
    val rate: Money,                 // provider's own rate (per hour or per unit)
    val city: String,
    val verification: VerificationStatus = VerificationStatus.PENDING
)
