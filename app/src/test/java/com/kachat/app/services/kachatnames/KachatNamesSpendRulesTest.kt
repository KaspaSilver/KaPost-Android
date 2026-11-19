package com.kachat.app.services.kachatnames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * When a name counts as expiring soon (iOS 24d673a, IOS-060). The spend rules of the .kachat
 * actions went with them: KaPost signs no registry transaction (iOS b78fdb0).
 */
class KachatNamesSpendRulesTest {

    // Expires soon follows the period (IOS-060)

    private fun params(periodMs: Long, renewWindowMs: Long) = Params(
        bond = 0, gapValue = 0, tCommit = 600, maxYears = 2, periodMs = periodMs, graceMs = periodMs,
        renewWindowMs = renewWindowMs, registerPrices = List(5) { 0L }, renewPrices = List(5) { 0L }, offerMaxFee = 0
    )

    @Test
    fun expiresSoonIsThirtyDaysOnAYearlyClockAndTheRenewalWindowOnAShortOne() {
        // mainnet: a year, a 10-day renewal window -> 30 days
        assertEquals(30L * 86_400_000L, params(KachatNames.YEAR_MS, 10L * 86_400_000L).expiresSoonMs)
        // testnet-10: 10-minute periods and window -> the window, not 30 days that cover every name
        assertEquals(600_000L, params(600_000L, 600_000L).expiresSoonMs)
        // a short window on a short clock: a twelfth of the period
        assertEquals(50_000L, params(600_000L, 10_000L).expiresSoonMs)
    }
}
