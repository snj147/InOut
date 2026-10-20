package com.personal.inout.util

object TaxLimitsConfig {
    /**
     * Note: Verify current limits as per your selected tax regime (Old vs New).
     * These limits change frequently based on the Indian Union Budget.
     * Values are stored in raw paise (₹1 = 100 paise).
     */
    val DEDUCTION_LIMITS = mapOf(
        "80C" to 150000L * 100L,              // LIC, PPF, ELSS, EPF
        "80D" to 75000L * 100L,               // Medical Insurance (incl. senior citizen)
        "80CCD(1B)" to 50000L * 100L,         // Additional NPS
        "24(b)" to 200000L * 100L,            // Home Loan Interest
        "80TTA/TTB" to 50000L * 100L          // Savings Account Interest
        // 80G (Donations) doesn't have a fixed ceiling (it's a % of adjusted gross income), so we omit a hard limit.
    )
}
