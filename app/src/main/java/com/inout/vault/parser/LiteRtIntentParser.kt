package com.inout.vault.parser

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ParsedIntentResult(
    val movementNature: String,
    val amount: Long?,
    val noteOrEntity: String?,
    val confidence: Float
)

class LiteRtIntentParser(private val context: Context) {
    private var interpreter: Interpreter? = null

    init {
        try {
            val buffer = loadModelFile("intent_model_quantized.tflite")
            interpreter = Interpreter(buffer)
        } catch (_: Exception) {
            interpreter = null
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun parse(input: String): ParsedIntentResult {
        if (interpreter == null) {
            return fallbackRegexParse(input)
        }

        return try {
            // Placeholder inference pipeline hook: Fallback to regex if parsing fails or confidence is low
            fallbackRegexParse(input)
        } catch (_: Exception) {
            fallbackRegexParse(input)
        }
    }

    private fun fallbackRegexParse(input: String): ParsedIntentResult {
        val trimmed = input.trim()
        val amountRegex = Regex("""(\d+(?:,\d+)*(?:\.\d+)?)""")
        val match = amountRegex.find(trimmed)
        val amount = match?.value?.replace(",", "")?.toDoubleOrNull()?.toLong()

        val nature = when {
            trimmed.startsWith("borrowed", ignoreCase = true) -> "PEER_BORROW"
            trimmed.startsWith("lent", ignoreCase = true) || trimmed.startsWith("lend", ignoreCase = true) -> "PEER_LEND"
            trimmed.startsWith("got", ignoreCase = true) || trimmed.startsWith("received", ignoreCase = true) -> "PEER_COLLECT"
            trimmed.startsWith("transferred", ignoreCase = true) -> "TRANSFER"
            else -> "OUTFLOW"
        }

        return ParsedIntentResult(
            movementNature = nature,
            amount = amount,
            noteOrEntity = trimmed,
            confidence = 1.0f
        )
    }
}
