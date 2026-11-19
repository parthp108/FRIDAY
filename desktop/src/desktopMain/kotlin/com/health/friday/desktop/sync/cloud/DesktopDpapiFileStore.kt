package com.health.friday.desktop.sync.cloud

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Windows user-scoped DPAPI wrapper. The default DPAPI scope is deliberately
 * used: another Windows account (or another PC) cannot decrypt this file.
 * Portable recovery remains the responsibility of CloudSyncKeyEnvelope.
 */
internal interface DpapiProtector {
    fun protect(plaintext: ByteArray): ByteArray
    fun unprotect(ciphertext: ByteArray): ByteArray
}

internal class WindowsDpapiProtector : DpapiProtector {
    override fun protect(plaintext: ByteArray): ByteArray {
        requireWindows()
        return Crypt32Util.cryptProtectData(
            plaintext,
            ENTROPY,
            CRYPTPROTECT_UI_FORBIDDEN,
            DESCRIPTION,
            null
        )
    }

    override fun unprotect(ciphertext: ByteArray): ByteArray {
        requireWindows()
        return Crypt32Util.cryptUnprotectData(
            ciphertext,
            ENTROPY,
            CRYPTPROTECT_UI_FORBIDDEN,
            null
        )
    }

    private fun requireWindows() {
        check(System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "FRIDAY desktop secret storage requires Windows DPAPI."
        }
    }

    private companion object {
        private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1
        private const val DESCRIPTION = "FRIDAY desktop protected secret"
        private val ENTROPY = "FRIDAY_DESKTOP_SECRET_STORE_V1".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Stores arbitrary secret bytes as DPAPI ciphertext using atomic replacement.
 * Plaintext is never written to disk. The caller owns and clears its buffers.
 */
internal class DpapiFileStore(
    private val path: Path,
    private val protector: DpapiProtector = WindowsDpapiProtector()
) {
    fun read(): ByteArray? {
        if (!Files.exists(path)) return null
        val encrypted = Files.readAllBytes(path)
        return try {
            protector.unprotect(encrypted)
        } finally {
            encrypted.fill(0)
        }
    }

    fun write(plaintext: ByteArray) {
        require(plaintext.isNotEmpty()) { "Refusing to store an empty protected secret." }
        val encrypted = protector.protect(plaintext)
        val parent = path.parent ?: error("Protected secret path has no parent directory.")
        Files.createDirectories(parent)
        val temporary = parent.resolve("${path.fileName}.tmp")
        try {
            Files.write(temporary, encrypted)
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            encrypted.fill(0)
            Files.deleteIfExists(temporary)
        }
    }

    fun delete() {
        Files.deleteIfExists(path)
    }

    companion object {
        fun appDataFile(fileName: String): Path {
            require(fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\'))
            val appData = System.getenv("APPDATA")
                ?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
            return File(File(File(appData, "FRIDAY"), "security"), fileName).toPath()
        }
    }
}
