package com.servicehub

import com.servicehub.model.parseMoneyToMinor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParsingTest {
    @Test fun parsesNormalAmounts() {
        assertEquals(2500L, parseMoneyToMinor("25"))
        assertEquals(2550L, parseMoneyToMinor("25.5"))
        assertEquals(2550L, parseMoneyToMinor("25,50"))
        assertEquals(50L, parseMoneyToMinor(".5"))
        assertEquals(0L, parseMoneyToMinor("0"))
        assertEquals(2505L, parseMoneyToMinor(" 25.05 "))
    }

    @Test fun rejectsBadInput() {
        assertNull(parseMoneyToMinor(""))
        assertNull(parseMoneyToMinor("abc"))
        assertNull(parseMoneyToMinor("25.555"))
        assertNull(parseMoneyToMinor("1.2.3"))
        assertNull(parseMoneyToMinor("-5"))
        assertNull(parseMoneyToMinor("12345678901"))
    }
}
