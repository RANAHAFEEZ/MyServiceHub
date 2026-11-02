package com.servicehub

import com.servicehub.model.buildQatarTimestamp
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeFormatTest {
    @Test fun utcIsShownInQatarTime() {
        assertEquals("2026-11-01 09:00", formatLocalDateTime("2026-11-01T06:00:00+00:00"))
        assertEquals("2026-11-02 01:30", formatLocalDateTime("2026-11-01T22:30:00+00:00"))
        assertEquals("2027-01-01 02:00", formatLocalDateTime("2026-12-31T23:00:00Z"))
        assertEquals("2028-02-29 01:00", formatLocalDateTime("2028-02-28T22:00:00+00:00"))
        assertEquals("2026-11-02 09:00", formatLocalDateTime("2026-11-02T09:00:00+03:00"))
        assertEquals("2026-11-01 09:00", formatLocalDateTime("2026-11-01T06:00:00.123456+00:00"))
    }

    @Test fun unknownFormatIsReturnedAsIs() {
        assertEquals("tomorrow", formatLocalDateTime("tomorrow"))
    }

    @Test fun buildsQatarTimestamp() {
        assertEquals("2026-11-02T09:00:00+03:00", buildQatarTimestamp("2026-11-02", "9:00"))
        assertEquals("2026-11-02T18:05:00+03:00", buildQatarTimestamp("2026-11-02", "18:05"))
        assertEquals("2028-02-29T10:00:00+03:00", buildQatarTimestamp("2028-02-29", "10:00"))
    }

    @Test fun rejectsInvalidDatesAndTimes() {
        assertNull(buildQatarTimestamp("2026-13-01", "09:00"))
        assertNull(buildQatarTimestamp("2026-02-30", "09:00"))
        assertNull(buildQatarTimestamp("2027-02-29", "09:00"))
        assertNull(buildQatarTimestamp("2026-11-02", "24:00"))
        assertNull(buildQatarTimestamp("2026-11-02", "09:60"))
        assertNull(buildQatarTimestamp("02/11/2026", "09:00"))
        assertNull(buildQatarTimestamp("", ""))
    }

    @Test fun friendlyErrors() {
        assertTrue(friendlyBookingError("ERROR: time_in_past").contains("future"))
        assertEquals("Something went wrong", friendlyBookingError(null))
        assertEquals("some other message", friendlyBookingError("some other message"))
    }
}
