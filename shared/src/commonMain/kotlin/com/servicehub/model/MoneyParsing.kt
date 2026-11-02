package com.servicehub.model

/**
 * Turns what a person types ("25", "25.5", "25,50") into minor units (2500, 2550, 2550).
 * Returns null if the text is not a valid amount.
 */
fun parseMoneyToMinor(text: String, decimals: Int = 2): Long? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val parts = t.split('.')
    if (parts.size > 2) return null
    val whole = parts[0]
    val frac = if (parts.size == 2) parts[1] else ""
    if (whole.isEmpty() && frac.isEmpty()) return null
    if (!whole.all { it in '0'..'9' } || !frac.all { it in '0'..'9' }) return null
    if (whole.length > 9 || frac.length > decimals) return null
    val w = if (whole.isEmpty()) 0L else (whole.toLongOrNull() ?: return null)
    var p = 1L
    repeat(decimals) { p *= 10 }
    val f = if (decimals == 0) 0L else frac.padEnd(decimals, '0').toLong()
    return w * p + f
}
