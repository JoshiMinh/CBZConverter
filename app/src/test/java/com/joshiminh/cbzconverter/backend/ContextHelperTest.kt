package com.joshiminh.cbzconverter.backend

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.anggrayudi.storage.file.DocumentFileCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito
import org.mockito.Mockito.mock

class ContextHelperTest {
    @Test
    fun getFileName_falls_back_when_display_name_is_placeholder() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        Mockito.`when`(context.contentResolver).thenReturn(resolver)

        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        cursor.addRow(arrayOf("document.pdf"))
        Mockito.`when`(
            resolver.query(
                any(Uri::class.java),
                any(Array<String>::class.java),
                isNull(),
                isNull(),
                isNull()
            )
        ).thenReturn(cursor)

        val uri = Uri.parse("content://provider/tree/primary:Download/MyComic.pdf")

        Mockito.mockStatic(DocumentFileCompat::class.java).use { dfMock ->
            dfMock.`when`<DocumentFileCompat?> { DocumentFileCompat.fromUri(context, uri) }.thenReturn(null)

            val helper = ContextHelper(context)
            val result = helper.getFileName(uri)
            assertEquals("MyComic.pdf", result)
        }
    }
}
