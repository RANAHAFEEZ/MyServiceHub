package com.servicehub.model

/**
 * Money is stored as a whole number of minor units (cents/fils/paisa) so there are never
 * floating-point rounding errors. Example: 23.00 is Money(2300).
 * Which currency and how many decimals it has is a business setting, not part of this class.
 */
data class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun times(qty: Int) = Money(minor * qty)
    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    /** "23.00" style text (2 decimals). Add the currency symbol in the UI. */
    fun format(decimals: Int = 2): String {
        require(decimals in 0..4)
        var p = 1L
        repeat(decimals) { p *= 10 }
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        if (decimals == 0) return "$sign$abs"
        return "$sign${abs / p}.${(abs % p).toString().padStart(decimals, '0')}"
    }

    companion object {
        val ZERO = Money(0)
        /** Round-half-up of (value * numerator / denominator) for non-negative values. */
        fun mulDiv(value: Long, numerator: Long, denominator: Long): Long {
            require(denominator > 0)
            return (value * numerator + denominator / 2) / denominator
        }
    }
}
