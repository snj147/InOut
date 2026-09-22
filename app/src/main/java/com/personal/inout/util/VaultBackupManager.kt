package com.personal.inout.util

import android.content.Context
import android.net.Uri
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
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

    suspend fun exportEncryptedBackup(
        context: Context,
        db: AppDatabase,
        destinationUri: Uri,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pockets: List<VaultPocket> = db.stateFlowDao().observeAllActivePockets().first()
            val flows: List<FlowRecord> = db.stateFlowDao().observeAllFlowRecords().first()

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())

                val pocketsArray = JSONArray()
                for (p in pockets) {
                    pocketsArray.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("pocketType", p.pocketType.name)
                        put("subType", p.subType)
                        put("creditLimit", p.creditLimit)
                        put("targetAmount", p.targetAmount)
                        put("targetDateEpoch", p.targetDateEpoch)
                        put("isArchived", p.isArchived)
                    })
                }
                put("pockets", pocketsArray)

                val flowsArray = JSONArray()
                for (f in flows) {
                    flowsArray.put(JSONObject().apply {
                        put("id", f.id)
                        put("nature", f.nature.name)
                        put("sourcePocketId", f.sourcePocketId ?: JSONObject.NULL)
                        put("targetPocketId", f.targetPocketId ?: JSONObject.NULL)
                        put("amount", f.amount)
                        put("category", f.category)
                        put("note", f.note)
                        put("timestamp", f.timestamp)
                        put("isRecurring", f.isRecurring)
                        put("frequency", f.frequency)
                        put("recurringCadence", f.recurringCadence)
                        put("isPaused", f.isPaused)
                    })
                }
                put("flows", flowsArray)
            }

            val plaintext = rootJson.toString().toByteArray(Charsets.UTF_8)
            val salt = ByteArray(SALT_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val ciphertext = cipher.doFinal(plaintext)

            context.contentResolver.openOutputStream(destinationUri)?.use { outStream: OutputStream ->
                outStream.write(salt)
                outStream.write(iv)
                outStream.write(ciphertext)
                outStream.flush()
            } ?: error("Failed to open output stream")
        }
    }

    suspend fun restoreEncryptedBackup(
        context: Context,
        db: AppDatabase,
        sourceUri: Uri,
        passphrase: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val allBytes = context.contentResolver.openInputStream(sourceUri)?.use { inStream: InputStream ->
                inStream.readBytes()
            } ?: error("Failed to open backup file")

            if (allBytes.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
                error("Corrupted backup file (too small)")
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
            val flowsArray = rootJson.getJSONArray("flows")

            val existingFlows = db.stateFlowDao().observeAllFlowRecords().first()
            for (f in existingFlows) {
                db.stateFlowDao().deleteFlowRecordById(f.id)
            }

            for (i in 0 until pocketsArray.length()) {
                val p = pocketsArray.getJSONObject(i)
                val pocketObj = VaultPocket(
                    id = p.getLong("id"),
                    name = p.getString("name"),
                    pocketType = PocketType.valueOf(p.getString("pocketType")),
                    subType = p.optString("subType", "LIQUID"),
                    creditLimit = p.optDouble("creditLimit", 0.0),
                    targetAmount = p.optDouble("targetAmount", 0.0),
                    targetDateEpoch = p.optLong("targetDateEpoch", 0L),
                    isArchived = p.optBoolean("isArchived", false)
                )
                db.stateFlowDao().insertPocket(pocketObj)
            }

            var importedCount = 0
            for (i in 0 until flowsArray.length()) {
                val f = flowsArray.getJSONObject(i)
                val flowObj = FlowRecord(
                    id = f.optLong("id", 0L),
                    sourcePocketId = if (f.isNull("sourcePocketId")) null else f.getLong("sourcePocketId"),
                    targetPocketId = if (f.isNull("targetPocketId")) null else f.getLong("targetPocketId"),
                    amount = f.getDouble("amount"),
                    category = f.getString("category"),
                    note = f.getString("note"),
                    timestamp = f.getLong("timestamp"),
                    isRecurring = f.optBoolean("isRecurring", false),
                    frequency = f.optString("frequency", "NONE"),
                    recurringCadence = f.optString("recurringCadence", "NONE"),
                    isPaused = f.optBoolean("isPaused", false)
                )
                db.stateFlowDao().insertFlowRecord(flowObj)
                importedCount++
            }

            importedCount
        }
    }
}
