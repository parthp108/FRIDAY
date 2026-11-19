package com.health.friday.sync.cloud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CloudSyncSecurityTest {

    private lateinit var fakeContext: Context
    private lateinit var security: AndroidCloudSyncSecurity

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return object : SharedPreferences {
                    private val map = mutableMapOf<String, String>()
                    override fun getAll(): MutableMap<String, *> = map
                    override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
                    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
                    override fun getInt(key: String?, defValue: Int): Int = 0
                    override fun getLong(key: String?, defValue: Long): Long = 0L
                    override fun getFloat(key: String?, defValue: Float): Float = 0f
                    override fun getBoolean(key: String?, defValue: Boolean): Boolean = false
                    override fun contains(key: String?): Boolean = map.containsKey(key)
                    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            if (key != null && value != null) map[key] = value
                            return this
                        }
                        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
                        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
                        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
                        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
                        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
                        override fun remove(key: String?): SharedPreferences.Editor = this
                        override fun clear(): SharedPreferences.Editor = this
                        override fun commit(): Boolean = true
                        override fun apply() {}
                    }
                    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                }
            }
        }

        security = AndroidCloudSyncSecurity(fakeContext)
    }

    @Test
    fun testEncryptAndDecryptSyncBatchRoundTrip() {
        val hlc = HlcTimestamp(wallMs = 1000L, counter = 0, nodeId = "device_test_1").encode()
        val payloadObj = buildJsonObject { put("testKey", "testValue") }

        val op = SyncOperation.newUpsert(
            deviceId = "device_test_1",
            entityType = "meal",
            entityId = "meal_123",
            hlc = hlc,
            createdAtMs = 1000L,
            payload = payloadObj
        )

        val batch = SyncBatch.create(
            deviceId = "device_test_1",
            createdAtMs = 1000L,
            operations = listOf(op)
        )

        val encrypted = security.encryptBatch(batch, passphrase = "test_secret_passphrase")
        assertNotNull(encrypted.saltBase64)
        assertNotNull(encrypted.ivBase64)
        assertNotNull(encrypted.ciphertextBase64)
        assertNotNull(encrypted.hmacBase64)

        val decrypted = security.decryptBatch(encrypted, passphrase = "test_secret_passphrase")
        assertEquals(batch.batchId, decrypted.batchId)
        assertEquals(batch.originDeviceId, decrypted.originDeviceId)
        assertEquals(1, decrypted.operations.size)
        assertEquals(op.entityId, decrypted.operations.first().entityId)
    }

    @Test
    fun testTamperedPayloadFailsAuthentication() {
        val hlc = HlcTimestamp(wallMs = 1000L, counter = 0, nodeId = "device_test_1").encode()
        val payloadObj = buildJsonObject { put("testKey", "testValue") }

        val op = SyncOperation.newUpsert(
            deviceId = "device_test_1",
            entityType = "meal",
            entityId = "meal_123",
            hlc = hlc,
            createdAtMs = 1000L,
            payload = payloadObj
        )

        val batch = SyncBatch.create(
            deviceId = "device_test_1",
            createdAtMs = 1000L,
            operations = listOf(op)
        )

        val encrypted = security.encryptBatch(batch, passphrase = "test_secret_passphrase")

        val tampered = encrypted.copy(ciphertextBase64 = encrypted.ciphertextBase64 + "X")

        assertThrows(Exception::class.java) {
            security.decryptBatch(tampered, passphrase = "test_secret_passphrase")
        }
    }
}
