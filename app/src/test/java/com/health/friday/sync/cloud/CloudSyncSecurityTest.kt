package com.health.friday.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncSecurityTest {

    private class MemoryKeyVault : LocalCloudMasterKeyVault {
        private var master: StoredCloudMasterKey? = null
        private var legacy: CharArray? = null

        override fun readMasterKey(): StoredCloudMasterKey? =
            master?.let { StoredCloudMasterKey(it.keyId, it.bytes.copyOf()) }

        override fun writeMasterKey(keyId: String, keyBytes: ByteArray) {
            master = StoredCloudMasterKey(keyId, keyBytes.copyOf())
        }

        override fun readLegacyPassphrase(): CharArray? = legacy?.copyOf()
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun androidInstallationA_toInstallationB_canDecryptSameBatch() {
        val deviceA = AndroidCloudSyncSecurity(MemoryKeyVault())
        val deviceB = AndroidCloudSyncSecurity(MemoryKeyVault())
        val recoveryPassphrase = "correct horse battery staple 2026".toCharArray()

        val envelope = deviceA.createRecoveryEnvelope(recoveryPassphrase)
        val serializedEnvelope = json.encodeToString(
            CloudSyncKeyEnvelope.serializer(),
            envelope
        )
        val transferredEnvelope = json.decodeFromString(
            CloudSyncKeyEnvelope.serializer(),
            serializedEnvelope
        )

        deviceB.restoreFromEnvelope(
            transferredEnvelope,
            recoveryPassphrase.copyOf()
        )

        val originalBatch = sampleBatch()
        val encrypted = deviceA.encryptBatch(originalBatch)
        val decrypted = deviceB.decryptBatch(encrypted)

        assertEquals(originalBatch.batchId, decrypted.batchId)
        assertEquals(originalBatch.operations, decrypted.operations)
        assertEquals(envelope.keyId, encrypted.keyId)
    }

    @Test
    fun wrongRecoveryPassphraseIsRejected() {
        val deviceA = AndroidCloudSyncSecurity(MemoryKeyVault())
        val deviceB = AndroidCloudSyncSecurity(MemoryKeyVault())

        val envelope = deviceA.createRecoveryEnvelope(
            "correct horse battery staple".toCharArray()
        )

        assertThrows(Exception::class.java) {
            deviceB.restoreFromEnvelope(
                envelope,
                "incorrect recovery phrase".toCharArray()
            )
        }
        assertTrue(!deviceB.hasLocalMasterKey())
    }

    @Test
    fun modifiedRecoveryEnvelopeIsRejected() {
        val deviceA = AndroidCloudSyncSecurity(MemoryKeyVault())
        val deviceB = AndroidCloudSyncSecurity(MemoryKeyVault())
        val passphrase = "correct horse battery staple".toCharArray()
        val envelope = deviceA.createRecoveryEnvelope(passphrase)

        val changedFirst = if (envelope.wrappedMasterKeyBase64.first() == 'A') 'B' else 'A'
        val tampered = envelope.copy(
            wrappedMasterKeyBase64 =
                changedFirst + envelope.wrappedMasterKeyBase64.drop(1)
        )

        assertThrows(Exception::class.java) {
            deviceB.restoreFromEnvelope(tampered, passphrase.copyOf())
        }
        assertTrue(!deviceB.hasLocalMasterKey())
    }

    @Test
    fun modifiedEncryptedBatchIsRejected() {
        val security = AndroidCloudSyncSecurity(MemoryKeyVault())
        security.createRecoveryEnvelope(
            "correct horse battery staple".toCharArray()
        )

        val encrypted = security.encryptBatch(sampleBatch())
        val changedFirst = if (encrypted.ciphertextBase64.first() == 'A') 'B' else 'A'
        val tampered = encrypted.copy(
            ciphertextBase64 = changedFirst + encrypted.ciphertextBase64.drop(1)
        )

        assertThrows(Exception::class.java) {
            security.decryptBatch(tampered)
        }
    }

    @Test
    fun sameLocalVaultRetainsMasterKeyAcrossSecurityObjects() {
        val vault = MemoryKeyVault()
        val firstInstance = AndroidCloudSyncSecurity(vault)
        val envelope = firstInstance.createRecoveryEnvelope(
            "correct horse battery staple".toCharArray()
        )
        val batch = sampleBatch()
        val encrypted = firstInstance.encryptBatch(batch)

        val secondInstance = AndroidCloudSyncSecurity(vault)
        val decrypted = secondInstance.decryptBatch(encrypted)

        assertEquals(envelope.keyId, encrypted.keyId)
        assertEquals(batch.batchId, decrypted.batchId)
    }

    @Test
    fun newBatchPayloadUsesV2FormatWithoutSeparateHmac() {
        val security = AndroidCloudSyncSecurity(MemoryKeyVault())
        security.createRecoveryEnvelope(
            "correct horse battery staple".toCharArray()
        )

        val encrypted = security.encryptBatch(sampleBatch())

        assertEquals("FRIDAY_E2EE_V2", encrypted.magicHeader)
        assertTrue(encrypted.keyId!!.isNotBlank())
        assertTrue(encrypted.hmacBase64 == null)
        assertTrue(encrypted.saltBase64 == null)
    }

    private fun sampleBatch(): SyncBatch {
        val deviceId = "device_test_1"
        val hlc = HlcTimestamp(
            wallMs = 1_000L,
            counter = 0,
            nodeId = deviceId
        ).encode()

        val op = SyncOperation.newUpsert(
            deviceId = deviceId,
            entityType = "meal",
            entityId = "meal_123",
            hlc = hlc,
            createdAtMs = 1_000L,
            payload = buildJsonObject {
                put("testKey", "testValue")
            }
        )

        return SyncBatch.create(
            deviceId = deviceId,
            createdAtMs = 1_000L,
            operations = listOf(op)
        )
    }
}
