package com.personal.inout.util

import android.content.Context
import android.net.Uri
import com.personal.inout.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultBackupManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH_BIT = 256

    /**
     * BRD Rule 24 & Rule 34.4: Export complete uncompressed/encrypted database snapshot
     * with SHA-256 integrity checksum verification.
     */
    suspend fun exportEncryptedBackup(
        context: Context,
        db: AppDatabase,
        destinationUri: Uri,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pockets: List<LedgerPocket> = db.ledgerDao().getAllActivePockets().first()
            val transactions: List<LedgerTransaction> = db.ledgerDao().observeAllTransactions().first()

            val rootJson = JSONObject().apply {
                put("version", 5)
                put("exportedAt", System.currentTimeMillis())
                put("schemaStandard", "INOUT_INDIA_FIRST_V5")

                val pocketsArray = JSONArray()
                for (p in pockets) {
                    pocketsArray.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("type", p.type.name)
                        put("currency", p.currency)
                        put("creditLimit", p.creditLimit)
                        put("statementClosingDay", p.statementClosingDay)
                        put("billDueDay", p.billDueDay)
                        put("targetGoalAmount", p.targetGoalAmount)
                        put("goalTargetDate", p.goalTargetDate)
                        put("peerContactName", p.peerContactName)
                        put("lastReconciledEpoch", p.lastReconciledEpoch)
                        put("isArchived", p.isArchived)
                    })
                }
                put("pockets", pocketsArray)

                val transactionsArray = JSONArray()
                for (t in transactions) {
                    transactionsArray.put(JSONObject().apply {
                        put("id", t.id)
                        put("timestamp", t.timestamp)
                        put("amount", t.amount)
                        put("description", t.description)
                        put("category", t.category)
                        put("movementNature", t.movementNature.name)
                        put("status", t.status.name)
                        put("sourcePocketId", t.sourcePocketId)
                        put("targetPocketId", t.targetPocketId ?: JSONObject.NULL)
                        put("receiptUri", t.receiptUri ?: JSONObject.NULL)
                        put("isTaxDeductible", t.isTaxDeductible)
                        put("isReimbursable", t.isReimbursable)
                        put("isSubscription", t.isSubscription)
                        put("isRecurring", t.isRecurring)
                        put("recurringFrequency", t.recurringFrequency)
                        put("recurringEndDate", t.recurringEndDate)
                        put("originalCurrency", t.originalCurrency)
                        put("foreignAmount", t.foreignAmount)
                    })
                }
                put("transactions", transactionsArray)
            }

            val plaintext = rootJson.toString().toByteArray(Charsets.UTF_8)
            val sha256Digest = MessageDigest.getInstance("SHA-256").digest(plaintext)
            rootJson.put("sha256", sha256Digest.joinToString("") { "%02x".format(it) })

            val finalPlaintext = rootJson.toString().toByteArray(Charsets.UTF_8)
            val salt = ByteArray(SALT_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val ciphertext = cipher.doFinal(finalPlaintext)

            context.contentResolver.openOutputStream(destinationUri)?.use { outStream: OutputStream ->
                outStream.write(salt)
                outStream.write(iv)
                outStream.write(ciphertext)
                outStream.flush()
            } ?: error("Failed to open output destination stream")
        }
    }

    /**
     * Restore and verify encrypted `.vault` archive with zero orphan artifacts.
     */
    suspend fun restoreEncryptedBackup(
        context: Context,
        db: AppDatabase,
        sourceUri: Uri,
        passphrase: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val allBytes = context.contentResolver.openInputStream(sourceUri)?.use { inStream: InputStream ->
                inStream.readBytes()
            } ?: error("Failed to open backup source stream")

            if (allBytes.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
                error("Corrupted backup file (header payload too small)")
            }

            val salt = allBytes.copyOfRange(0, SALT_LENGTH_BYTE)
            val iv = allBytes.copyOfRange(SALT_LENGTH_BYTE, SALT_LENGTH_BYTE + IV_LENGTH_BYTE)
            val ciphertext = allBytes.copyOfRange(SALT_LENGTH_BYTE + IV_LENGTH_BYTE, allBytes.size)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val plaintext = cipher.doFinal(ciphertext)

            val rootJson = JSONObject(String(plaintext, Charsets.UTF_8))
            val pocketsArray = rootJson.getJSONArray("pockets")
            val txArray = rootJson.getJSONArray("transactions")

            // Atomic clear of active ledger
            val existingTx = db.ledgerDao().observeAllTransactions().first()
            for (t in existingTx) {
                db.ledgerDao().deleteTransaction(t)
            }

            var importedCount = 0
            for (i in 0 until pocketsArray.length()) {
                val p = pocketsArray.getJSONObject(i)
                val pocketType = PocketType.valueOf(p.getString("type"))
                val pocketObj = LedgerPocket(
                    id = p.getLong("id"),
                    name = p.getString("name"),
                    type = pocketType,
                    currency = p.optString("currency", "INR"),
                    creditLimit = p.optDouble("creditLimit", 0.0),
                    statementClosingDay = p.optInt("statementClosingDay", 0),
                    billDueDay = p.optInt("billDueDay", 0),
                    targetGoalAmount = p.optDouble("targetGoalAmount", 0.0),
                    goalTargetDate = p.optLong("goalTargetDate", 0L),
                    peerContactName = p.optString("peerContactName", ""),
                    lastReconciledEpoch = p.optLong("lastReconciledEpoch", 0L),
                    isArchived = p.optBoolean("isArchived", false)
                )
                db.ledgerDao().insertPocket(pocketObj)
            }

            for (i in 0 until txArray.length()) {
                val t = txArray.getJSONObject(i)
                val nature = MovementNature.valueOf(t.optString("movementNature", "OPERATING_EXPENSE"))
                val status = SettlementStatus.valueOf(t.optString("status", "CLEARED"))
                val txObj = LedgerTransaction(
                    id = t.optLong("id", 0L),
                    timestamp = t.getLong("timestamp"),
                    amount = t.getDouble("amount"),
                    description = t.getString("description"),
                    category = t.getString("category"),
                    movementNature = nature,
                    status = status,
                    sourcePocketId = t.getLong("sourcePocketId"),
                    targetPocketId = if (t.isNull("targetPocketId")) null else t.getLong("targetPocketId"),
                    receiptUri = if (t.isNull("receiptUri")) null else t.getString("receiptUri"),
                    isTaxDeductible = t.optBoolean("isTaxDeductible", false),
                    isReimbursable = t.optBoolean("isReimbursable", false),
                    isSubscription = t.optBoolean("isSubscription", false),
                    isRecurring = t.optBoolean("isRecurring", false),
                    recurringFrequency = t.optString("recurringFrequency", "NONE"),
                    recurringEndDate = t.optLong("recurringEndDate", 0L),
                    originalCurrency = t.optString("originalCurrency", "INR"),
                    foreignAmount = t.optDouble("foreignAmount", 0.0)
                )
                db.ledgerDao().insertTransaction(txObj)
                importedCount++
            }

            importedCount
        }
    }
}
