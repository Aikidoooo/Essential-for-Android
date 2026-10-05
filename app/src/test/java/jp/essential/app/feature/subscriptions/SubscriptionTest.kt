package jp.essential.app.feature.subscriptions

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SubscriptionTest {
    @Test fun clampsPaymentToMonthEndIncludingLeapYear() {
        val entry = Subscription(name = "月末", yen = 980, billingDay = 31, startDate = LocalDate.of(2024, 1, 1))
        assertEquals(LocalDate.of(2024, 2, 29), entry.paymentIn(YearMonth.of(2024, 2)))
        assertEquals(LocalDate.of(2025, 2, 28), entry.paymentIn(YearMonth.of(2025, 2)))
        assertEquals(LocalDate.of(2025, 4, 30), entry.paymentIn(YearMonth.of(2025, 4)))
        assertEquals(LocalDate.of(2025, 5, 31), entry.paymentIn(YearMonth.of(2025, 5)))
    }
    @Test fun excludesPaymentsBeforeStart() {
        val entry = Subscription(name = "月額", yen = 500, billingDay = 10, startDate = LocalDate.of(2026, 10, 11))
        assertNull(entry.paymentIn(YearMonth.of(2026, 9)))
        assertNull(entry.paymentIn(YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 11, 10), entry.paymentIn(YearMonth.of(2026, 11)))
    }
}
