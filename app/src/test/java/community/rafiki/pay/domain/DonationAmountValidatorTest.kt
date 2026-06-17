package community.rafiki.pay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonationAmountValidatorTest {
    @Test
    fun acceptsWholePoundsWithinRange() {
        assertEquals(100L, DonationAmountValidator.poundsToPenceOrNull("1"))
        assertEquals(50000L, DonationAmountValidator.poundsToPenceOrNull("500"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        assertNull(DonationAmountValidator.poundsToPenceOrNull(""))
        assertNull(DonationAmountValidator.poundsToPenceOrNull("0"))
        assertNull(DonationAmountValidator.poundsToPenceOrNull("501"))
        assertNull(DonationAmountValidator.poundsToPenceOrNull("10.50"))
    }
}
