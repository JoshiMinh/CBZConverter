package com.joshiminh.cbzconverter.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.kernel.geom.PageSize
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ImageProcessor {
    private val logger = Logger.getLogger(ImageProcessor::class.java.name)
    private const val DEFAULT_JPEG_QUALITY = 90
    private const val COMPRESSED_JPEG_QUALITY = 75

    fun extractImageAndAddToPDF(
        zipFile: ZipFile,
        entry: ZipEntry,
        document: Document,
        cacheDir: File,
        subStepAction: (String) -> Unit,
        compress: Boolean
    ) {
        try {
            val temp = File(cacheDir, "temp_image").apply {
                outputStream().use { out -> zipFile.getInputStream(entry).use { it.copyTo(out) } }
            }
            val quality = if (compress) COMPRESSED_JPEG_QUALITY else DEFAULT_JPEG_QUALITY
            val isWebp = entry.name.lowercase().endsWith(".webp")
            val baseFile = if (isWebp) {
                subStepAction("Converting WebP: ${entry.name}")
                convertWebpToJpeg(temp, quality)
            } else temp

            val processed = if (compress && baseFile == temp) {
                recompressImage(baseFile, quality) ?: baseFile
            } else baseFile

            val pdfImg = Image(ImageDataFactory.create(processed.absolutePath))
            document.pdfDocument.defaultPageSize = PageSize(pdfImg.imageWidth, pdfImg.imageHeight)
            document.add(pdfImg).flush()

            if (processed != baseFile) processed.delete()
            if (baseFile != temp) baseFile.delete()
            temp.delete()
        } catch (e: Exception) {
            logger.warning("Error processing ${entry.name}: ${e.message}")
        }
    }

    private fun convertWebpToJpeg(input: File, quality: Int): File {
        val bitmap = BitmapFactory.decodeFile(input.absolutePath) ?: throw Exception("WebP decode failed")
        val out = File(input.parentFile, "temp_conv.jpg")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        return out
    }

    private fun recompressImage(source: File, quality: Int): File? = try {
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: null
        val out = File(source.parentFile, "temp_comp.jpg")
        bitmap?.let { b ->
            FileOutputStream(out).use { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            b.recycle()
            out
        }
    } catch (e: Exception) { null }
}