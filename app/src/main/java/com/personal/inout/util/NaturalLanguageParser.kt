package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.VaultPocket

object NaturalLanguageParser {
    fun parse(input: String, activePockets: List<VaultPocket>, context: Context? = null): ParsedIntent? {
        return FuzzyCommandParser.parse(input, activePockets, context)
    }
}
