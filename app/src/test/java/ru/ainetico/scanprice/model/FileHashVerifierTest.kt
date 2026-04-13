package ru.ainetico.scanprice.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileHashVerifierTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sha256 returns correct hash for known content`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            FileHashVerifier.sha256(file)
        )
    }

    @Test
    fun `verify returns true for matching hash`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        assertTrue(FileHashVerifier.verify(file, "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"))
    }

    @Test
    fun `verify returns false for wrong hash`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        assertFalse(FileHashVerifier.verify(file, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
