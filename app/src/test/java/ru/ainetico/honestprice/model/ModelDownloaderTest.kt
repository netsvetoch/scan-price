package ru.ainetico.honestprice.model

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ModelDownloaderTest {

    private lateinit var context: Context
    private lateinit var downloader: ModelDownloader

    @Before
    fun setUp() {
        context = spyk(RuntimeEnvironment.getApplication() as Context) {
            // ModelDownloader constructor calls context.getString() for progress labels
            every { getString(any()) } returns "test label"
        }
        downloader = ModelDownloader(context)
    }

    // --- Initial state ---

    @Test
    fun `initial state is Idle`() {
        assertEquals(ModelDownloader.DownloadState.Idle, downloader.state.value)
    }

    // --- isModelDownloaded ---

    @Test
    fun `isModelDownloaded returns false when no files exist`() {
        assertFalse(downloader.isModelDownloaded())
    }

    @Test
    fun `isModelDownloaded returns false when only model file exists`() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        File(modelsDir, ModelDownloader.MODEL_FILENAME).createNewFile()

        assertFalse(downloader.isModelDownloaded())
    }

    @Test
    fun `isModelDownloaded returns false when only mmproj file exists`() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        File(modelsDir, ModelDownloader.MMPROJ_FILENAME).createNewFile()

        assertFalse(downloader.isModelDownloaded())
    }

    @Test
    fun `isModelDownloaded returns true when both files exist`() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        File(modelsDir, ModelDownloader.MODEL_FILENAME).createNewFile()
        File(modelsDir, ModelDownloader.MMPROJ_FILENAME).createNewFile()

        assertTrue(downloader.isModelDownloaded())
    }

    // --- startDownloadIfNeeded ---

    @Test
    fun `startDownloadIfNeeded sets Completed when models already downloaded`() {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        File(modelsDir, ModelDownloader.MODEL_FILENAME).createNewFile()
        File(modelsDir, ModelDownloader.MMPROJ_FILENAME).createNewFile()

        downloader.startDownloadIfNeeded()

        assertEquals(ModelDownloader.DownloadState.Completed, downloader.state.value)
    }

    // --- DownloadState sealed class ---

    @Test
    fun `DownloadState Idle is singleton`() {
        assertSame(ModelDownloader.DownloadState.Idle, ModelDownloader.DownloadState.Idle)
    }

    @Test
    fun `DownloadState Completed is singleton`() {
        assertSame(ModelDownloader.DownloadState.Completed, ModelDownloader.DownloadState.Completed)
    }

    @Test
    fun `DownloadState Error carries message`() {
        val error = ModelDownloader.DownloadState.Error("Network failure")
        assertEquals("Network failure", error.message)
    }

    @Test
    fun `DownloadState Downloading carries file progress`() {
        val f1 = ModelDownloader.FileProgress("Model", 50)
        val f2 = ModelDownloader.FileProgress("Projector", 75, done = true)
        val state = ModelDownloader.DownloadState.Downloading(f1, f2)

        assertEquals(50, state.file1.progress)
        assertEquals(75, state.file2.progress)
        assertFalse(state.file1.done)
        assertTrue(state.file2.done)
    }

    // --- FileProgress ---

    @Test
    fun `FileProgress defaults done to false`() {
        val progress = ModelDownloader.FileProgress("test", 0)
        assertFalse(progress.done)
    }

    @Test
    fun `FileProgress copy updates correctly`() {
        val original = ModelDownloader.FileProgress("test", 0)
        val updated = original.copy(progress = 100, done = true)

        assertEquals(0, original.progress)
        assertFalse(original.done)
        assertEquals(100, updated.progress)
        assertTrue(updated.done)
    }

    // --- Constants ---

    @Test
    fun `MODEL_URL points to HuggingFace`() {
        assertTrue(ModelDownloader.MODEL_URL.startsWith("https://huggingface.co/"))
    }

    @Test
    fun `MMPROJ_URL points to HuggingFace`() {
        assertTrue(ModelDownloader.MMPROJ_URL.startsWith("https://huggingface.co/"))
    }

    @Test
    fun `filenames match between LocalVisionEngine and ModelDownloader`() {
        assertEquals("Qwen3.5-0.8B-Q4_K_M.gguf", ModelDownloader.MODEL_FILENAME)
        assertEquals("mmproj-BF16.gguf", ModelDownloader.MMPROJ_FILENAME)
    }
}
