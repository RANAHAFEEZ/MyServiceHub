package com.servicehub.pricing

import com.servicehub.model.CategoryConfig
import com.servicehub.model.Money
import com.servicehub.model.PricingType

/** What the consumer typed into the booking form. */
data class BookingInput(
    val estimatedMinutes: Int = 0, // HOURLY
    val quantity: Int = 1          // PER_UNIT (e.g. number of cars)
)

/**
 * providerEarnings = what the provider gets (their own rate x time/quantity + visit fee).
 * commission       = added ON TOP by the platform (so the provider is never paid less than their rate).
 * customerTotal    = providerEarnings + commission.
 */
data class Quote(
    val providerEarnings: Money,
    val commission: Money,
    val customerTotal: Money,
    val billableMinutes: Int?,
    val lines: List<String>
)

sealed interface QuoteResult {
    data class Ok(val quote: Quote) : QuoteResult
    /** Category is priced by quote: the provider must send a price after seeing the job. */
    data object NeedsProviderQuote : QuoteResult
    data class Invalid(val reason: String) : QuoteResult
}

object PricingCalculator {
    private const val ROUND_UP_TO_MINUTES = 15

    /** The hourly/unit rate the consumer sees on a provider card: rate + commission. */
    fun customerRate(providerRate: Money, commissionBps: Int): Money =
        providerRate + commissionOn(providerRate, commissionBps)

    fun commissionOn(base: Money, commissionBps: Int): Money =
        Money(Money.mulDiv(base.minor, commissionBps.toLong(), 10_000))

    fun quote(config: CategoryConfig, providerRate: Money, input: BookingInput): QuoteResult {
        if (!config.enabled) return QuoteResult.Invalid("This category is not available yet")
        if (providerRate.minor <= 0) return QuoteResult.Invalid("Provider rate is not set")

        val lines = mutableListOf<String>()
        var billable: Int? = null

        val labour: Money = when (config.pricingType) {
            PricingType.QUOTE -> return QuoteResult.NeedsProviderQuote
            PricingType.HOURLY -> {
                if (input.estimatedMinutes <= 0) return QuoteResult.Invalid("Enter the estimated time")
                val minutes = maxOf(roundUp(input.estimatedMinutes, ROUND_UP_TO_MINUTES), config.minMinutes)
                billable = minutes
                lines += "Labour: $minutes min"
                Money(Money.mulDiv(providerRate.minor, minutes.toLong(), 60))
            }
            PricingType.PER_UNIT -> {
                if (input.quantity <= 0) return QuoteResult.Invalid("Quantity must be at least 1")
                lines += "Labour: ${input.quantity} unit(s)"
                providerRate * input.quantity
            }
        }

        var base = labour
        if (config.visitFee.minor > 0) {
            base += config.visitFee
            lines += "Visit fee included"
        }
        val commission = commissionOn(base, config.commissionBps)
        return QuoteResult.Ok(Quote(base, commission, base + commission, billable, lines))
    }

    private fun roundUp(value: Int, step: Int) = ((value + step - 1) / step) * step
}
