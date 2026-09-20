package com.inout.vault.parser

import android.content.Context
import com.google.ai.edge.litert.Interpreter
import com.inout.vault.data.MovementNature
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ParsedIntentResult(
    val movementNature: MovementNature,
    val amount: Long?,
    val note: String,
    val counterpartyOrCategory: String?,
    val confidence: Float
)

class LiteRtIntentParser(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val modelFileName = "intent_model_quantized.tflite"

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            val modelBuffer = loadModelFile(modelFileName)
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (_: Exception) {
            interpreter = null
        }
    }

    @Throws(Exception::class)
    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun parse(input: String): ParsedIntentResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ParsedIntentResult(
                movementNature = MovementNature.OUTFLOW,
                amount = null,
                note = "",
                counterpartyOrCategory = null,
                confidence = 0f
            )
        }

        val activeInterpreter = interpreter
        if (activeInterpreter != null) {
            try {
                return runModelInference(activeInterpreter, trimmed)
            } catch (_: Exception) {
                return fallbackDeterministicParse(trimmed)
            }
        }

        return fallbackDeterministicParse(trimmed)
    }

    private fun runModelInference(tflite: Interpreter, input: String): ParsedIntentResult {
        val inputBuffer = ByteBuffer.allocateDirect(1 * 256 * 4).order(ByteOrder.nativeOrder())
        val chars = input.toCharArray()
        for (i in 0 until 256) {
            if (i < chars.size) {
                inputBuffer.putFloat(chars[i].code.toFloat())
            } else {
                inputBuffer.putFloat(0f)
            }
        }

        val outputScores = Array(1) { FloatArray(7) }
        tflite.run(inputBuffer, outputScores)

        val scores = outputScores[0]
        var maxIndex = 0
        var maxScore = -1f
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIndex = i
            }
        }

        if (maxScore < 0.70f) {
            return fallbackDeterministicParse(input)
        }

        val nature = when (maxIndex) {
            1 -> MovementNature.INFLOW
            2 -> MovementNature.TRANSFER
            3 -> MovementNature.PEER_LEND
            4 -> MovementNature.PEER_BORROW
            5 -> MovementNature.PEER_COLLECT
            6 -> MovementNature.PEER_REPAY
            else -> MovementNature.OUTFLOW
        }

        return ParsedIntentResult(
            movementNature = nature,
            amount = extractAmount(input),
            note = input,
            counterpartyOrCategory = null,
            confidence = maxScore
        )
    }

    private fun fallbackDeterministicParse(input: String): ParsedIntentResult {
        val lower = input.lowercase()
        val nature = when {
            lower.startsWith("borrowed") || lower.startsWith("borrow") -> MovementNature.PEER_BORROW
            lower.startsWith("lent") || lower.startsWith("lend") -> MovementNature.PEER_LEND
            lower.startsWith("got") || lower.startsWith("received") -> MovementNature.PEER_COLLECT
            lower.startsWith("repaid") || lower.startsWith("paid back") -> MovementNature.PEER_REPAY
            lower.startsWith("transferred") || lower.startsWith("transfer") -> MovementNature.TRANSFER
            lower.startsWith("salary") || lower.startsWith("income") -> MovementNature.INFLOW
            else -> MovementNature.OUTFLOW
        }

        return ParsedIntentResult(
            movementNature = nature,
            amount = extractAmount(input),
            note = input,
            counterpartyOrCategory = null,
            confidence = 1.0f
        )
    }

    private fun extractAmount(input: String): Long? {
        val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)?\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
        val match = amountRegex.find(input)
        return match?.groups?.get(1)?.value?.replace(",", "")?.toDoubleOrNull()?.toLong()
    }
}
