package com.joshiminh.cbzconverter.backend

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files

class MainViewModelTest {

    @Test
    fun getPdfFileNames_falls_back_to_parent_when_placeholder() {
        val helper = mock(ContextHelper::class.java)
        val ctx = mock(Context::class.java)
        Mockito.`when`(helper.getContext()).thenReturn(ctx)

        val uri1 = Uri.parse("content://provider/ParentOne/file.cbz")
        val uri2 = Uri.parse("content://provider/ParentTwo/file.cbz")

        Mockito.`when`(helper.getFileName(uri1)).thenReturn("Unknown")
        Mockito.`when`(helper.getFileName(uri2)).thenReturn("Unknown")

        val viewModel = MainViewModel(helper)

        Mockito.mockStatic(DocumentFile::class.java).use { dfMock ->
            dfMock.`when`<DocumentFile?> {
                DocumentFile.fromSingleUri(any(Context::class.java), any(Uri::class.java))
            }.thenReturn(null)

            val method = viewModel.javaClass.getDeclaredMethod(
                "getPdfFileNames",
                List::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            val names = method.invoke(viewModel, listOf(uri1, uri2), false) as List<String>
            assertEquals(listOf("ParentOne.pdf", "ParentTwo.pdf"), names)
        }
    }

    @Test
    fun resolveFileNameConflicts_handles_duplicate_parent_fallbacks() {
        val helper = mock(ContextHelper::class.java)
        val ctx = mock(Context::class.java)
        Mockito.`when`(helper.getContext()).thenReturn(ctx)

        val uri1 = Uri.parse("content://provider/Parent/file1.cbz")
        val uri2 = Uri.parse("content://provider/Parent/file2.cbz")

        Mockito.`when`(helper.getFileName(uri1)).thenReturn("Unknown")
        Mockito.`when`(helper.getFileName(uri2)).thenReturn("Unknown")

        val viewModel = MainViewModel(helper)

        Mockito.mockStatic(DocumentFile::class.java).use { dfMock ->
            dfMock.`when`<DocumentFile?> {
                DocumentFile.fromSingleUri(any(Context::class.java), any(Uri::class.java))
            }.thenReturn(null)

            val getNames = viewModel.javaClass.getDeclaredMethod(
                "getPdfFileNames",
                List::class.java,
                Boolean::class.javaPrimitiveType
            )
            getNames.isAccessible = true
            val baseNames = getNames.invoke(viewModel, listOf(uri1, uri2), false) as List<String>

            val resolve = viewModel.javaClass.getDeclaredMethod(
                "resolveFileNameConflicts",
                List::class.java,
                File::class.java
            )
            resolve.isAccessible = true
            val outputDir = Files.createTempDirectory("mvmt").toFile()
            val resolved = resolve.invoke(viewModel, baseNames, outputDir) as List<String>
            assertEquals(listOf("Parent.pdf", "Parent 1.pdf"), resolved)
        }
    }

    @Test
    fun getPdfFileNames_avoids_document_placeholder_in_default_mode() {
        val helper = mock(ContextHelper::class.java)
        val ctx = mock(Context::class.java)
        Mockito.`when`(helper.getContext()).thenReturn(ctx)

        val uri = Uri.parse("content://provider/document/file.cbz")
        Mockito.`when`(helper.getFileName(uri)).thenReturn("document.pdf")

        val viewModel = MainViewModel(helper)

        Mockito.mockStatic(DocumentFile::class.java).use { dfMock ->
            val parent = mock(DocumentFile::class.java)
            Mockito.`when`(parent.name).thenReturn("document")
            val file = mock(DocumentFile::class.java)
            Mockito.`when`(file.parentFile).thenReturn(parent)
            dfMock.`when`<DocumentFile?> { DocumentFile.fromSingleUri(any(Context::class.java), any(Uri::class.java)) }
                .thenReturn(file)

            val method = viewModel.javaClass.getDeclaredMethod(
                "getPdfFileNames",
                List::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            val names = method.invoke(viewModel, listOf(uri), false) as List<String>
            assertEquals(listOf("Unknown.pdf"), names)
        }
    }

    @Test
    fun getPdfFileNames_uses_file_name_when_parent_is_placeholder_in_mihon_mode() {
        val helper = mock(ContextHelper::class.java)
        val ctx = mock(Context::class.java)
        Mockito.`when`(helper.getContext()).thenReturn(ctx)

        val uri = Uri.parse("content://provider/document/MyManga.cbz")
        Mockito.`when`(helper.getFileName(uri)).thenReturn("MyManga.cbz")

        val viewModel = MainViewModel(helper)

        Mockito.mockStatic(DocumentFile::class.java).use { dfMock ->
            val parent = mock(DocumentFile::class.java)
            Mockito.`when`(parent.name).thenReturn("document")
            val file = mock(DocumentFile::class.java)
            Mockito.`when`(file.parentFile).thenReturn(parent)
            dfMock.`when`<DocumentFile?> { DocumentFile.fromSingleUri(any(Context::class.java), any(Uri::class.java)) }
                .thenReturn(file)

            val method = viewModel.javaClass.getDeclaredMethod(
                "getPdfFileNames",
                List::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            val names = method.invoke(viewModel, listOf(uri), true) as List<String>
            assertEquals(listOf("MyManga.pdf"), names)
        }
    }
}

