package com.personal.inout.util

import java.text.NumberFormat
import java.util.Locale

object IndianCurrencyFormatter {
    private val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    fun format(amount: Double): String {
        if (amount == 0.0 || amount == -0.0) return "₹ 0"
        return format.format(amount).replace("₹", "₹ ")
    }
    
    fun formatRaw(amount: Double): String {
        if (amount == 0.0 || amount == -0.0) return "0"
        return format.format(amount).replace("₹", "").trim()
    }
}

// Extensions for rapid Compose usage
fun Double.toIndianRupee(): String = IndianCurrencyFormatter.format(this)
fun Double.toIndianRupeeRaw(): String = IndianCurrencyFormatter.formatRaw(this)
