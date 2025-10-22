package com.joshiminh.cbzconverter.backend

import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito

class MainViewModelTest {

    @Test
    fun loadMihonLibrary_populatesEntriesAndClearsCaches() = runBlocking {
        val contextHelper = Mockito.mock(ContextHelper::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(contextHelper.getPreferences()).thenReturn(preferences)
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(editor)
        Mockito.`when`(editor.remove(Mockito.anyString())).thenReturn(editor)
        Mockito.doNothing().`when`(editor).apply()

        val viewModel = MainViewModel(contextHelper)

        val downloads = FakeDocumentFile.directory("downloads")
        val extensionA = downloads.addDirectory("extensionA")
        val mangaOne = extensionA.addDirectory("Manga One")
        val firstChapter = mangaOne.addFile("chapter1.cbz")
        mangaOne.addFile("chapter-notes.txt")
        val secondChapter = mangaOne.addFile("chapter2.cbz")

        val extensionB = downloads.addDirectory("extensionB")
        val mangaTwo = extensionB.addDirectory("Manga Two")
        val finalChapter = mangaTwo.addFile("finale.CBZ")

        downloads.addDirectory("extensionC").addDirectory("Manga Three")

        val cbzParentMap = viewModel.getMapField<MutableMap<Uri, String>>("cbzParentName")
        val parentNameMap = viewModel.getMapField<MutableMap<Uri, String?>>("parentNameCache")
        val parentUriMap = viewModel.getMapField<MutableMap<Uri, Uri?>>("parentUriCache")

        val staleUri = Uri.parse("content://stale")
        cbzParentMap[staleUri] = "stale"
        parentNameMap[staleUri] = "stale-parent"
        parentUriMap[staleUri] = Uri.parse("content://stale/parent")

        val progress = mutableListOf<Pair<Int, Int>>()
        val entries = viewModel.invokeLoadMihonLibrary(downloads) { completed, total ->
            progress.add(completed to total)
        }

        assertEquals(listOf("Manga One", "Manga Two"), entries.map(MihonMangaEntry::name))
        assertEquals(listOf("chapter1.cbz", "chapter2.cbz"), entries[0].files.map(MihonCbzFile::name))
        assertEquals(listOf("finale.CBZ"), entries[1].files.map(MihonCbzFile::name))

        assertEquals(listOf(1 to 2, 2 to 2), progress)

        assertFalse(cbzParentMap.containsKey(staleUri))
        assertFalse(parentNameMap.containsKey(staleUri))
        assertFalse(parentUriMap.containsKey(staleUri))

        assertEquals("Manga One", cbzParentMap[firstChapter.uri])
        assertEquals("Manga One", parentNameMap[firstChapter.uri])
        assertEquals(mangaOne.uri, parentUriMap[firstChapter.uri])

        assertEquals("Manga One", cbzParentMap[secondChapter.uri])
        assertEquals("Manga One", parentNameMap[secondChapter.uri])
        assertEquals(mangaOne.uri, parentUriMap[secondChapter.uri])

        assertEquals("Manga Two", cbzParentMap[finalChapter.uri])
        assertEquals("Manga Two", parentNameMap[finalChapter.uri])
        assertEquals(mangaTwo.uri, parentUriMap[finalChapter.uri])
    }

    private suspend fun MainViewModel.invokeLoadMihonLibrary(
        root: DocumentFile,
        onProgress: (Int, Int) -> Unit,
    ): List<MihonMangaEntry> {
        val function = this::class.declaredMemberFunctions.first { it.name == "loadMihonLibrary" }
        function.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return function.callSuspend(this, root, onProgress) as List<MihonMangaEntry>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> MainViewModel.getMapField(name: String): T {
        val field = this::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    private class FakeDocumentFile private constructor(
        parent: DocumentFile?,
        private val displayName: String?,
        private val directory: Boolean,
        private val fileUri: Uri = Uri.parse("content://fake/${UUID.randomUUID()}")
    ) : DocumentFile(parent) {

        private val children = mutableListOf<DocumentFile>()

        override fun getUri(): Uri = fileUri

        override fun getName(): String? = displayName

        override fun getType(): String? = null

        override fun isDirectory(): Boolean = directory

        override fun isFile(): Boolean = !directory

        override fun isVirtual(): Boolean = false

        override fun lastModified(): Long = 0

        override fun length(): Long = 0

        override fun canRead(): Boolean = true

        override fun canWrite(): Boolean = true

        override fun createFile(mimeType: String, displayName: String): DocumentFile? = null

        override fun createDirectory(displayName: String): DocumentFile? = null

        override fun delete(): Boolean = false

        override fun exists(): Boolean = true

        override fun listFiles(): Array<DocumentFile> = children.toTypedArray()

        override fun renameTo(displayName: String): Boolean = false

        override fun findFile(name: String): DocumentFile? = children.firstOrNull { it.name == name }

        fun addDirectory(name: String): FakeDocumentFile {
            val child = FakeDocumentFile(this, name, true)
            children.add(child)
            return child
        }

        fun addFile(name: String): FakeDocumentFile {
            val child = FakeDocumentFile(this, name, false)
            children.add(child)
            return child
        }

        companion object {
            fun directory(name: String): FakeDocumentFile = FakeDocumentFile(null, name, true)
        }
    }
}
