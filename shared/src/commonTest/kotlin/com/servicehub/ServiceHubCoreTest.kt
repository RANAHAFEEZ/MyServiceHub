package com.servicehub.shared

import com.servicehub.booking.Actor
import com.servicehub.booking.BookingStateMachine
import com.servicehub.booking.BookingStatus
import com.servicehub.booking.TransitionResult
import com.servicehub.model.CategoryConfig
import com.servicehub.model.Money
import com.servicehub.model.PricingType
import com.servicehub.model.ProviderProfile
import com.servicehub.model.ServiceCategory
import com.servicehub.model.validateProviderProfile
import com.servicehub.pricing.BookingInput
import com.servicehub.pricing.PricingCalculator
import com.servicehub.pricing.QuoteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceHubCoreTest {

    private fun hourly(min: Int = 120, bps: Int = 1500, visit: Money = Money.ZERO) =
        CategoryConfig(ServiceCategory.CLEANER, true, PricingType.HOURLY, bps, min, visit)

    private fun ok(r: QuoteResult) = (r as QuoteResult.Ok).quote

    @Test fun customerRate_isProviderRatePlusCommission() {
        // provider 20.00/hr + 15 % = 23.00/hr
        assertEquals(Money(2300), PricingCalculator.customerRate(Money(2000), 1500))
    }

    @Test fun hourly_appliesMinimumHours() {
        val q = ok(PricingCalculator.quote(hourly(min = 120), Money(2000), BookingInput(estimatedMinutes = 90)))
        assertEquals(120, q.billableMinutes)
        assertEquals(Money(4000), q.providerEarnings)
        assertEquals(Money(600), q.commission)
        assertEquals(Money(4600), q.customerTotal)
    }

    @Test fun hourly_roundsUpToQuarterHour() {
        val q = ok(PricingCalculator.quote(hourly(min = 60), Money(2000), BookingInput(estimatedMinutes = 100)))
        assertEquals(105, q.billableMinutes)
        assertEquals(Money(3500), q.providerEarnings)
    }

    @Test fun perUnit_multipliesQuantity() {
        val cfg = CategoryConfig(ServiceCategory.CLEANER, true, PricingType.PER_UNIT, 1000)
        val q = ok(PricingCalculator.quote(cfg, Money(1500), BookingInput(quantity = 3)))
        assertEquals(Money(4500), q.providerEarnings)
        assertEquals(Money(450), q.commission)
        assertEquals(Money(4950), q.customerTotal)
    }

    @Test fun visitFee_isIncludedBeforeCommission() {
        val q = ok(PricingCalculator.quote(hourly(min = 60, visit = Money(1000)), Money(2000), BookingInput(estimatedMinutes = 60)))
        assertEquals(Money(3000), q.providerEarnings)   // 20.00 labour + 10.00 visit
        assertEquals(Money(450), q.commission)          // 15 % of 30.00
        assertEquals(Money(3450), q.customerTotal)
    }

    @Test fun quoteCategory_needsProviderQuote() {
        val cfg = CategoryConfig(ServiceCategory.PAINTER, true, PricingType.QUOTE, 1500)
        assertTrue(PricingCalculator.quote(cfg, Money(2000), BookingInput()) is QuoteResult.NeedsProviderQuote)
    }

    @Test fun disabledCategory_isInvalid() {
        val cfg = CategoryConfig(ServiceCategory.MASON, false, PricingType.HOURLY, 1500)
        assertTrue(PricingCalculator.quote(cfg, Money(2000), BookingInput(estimatedMinutes = 60)) is QuoteResult.Invalid)
    }

    @Test fun money_formatsWithDecimals() {
        assertEquals("23.00", Money(2300).format())
        assertEquals("0.05", Money(5).format())
        assertEquals("-1.50", Money(-150).format())
    }

    @Test fun bookingFlow_happyPath() {
        var s = BookingStatus.REQUESTED
        val steps = listOf(
            BookingStatus.ACCEPTED to Actor.PROVIDER, BookingStatus.ON_THE_WAY to Actor.PROVIDER,
            BookingStatus.IN_PROGRESS to Actor.PROVIDER, BookingStatus.COMPLETED to Actor.PROVIDER,
            BookingStatus.PAID to Actor.CONSUMER, BookingStatus.REVIEWED to Actor.CONSUMER
        )
        for ((next, who) in steps) {
            val r = BookingStateMachine.transition(s, next, who)
            assertTrue(r is TransitionResult.Ok, "failed at $s -> $next")
            s = (r as TransitionResult.Ok).status
        }
        assertEquals(BookingStatus.REVIEWED, s)
    }

    @Test fun bookingFlow_blocksWrongActorsAndJumps() {
        assertTrue(BookingStateMachine.transition(BookingStatus.REQUESTED, BookingStatus.PAID, Actor.PROVIDER) is TransitionResult.Rejected)
        assertTrue(BookingStateMachine.transition(BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS, Actor.PROVIDER) is TransitionResult.Rejected)
        assertTrue(BookingStateMachine.transition(BookingStatus.ACCEPTED, BookingStatus.ON_THE_WAY, Actor.CONSUMER) is TransitionResult.Rejected)
        assertTrue(BookingStateMachine.transition(BookingStatus.REQUESTED, BookingStatus.CANCELLED, Actor.CONSUMER) is TransitionResult.Ok)
        assertEquals(listOf(BookingStatus.ACCEPTED, BookingStatus.DECLINED), BookingStateMachine.allowedNext(BookingStatus.REQUESTED, Actor.PROVIDER))
    }

    @Test fun profileValidation() {
        val good = ProviderProfile("1", "Ali Khan", "+974 5555 1234", 5, setOf(ServiceCategory.PLUMBER), Money(2500), "Doha")
        assertTrue(validateProviderProfile(good).isEmpty())
        val bad = good.copy(name = "A", phone = "12", categories = emptySet(), rate = Money(0))
        assertEquals(4, validateProviderProfile(bad).size)
    }
}
