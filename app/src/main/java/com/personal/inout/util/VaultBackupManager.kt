package com.personal.inout.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
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
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultBackupManager {
    // V6 Modern Constants
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATION_COUNT = 600000 
    private const val KEY_LENGTH_BIT = 256

    // Legacy Vault Constants
    private const val LEGACY_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val LEGACY_IV_LENGTH_BYTE = 16
    private const val LEGACY_ITERATION_COUNT = 10000

    suspend fun exportEncryptedBackup(
        context: Context,
        db: AppDatabase,
        destinationUri: Uri,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pockets: List<LedgerPocket> = db.ledgerDao().getAllActivePockets().first()
            val transactions: List<LedgerTransaction> = db.ledgerDao().observeAllTransactions().first()
            val audits: List<LedgerAuditEntry> = db.ledgerDao().observeAuditJournal().first()

            val rootJson = JSONObject().apply {
                put("version", 6)
                put("exportedAt", System.currentTimeMillis())
                put("schemaStandard", "INOUT_INDIA_V6")

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
                        put("createdAt", p.createdAt)
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
                        put("splitParentId", t.splitParentId ?: JSONObject.NULL)
                        put("idempotencyHash", t.idempotencyHash)
                    })
                }
                put("transactions", transactionsArray)
                
                val auditArray = JSONArray()
                for (a in audits) {
                    auditArray.put(JSONObject().apply {
                        put("auditId", a.auditId)
                        put("actionType", a.actionType)
                        put("entityType", a.entityType)
                        put("recordId", a.recordId)
                        put("preStateJson", a.preStateJson)
                        put("postStateJson", a.postStateJson)
                        put("reasonNote", a.reasonNote)
                        put("timestamp", a.timestamp)
                    })
                }
                put("audits", auditArray)
            }

            val plaintextForHash = rootJson.toString().toByteArray(Charsets.UTF_8)
            val sha256Digest = MessageDigest.getInstance("SHA-256").digest(plaintextForHash)
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

    suspend fun restoreEncryptedBackup(
        context: Context,
        db: AppDatabase,
        sourceUri: Uri,
        passphrase: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        
        val allBytes = try {
            context.contentResolver.openInputStream(sourceUri)?.use { inStream: InputStream ->
                inStream.readBytes()
            } ?: error("Failed to open backup source stream")
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        // Attempt V6 Modern GCM Decryption First
        val v6Result = runCatching {
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
            
            val providedHash = rootJson.optString("sha256", "")
            rootJson.remove("sha256") 
            val verificationHashBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
            val calculatedHash = MessageDigest.getInstance("SHA-256").digest(verificationHashBytes).joinToString("") { "%02x".format(it) }
            
            if (providedHash.isNotEmpty() && providedHash != calculatedHash) {
                error("Integrity failure: Backup hash mismatch. File may be corrupted or tampered with.")
            }

            importParsedJsonToDatabase(db, rootJson)
        }

        if (v6Result.isSuccess) return@withContext v6Result

        // Fallback: Attempt Legacy V5/CBC Decryption if GCM fails
        val legacyResult = runCatching {
            if (allBytes.size < SALT_LENGTH_BYTE + LEGACY_IV_LENGTH_BYTE) {
                error("Corrupted legacy file (header payload too small)")
            }

            val salt = allBytes.copyOfRange(0, SALT_LENGTH_BYTE)
            val iv = allBytes.copyOfRange(SALT_LENGTH_BYTE, SALT_LENGTH_BYTE + LEGACY_IV_LENGTH_BYTE)
            val ciphertext = allBytes.copyOfRange(SALT_LENGTH_BYTE + LEGACY_IV_LENGTH_BYTE, allBytes.size)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") // Legacy used SHA1
            val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, LEGACY_ITERATION_COUNT, KEY_LENGTH_BIT)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(LEGACY_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val plaintext = cipher.doFinal(ciphertext)

            val rootJson = JSONObject(String(plaintext, Charsets.UTF_8))
            importParsedJsonToDatabase(db, rootJson)
        }

        if (legacyResult.isSuccess) return@withContext legacyResult
        
        // If both fail, return the original V6 error since that's what we expect by default
        return@withContext Result.failure(Exception("Decryption failed. Invalid passphrase or corrupted backup file."))
    }

    private suspend fun importParsedJsonToDatabase(db: AppDatabase, rootJson: JSONObject): Int {
        val pocketsArray = rootJson.getJSONArray("pockets")
        val txArray = rootJson.getJSONArray("transactions")
        val auditArray = rootJson.optJSONArray("audits") ?: JSONArray()

        var importedCount = 0
        
        db.withTransaction {
            val dao = db.ledgerDao()
            dao.observeAllTransactions().first().forEach { dao.deleteTransaction(it) }
            dao.getAllActivePockets().first().forEach { dao.deletePocket(it) }

            for (i in 0 until pocketsArray.length()) {
                val p = pocketsArray.getJSONObject(i)
                val pocketType = PocketType.valueOf(p.getString("type"))
                val pocketObj = LedgerPocket(
                    id = p.getLong("id"),
                    name = p.getString("name"),
                    type = pocketType,
                    currency = p.optString("currency", "INR"),
                    creditLimit = p.optLong("creditLimit", 0L),
                    statementClosingDay = p.optInt("statementClosingDay", 0),
                    billDueDay = p.optInt("billDueDay", 0),
                    targetGoalAmount = p.optLong("targetGoalAmount", 0L),
                    goalTargetDate = p.optLong("goalTargetDate", 0L),
                    peerContactName = p.optString("peerContactName", ""),
                    lastReconciledEpoch = p.optLong("lastReconciledEpoch", 0L),
                    isArchived = p.optBoolean("isArchived", false),
                    createdAt = p.optLong("createdAt", System.currentTimeMillis())
                )
                dao.insertPocket(pocketObj)
            }

            for (i in 0 until txArray.length()) {
                val t = txArray.getJSONObject(i)
                val nature = MovementNature.valueOf(t.optString("movementNature", "OPERATING_EXPENSE"))
                val status = SettlementStatus.valueOf(t.optString("status", "CLEARED"))
                val txObj = LedgerTransaction(
                    id = t.optLong("id", 0L),
                    timestamp = t.getLong("timestamp"),
                    amount = t.getLong("amount"),
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
                    foreignAmount = t.optLong("foreignAmount", 0L),
                    splitParentId = if (t.isNull("splitParentId")) null else t.getLong("splitParentId"),
                    idempotencyHash = t.optString("idempotencyHash", "")
                )
                dao.insertTransaction(txObj)
                importedCount++
            }

            for (i in 0 until auditArray.length()) {
                val a = auditArray.getJSONObject(i)
                dao.insertAuditEntry(
                    LedgerAuditEntry(
                        auditId = a.optLong("auditId", 0L),
                        actionType = a.getString("actionType"),
                        entityType = a.getString("entityType"),
                        recordId = a.getLong("recordId"),
                        preStateJson = a.getString("preStateJson"),
                        postStateJson = a.getString("postStateJson"),
                        reasonNote = a.optString("reasonNote", ""),
                        timestamp = a.getLong("timestamp")
                    )
                )
            }
        }
        return importedCount
    }
}
