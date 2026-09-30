package com.cardpregrade.app.capture

import com.cardpregrade.core.model.CaptureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storage by lazy { CaptureStorage(tmp.root) }
    private val sessionA = "3f1c2a9e-0000-4000-8000-00000000000a"
    private val sessionB = "3f1c2a9e-0000-4000-8000-00000000000b"

    @Test
    fun `pending then promote creates the documented layout`() {
        val pending = storage.newPendingFile(sessionA, CaptureKind.FRONT_ANGLE_LEFT)
        pending.writeText("jpeg")
        assertTrue(pending.path.contains("scans/$sessionA/pending/front-left-"))

        val accepted = storage.promote(pending, sessionA, CaptureKind.FRONT_ANGLE_LEFT)
        assertEquals(File(tmp.root, "scans/$sessionA/front-left.jpg").canonicalPath, accepted.canonicalPath)
        assertTrue(accepted.exists())
        assertFalse(pending.exists())
        assertEquals("scans/$sessionA/front-left.jpg", storage.relativePath(accepted))
    }

    @Test
    fun `file names follow capture kind`() {
        assertEquals("front-straight", CaptureStorage.slug(CaptureKind.FRONT_STRAIGHT))
        assertEquals("back-straight", CaptureStorage.slug(CaptureKind.BACK_STRAIGHT))
        assertEquals("front-left", CaptureStorage.slug(CaptureKind.FRONT_ANGLE_LEFT))
        assertEquals("front-right", CaptureStorage.slug(CaptureKind.FRONT_ANGLE_RIGHT))
    }

    @Test
    fun `retake replaces the accepted photo of that kind`() {
        val first = storage.newPendingFile(sessionA, CaptureKind.FRONT_STRAIGHT).apply { writeText("one") }
        storage.promote(first, sessionA, CaptureKind.FRONT_STRAIGHT)
        val second = storage.newPendingFile(sessionA, CaptureKind.FRONT_STRAIGHT).apply { writeText("two") }
        val final = storage.promote(second, sessionA, CaptureKind.FRONT_STRAIGHT)
        assertEquals("two", final.readText())
    }

    @Test
    fun `sessions are isolated`() {
        val a = storage.newPendingFile(sessionA, CaptureKind.FRONT_STRAIGHT).apply { writeText("a") }
        val b = storage.newPendingFile(sessionB, CaptureKind.FRONT_STRAIGHT).apply { writeText("b") }
        storage.promote(a, sessionA, CaptureKind.FRONT_STRAIGHT)
        storage.promote(b, sessionB, CaptureKind.FRONT_STRAIGHT)

        storage.deleteSession(sessionA)
        assertFalse(storage.sessionDir(sessionA).exists())
        assertEquals("b", storage.acceptedFile(sessionB, CaptureKind.FRONT_STRAIGHT).readText())

        // A pending file of session B cannot be promoted into session A.
        val bPending = storage.newPendingFile(sessionB, CaptureKind.BACK_STRAIGHT).apply { writeText("x") }
        assertThrows(IllegalArgumentException::class.java) { storage.promote(bPending, sessionA, CaptureKind.BACK_STRAIGHT) }
    }

    @Test
    fun `unsafe session ids and escaping paths are rejected`() {
        listOf("../etc", "a/b", "", "Lugia 149/147", ".").forEach { bad ->
            assertThrows("should reject '$bad'", IllegalArgumentException::class.java) { storage.sessionDir(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { storage.resolve("../secrets.txt") }
        assertThrows(IllegalArgumentException::class.java) { storage.resolve("scans/../../x") }
    }

    @Test
    fun `clearPending removes only candidates`() {
        val accepted = storage.promote(
            storage.newPendingFile(sessionA, CaptureKind.FRONT_STRAIGHT).apply { writeText("keep") },
            sessionA, CaptureKind.FRONT_STRAIGHT,
        )
        val pending = storage.newPendingFile(sessionA, CaptureKind.BACK_STRAIGHT).apply { writeText("drop") }
        storage.clearPending(sessionA)
        assertTrue(accepted.exists())
        assertFalse(pending.exists())
    }
}
