package community.rafiki.pay.domain

object DonationAmountValidator {
    const val MIN_POUNDS = 1
    const val MAX_POUNDS = 500

    fun poundsToPenceOrNull(input: String): Long? {
        val pounds = input.trim().toIntOrNull() ?: return null
        return poundsToPenceOrNull(pounds)
    }

    fun poundsToPenceOrNull(pounds: Int): Long? {
        if (pounds !in MIN_POUNDS..MAX_POUNDS) return null
        return pounds * 100L
    }

    fun displayPounds(amountPence: Long): String {
        return "\u00A3${amountPence / 100L}"
    }
}
