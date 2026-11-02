package com.servicehub.model

// Small date/time helpers (no library needed). Qatar is UTC+3 all year (no daylight saving).

private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146097L + doe - 719468L
}

private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = (z - era * 146097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400L
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple((y + (if (m <= 2) 1 else 0)).toInt(), m, d)
}

private fun pad2(n: Int) = n.toString().padStart(2, '0')

private fun offsetMinutes(s: String): Int {
    if (s.isEmpty() || s == "Z") return 0
    val sign = if (s[0] == '-') -1 else 1
    val digits = s.substring(1).replace(":", "")
    val hh = digits.substring(0, 2).toInt()
    val mm = if (digits.length >= 4) digits.substring(2, 4).toInt() else 0
    return sign * (hh * 60 + mm)
}

private val isoRegex =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::\\d{2}(?:\\.\\d+)?)?(Z|[+-]\\d{2}(?::?\\d{2})?)?$")

/** "2026-11-01T22:30:00+00:00" -> "2026-11-02 01:30" (shown in Qatar time). Unknown formats are returned unchanged. */
fun formatLocalDateTime(iso: String, targetOffsetMinutes: Int = 180): String {
    val m = isoRegex.find(iso.trim()) ?: return iso
    val (ys, mos, ds, hs, mis, off) = m.destructured
    val total = daysFromCivil(ys.toInt(), mos.toInt(), ds.toInt()) * 1440L +
            hs.toInt() * 60L + mis.toInt() - offsetMinutes(off) + targetOffsetMinutes
    val days = total.floorDiv(1440L)
    val rem = total.mod(1440L).toInt()
    val (y, mo, d) = civilFromDays(days)
    return "$y-${pad2(mo)}-${pad2(d)} ${pad2(rem / 60)}:${pad2(rem % 60)}"
}

private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
private fun daysInMonth(y: Int, m: Int) = when (m) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (isLeap(y)) 29 else 28
}

/** "2026-11-02" + "9:00" -> "2026-11-02T09:00:00+03:00" (Qatar time). Returns null if invalid. */
fun buildQatarTimestamp(date: String, time: String): String? {
    val d = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(date.trim()) ?: return null
    val t = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(time.trim()) ?: return null
    val (ys, ms, ds) = d.destructured
    val (hs, mis) = t.destructured
    val y = ys.toInt(); val mo = ms.toInt(); val da = ds.toInt()
    val h = hs.toInt(); val mi = mis.toInt()
    if (mo !in 1..12 || da !in 1..daysInMonth(y, mo) || h !in 0..23 || mi !in 0..59) return null
    return "$y-${pad2(mo)}-${pad2(da)}T${pad2(h)}:${pad2(mi)}:00+03:00"
}

/** Turns raw server error codes into messages a customer can understand. */
fun friendlyBookingError(raw: String?): String {
    val m = raw ?: return "Something went wrong"
    return when {
        "time_in_past" in m -> "Please choose a time in the future."
        "too_soon" in m -> "Please choose a later time. Bookings need some notice."
        "too_far" in m -> "That date is too far ahead. Please choose a nearer date."
        "too_long" in m -> "That is longer than the maximum hours allowed for one booking."
        "too_many_units" in m -> "That quantity is more than allowed for one booking."
        "too_many_open_requests" in m -> "You already have the maximum number of open bookings. Finish or cancel one first."
        "duplicate_request" in m -> "You already have a pending request with this provider for this service."
        "provider_busy" in m -> "You already have an accepted job at that time."
        "provider_not_approved" in m -> "Your profile is not approved yet, so you cannot accept jobs."
        "request_expired" in m -> "This request is for a time that has already passed."
        "commission_out_of_range" in m -> "Commission must be between 0% and 50%."
        "min_minutes_out_of_range" in m -> "Minimum minutes must be between 0 and 480."
        "visit_fee_invalid" in m -> "Visit fee cannot be negative."
        "cannot_block_yourself" in m -> "You cannot block your own account."
        "cannot_block_admin" in m -> "An admin account cannot be blocked."
        "provider_over_limit" in m -> "This provider has reached the commission limit. New requests are paused until the balance is paid."
        "provider_has_active_bookings" in m -> "You cannot change your trade while you have open requests or jobs."
        "invalid_amount" in m -> "Please enter a valid amount."
        "note_required" in m -> "Please write a short reason."
        "invalid_rate" in m -> "Please enter a valid rate."
        "provider_unavailable" in m -> "This provider is not available for this service right now."
        "category_unavailable" in m -> "This service is not open yet."
        "invalid_minutes" in m -> "Please enter how many hours you need."
        "invalid_quantity" in m -> "Please enter a valid quantity."
        "address_required" in m -> "Please enter your address."
        "cannot_book_yourself" in m -> "You cannot book yourself."
        "account_blocked" in m -> "Your account is blocked. Please contact support."
        "transition_not_allowed" in m -> "That action is not allowed at this stage."
        "booking_not_paid" in m -> "You can review a booking after it is paid."
        "not_authenticated" in m -> "Please sign in again."
        "not_allowed" in m -> "You are not allowed to do that."
        else -> m
    }
}
