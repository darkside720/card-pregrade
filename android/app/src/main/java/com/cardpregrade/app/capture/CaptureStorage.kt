package com.cardpregrade.app.capture

import com.cardpregrade.core.model.CaptureKind
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * App-private photo storage, organised per scan session:
 *
 * ```
 * <filesDir>/scans/<sessionId>/front-straight.jpg     accepted photos (one per capture kind)
 * <filesDir>/scans/<sessionId>/pending/<kind>-<uuid>.jpg  candidates awaiting accept/retake
 * ```
 *
 * Paths are built only from generated session ids (validated) and fixed capture-kind slugs —
 * never from user-entered card names. Nothing here is written to shared/public storage.
 */
class CaptureStorage(private val filesDir: File) {

    private val root: File get() = File(filesDir, SCANS_DIR)

    fun sessionDir(sessionId: String): File {
        require(SESSION_ID.matches(sessionId)) { "Invalid session id" }
        return File(root, sessionId)
    }

    /** New unique file for a capture candidate. */
    fun newPendingFile(sessionId: String, kind: CaptureKind): File {
        val dir = File(sessionDir(sessionId), PENDING_DIR).apply { mkdirs() }
        return File(dir, "${slug(kind)}-${UUID.randomUUID()}.jpg")
    }

    fun acceptedFile(sessionId: String, kind: CaptureKind): File = File(sessionDir(sessionId), "${slug(kind)}.jpg")

    /** Moves an accepted candidate into place, replacing an earlier photo of the same kind. */
    fun promote(pending: File, sessionId: String, kind: CaptureKind): File {
        val pendingDir = File(sessionDir(sessionId), PENDING_DIR).canonicalFile
        require(pending.canonicalFile.parentFile == pendingDir) { "File is not a pending capture of this session" }
        val target = acceptedFile(sessionId, kind)
        if (target.exists() && !target.delete()) throw IOException("Could not replace ${target.name}")
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            pending.delete()
        }
        return target
    }

    /** Path stored in the database: relative to filesDir so it survives app data moves. */
    fun relativePath(file: File): String = file.canonicalFile.relativeTo(filesDir.canonicalFile).path

    fun resolve(relativePath: String): File {
        val file = File(filesDir, relativePath).canonicalFile
        require(file.path.startsWith(root.canonicalFile.path + File.separator)) { "Path escapes scan storage" }
        return file
    }

    fun discard(file: File?) {
        file?.delete()
    }

    fun deleteAccepted(sessionId: String, kind: CaptureKind) {
        acceptedFile(sessionId, kind).delete()
    }

    fun clearPending(sessionId: String) {
        File(sessionDir(sessionId), PENDING_DIR).deleteRecursively()
    }

    fun deleteSession(sessionId: String) {
        sessionDir(sessionId).deleteRecursively()
    }

    companion object {
        const val SCANS_DIR = "scans"
        private const val PENDING_DIR = "pending"
        private val SESSION_ID = Regex("^[A-Za-z0-9-]{1,64}$")

        fun slug(kind: CaptureKind): String = when (kind) {
            CaptureKind.FRONT_STRAIGHT -> "front-straight"
            CaptureKind.BACK_STRAIGHT -> "back-straight"
            CaptureKind.FRONT_ANGLE_LEFT -> "front-left"
            CaptureKind.FRONT_ANGLE_RIGHT -> "front-right"
            else -> kind.name.lowercase().replace('_', '-')
        }
    }
}
