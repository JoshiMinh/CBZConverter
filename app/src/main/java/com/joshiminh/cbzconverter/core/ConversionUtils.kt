package com.joshiminh.cbzconverter.core

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.CompressionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import java.io.File
import java.io.IOException
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

private val logger = Logger.getLogger("com.joshiminh.cbzconverter.core.ConversionUtils")
private const val COMBINED_TEMP = "combined_temp.cbz"

fun convertCbzToPdf(
    fileUri: List<Uri>,
    contextHelper: ContextHelper,
    subStepAction: (String) -> Unit = {},
    maxPages: Int = 1_000,
    batchSize: Int = 300,
    outputFileNames: List<String>,
    merge: Boolean = false,
    compress: Boolean = false,
    outputDir: DocumentFile
): List<DocumentFile> {
    if (fileUri.isEmpty()) return emptyList()
    val outputFiles = mutableListOf<DocumentFile>()
    contextHelper.getCacheDir().let { if (it.exists()) it.deleteRecursively(); it.mkdirs() }

    return if (merge) {
        mergeCbzAndProcess(contextHelper, fileUri, subStepAction, outputFileNames, outputFiles, outputDir, maxPages, batchSize, compress)
    } else {
        processIndividualCbz(fileUri, outputFileNames, contextHelper, subStepAction, outputFiles, outputDir, maxPages, batchSize, compress)
    }
}

private fun processIndividualCbz(
    fileUri: List<Uri>,
    outputFileNames: List<String>,
    contextHelper: ContextHelper,
    subStepAction: (String) -> Unit,
    outputFiles: MutableList<DocumentFile>,
    outputDir: DocumentFile,
    maxPages: Int,
    batchSize: Int,
    compress: Boolean
): List<DocumentFile> {
    fileUri.forEachIndexed { i, uri ->
        try {
            val temp = copyCbzToCache(contextHelper, subStepAction, uri)
            createPdf(temp, subStepAction, outputFileNames[i], outputDir, maxPages, outputFiles, contextHelper, batchSize, compress)
        } catch (_: IOException) {}
    }
    return outputFiles
}

private fun mergeCbzAndProcess(
    contextHelper: ContextHelper,
    fileUri: List<Uri>,
    subStepAction: (String) -> Unit,
    outputFileNames: List<String>,
    outputFiles: MutableList<DocumentFile>,
    outputDir: DocumentFile,
    maxPages: Int,
    batchSize: Int,
    compress: Boolean
): List<DocumentFile> {
    subStepAction("Merging files in Cache...")
    val combined = File(contextHelper.getCacheDir(), COMBINED_TEMP)
    ZipOutputStream(combined.outputStream()).use { out ->
        fileUri.forEachIndexed { i, uri ->
            try {
                val temp = copyCbzToCache(contextHelper, subStepAction, uri)
                ZipFile(temp).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        out.putNextEntry(ZipEntry("${i.toString().padStart(9, '0')}_${outputFileNames[i]}_${entry.name}"))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
                temp.delete()
            } catch (_: IOException) {}
        }
    }
    createPdf(combined, subStepAction, outputFileNames.first(), outputDir, maxPages, outputFiles, contextHelper, batchSize, compress)
    return outputFiles
}

private fun createPdf(
    temp: File, subStepAction: (String) -> Unit, outputName: String, outputDir: DocumentFile,
    maxPages: Int, outputFiles: MutableList<DocumentFile>, contextHelper: ContextHelper,
    batchSize: Int, compress: Boolean
) {
    try {
        ZipFile(temp).use { zip ->
            val total = zip.size()
            if (total == 0) return
            val entries = zip.entries().asSequence().sortedBy { it.name }.toList()
            if (total > maxPages) {
                val count = ceil(total.toDouble() / maxPages).toInt()
                for (i in 0 until count) {
                    val name = outputName.replace(".pdf", "_part-${i + 1}.pdf")
                    val file = contextHelper.createDocumentFile(outputDir, name, "application/pdf")
                    val start = i * maxPages
                    val end = ((i + 1) * maxPages).coerceAtMost(total)
                    val sub = entries.subList(start, end)
                    processPdfPart(sub, file, zip, contextHelper, subStepAction, batchSize, compress, i + 1, count, total, start)
                    outputFiles.add(file)
                }
            } else {
                val file = contextHelper.createDocumentFile(outputDir, outputName, "application/pdf")
                processPdfPart(entries, file, zip, contextHelper, subStepAction, batchSize, compress, 1, 1, total, 0)
                outputFiles.add(file)
            }
        }
    } finally { temp.delete() }
}

private fun processPdfPart(
    entries: List<ZipEntry>, file: DocumentFile, zip: ZipFile, contextHelper: ContextHelper,
    subStepAction: (String) -> Unit, batchSize: Int, compress: Boolean,
    part: Int, totalParts: Int, totalImages: Int, globalOffset: Int
) {
    if (entries.size > batchSize) {
        val tempFiles = mutableListOf<File>()
        val batches = ceil(entries.size.toDouble() / batchSize).toInt()
        for (i in 0 until batches) {
            val temp = File(contextHelper.getCacheDir(), "temp_batch_$i.pdf")
            val start = i * batchSize
            val end = ((i + 1) * batchSize).coerceAtMost(entries.size)
            writePdf(entries.subList(start, end), temp, zip, contextHelper, subStepAction, compress) { current ->
                "Part $part/$totalParts - Batch ${i + 1}/$batches - Image ${globalOffset + start + current}/$totalImages"
            }
            tempFiles.add(temp)
        }
        mergePdf(file, tempFiles, compress, contextHelper)
    } else {
        writePdfSaf(entries, file, zip, contextHelper, subStepAction, compress) { current ->
            "Part $part/$totalParts - Image ${globalOffset + current}/$totalImages"
        }
    }
}

private fun writePdf(
    entries: List<ZipEntry>, out: File, zip: ZipFile, contextHelper: ContextHelper,
    subStepAction: (String) -> Unit, compress: Boolean, msg: (Int) -> String
) {
    val props = if (compress) WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION) else null
    val writer = if (props != null) PdfWriter(out.absolutePath, props) else PdfWriter(out.absolutePath)
    PdfDocument(writer).use { pdfDoc ->
        Document(pdfDoc, PageSize.LETTER).use { doc ->
            doc.setMargins(15f, 10f, 15f, 10f)
            entries.forEachIndexed { i, entry ->
                subStepAction(msg(i + 1))
                ImageProcessor.extractImageAndAddToPDF(zip, entry, doc, contextHelper.getCacheDir(), subStepAction, compress)
            }
        }
    }
}

private fun writePdfSaf(
    entries: List<ZipEntry>, out: DocumentFile, zip: ZipFile, contextHelper: ContextHelper,
    subStepAction: (String) -> Unit, compress: Boolean, msg: (Int) -> String
) {
    val stream = contextHelper.openOutputStream(out.uri) ?: throw IOException("Stream error")
    val props = if (compress) WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION) else null
    val writer = if (props != null) PdfWriter(stream, props) else PdfWriter(stream)
    PdfDocument(writer).use { pdfDoc ->
        Document(pdfDoc, PageSize.LETTER).use { doc ->
            doc.setMargins(15f, 10f, 15f, 10f)
            entries.forEachIndexed { i, entry ->
                subStepAction(msg(i + 1))
                ImageProcessor.extractImageAndAddToPDF(zip, entry, doc, contextHelper.getCacheDir(), subStepAction, compress)
            }
        }
    }
}

private fun mergePdf(target: DocumentFile, files: MutableList<File>, compress: Boolean, contextHelper: ContextHelper) {
    val stream = contextHelper.openOutputStream(target.uri) ?: throw IOException("Stream error")
    val props = if (compress) WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION) else null
    val writer = if (props != null) PdfWriter(stream, props) else PdfWriter(stream)
    PdfDocument(writer).use { final ->
        val merger = PdfMerger(final)
        files.forEach { file ->
            PdfDocument(PdfReader(file)).use { doc ->
                merger.merge(doc, 1, doc.numberOfPages)
                final.flushCopiedObjects(doc)
            }
            file.delete()
        }
        merger.close()
    }
    files.clear()
}

private fun copyCbzToCache(contextHelper: ContextHelper, subStepAction: (String) -> Unit, uri: Uri): File {
    val temp = File(contextHelper.getCacheDir(), "temp.cbz")
    val input = contextHelper.openInputStream(uri) ?: throw IOException("Copy error")
    input.use { s -> temp.outputStream().use { o -> s.copyTo(o) } }
    return temp
}