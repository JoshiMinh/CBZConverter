package com.joshiminh.cbzconverter.backend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.CompressionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.sequences.asSequence

private val logger = Logger.getLogger("com.joshiminh.cbzconverter.ConversionFunctions")
private const val COMBINED_TEMP_CBZ_FILE = "combined_temp.cbz"
private const val DEFAULT_JPEG_QUALITY = 90
private const val COMPRESSED_JPEG_QUALITY = 75

private fun buildWriterProperties(compressOutputPdf: Boolean): WriterProperties? =
    if (compressOutputPdf) {
        WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
    } else {
        null
    }

private fun createPdfWriter(outputFile: File, compressOutputPdf: Boolean): PdfWriter {
    val writerProperties = buildWriterProperties(compressOutputPdf)
    return if (writerProperties != null) {
        PdfWriter(outputFile.absolutePath, writerProperties)
    } else {
        PdfWriter(outputFile.absolutePath)
    }
}

private fun createPdfWriter(outputStream: OutputStream, compressOutputPdf: Boolean): PdfWriter {
    val writerProperties = buildWriterProperties(compressOutputPdf)
    return if (writerProperties != null) {
        PdfWriter(outputStream, writerProperties)
    } else {
        PdfWriter(outputStream)
    }
}

/**
 * Entry point for converting one or multiple CBZ files to PDF(s).
 */
fun convertCbzToPdf(
    fileUri: List<Uri>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit = { status -> logger.info(status) },
    maxNumberOfPages: Int = 1_000,
    batchSize: Int = 300,
    outputFileNames: List<String> = List(fileUri.size) { index -> "output_$index.pdf" },
    overrideMergeFiles: Boolean = false,
    compressOutputPdf: Boolean = false,
    outputDirectory: DocumentFile
): List<DocumentFile> {
    if (fileUri.isEmpty()) return mutableListOf()

    val outputFiles = mutableListOf<DocumentFile>()
    contextHelper.getCacheDir().let { cacheDir ->
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        cacheDir.mkdirs()
    }

    return if (overrideMergeFiles) {
        mergeFilesAndCreatePdf(
            contextHelper = contextHelper,
            fileUri = fileUri,
            subStepStatusAction = subStepStatusAction,
            outputFileNames = outputFileNames,
            outputFiles = outputFiles,
            outputDirectory = outputDirectory,
            maxNumberOfPages = maxNumberOfPages,
            batchSize = batchSize,
            compressOutputPdf = compressOutputPdf
        )
    } else {
        applyEachFileAndCreatePdf(
            fileUri = fileUri,
            outputFileNames = outputFileNames,
            contextHelper = contextHelper,
            subStepStatusAction = subStepStatusAction,
            outputFiles = outputFiles,
            outputDirectory = outputDirectory,
            maxNumberOfPages = maxNumberOfPages,
            batchSize = batchSize,
            compressOutputPdf = compressOutputPdf
        )
}
}

/**
 * Process each CBZ independently and produce one PDF per file.
 */
private fun applyEachFileAndCreatePdf(
    fileUri: List<Uri>,
    outputFileNames: List<String>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    outputFiles: MutableList<DocumentFile>,
    outputDirectory: DocumentFile,
    maxNumberOfPages: Int,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<DocumentFile> {
    fileUri.forEachIndexed { index, uri ->
        val outputFileName = outputFileNames[index]

        try {
            val tempFile = copyCbzToCacheAndCloseInputStream(
                contextHelper = contextHelper,
                subStepStatusAction = subStepStatusAction,
                uri = uri
            )

            createPdfEitherSingleOrMultiple(
                tempFile = tempFile,
                subStepStatusAction = subStepStatusAction,
                outputFileName = outputFileName,
                outputDirectory = outputDirectory,
                maxNumberOfPages = maxNumberOfPages,
                outputFiles = outputFiles,
                contextHelper = contextHelper,
                batchSize = batchSize,
                compressOutputPdf = compressOutputPdf
            )
        } catch (_: IOException) {
            // Skip file on IO error, as in original behavior
            return@forEachIndexed
        }
    }
    return outputFiles
}

/**
 * Merge all selected CBZs into one temporary CBZ, then export to PDF(s).
 */
private fun mergeFilesAndCreatePdf(
    contextHelper: ContextHelper,
    fileUri: List<Uri>,
    subStepStatusAction: (String) -> Unit,
    outputFileNames: List<String>,
    outputFiles: MutableList<DocumentFile>,
    outputDirectory: DocumentFile,
    maxNumberOfPages: Int,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<DocumentFile> {
    subStepStatusAction("Creating $COMBINED_TEMP_CBZ_FILE in Cache")
    val combinedTempFile = File(contextHelper.getCacheDir(), COMBINED_TEMP_CBZ_FILE)

    ZipOutputStream(combinedTempFile.outputStream()).use { zipOutputStream ->
        fileUri.forEachIndexed { index, uri ->
            addEntriesToZip(
                zipOutputStream = zipOutputStream,
                fileName = outputFileNames[index],
                index = index,
                subStepStatusAction = subStepStatusAction,
                contextHelper = contextHelper,
                uri = uri
            )
        }
    }

    val outputFileName = outputFileNames.first()

    createPdfEitherSingleOrMultiple(
        tempFile = combinedTempFile,
        subStepStatusAction = subStepStatusAction,
        outputFileName = outputFileName,
        outputDirectory = outputDirectory,
        maxNumberOfPages = maxNumberOfPages,
        outputFiles = outputFiles,
        contextHelper = contextHelper,
        batchSize = batchSize,
        compressOutputPdf = compressOutputPdf
    )

    return outputFiles
}

/**
 * Order ZIP entries either by natural ZIP offset (original order) or by filename.
 */
private fun orderZipEntriesByName(zipFile: ZipFile): List<ZipEntry> =
    zipFile.entries()
        .asSequence()
        .sortedBy { it.name }
        .toList()

/**
 * Append all entries from a CBZ (ZIP) into an output ZIP stream with stable ordering.
 */
private fun addEntriesToZip(
    zipOutputStream: ZipOutputStream,
    fileName: String,
    index: Int,
    subStepStatusAction: (String) -> Unit,
    contextHelper: ContextHelper,
    uri: Uri
) {
    val tempFile = try {
        copyCbzToCacheAndCloseInputStream(
            contextHelper = contextHelper,
            subStepStatusAction = subStepStatusAction,
            uri = uri
        )
    } catch (_: IOException) {
        return
    }

    try {
        ZipFile(tempFile).use { zipFile ->
            subStepStatusAction("Adding ${zipFile.size()} entries from $fileName")

            zipFile.entries().asSequence().forEach { zipEntry ->
                try {
                    val formattedIndex = index.toString().padStart(9, '0')
                    val currentFileUniqueName = "${formattedIndex}_${fileName}_${zipEntry.name}"

                    zipOutputStream.putNextEntry(ZipEntry(currentFileUniqueName))
                    zipFile.getInputStream(zipEntry).use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                } catch (e: Exception) {
                    subStepStatusAction("Error processing file ${zipEntry.name}")
                    logger.warning("Exception message: ${e.message}")
                    logger.warning("Exception stacktrace: ${e.stackTrace.contentToString()}")
                }
            }
        }
    } finally {
        tempFile.delete()
    }
}

/**
 * Decide whether to create one PDF or split into multiple, then export.
 */
fun createPdfEitherSingleOrMultiple(
    tempFile: File,
    subStepStatusAction: (String) -> Unit,
    outputFileName: String,
    outputDirectory: DocumentFile,
    maxNumberOfPages: Int,
    outputFiles: MutableList<DocumentFile>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<DocumentFile> {
    val result = try {
        ZipFile(tempFile).use { zipFile ->
            val totalNumberOfImages = zipFile.size()

            if (totalNumberOfImages == 0) {
                subStepStatusAction("No images found in CBZ file: $outputFileName")
                return@use mutableListOf<DocumentFile>()
            }

            val zipFileEntriesList = orderZipEntriesByName(zipFile)

            if (!outputDirectory.exists() || !outputDirectory.isDirectory) {
                throw IOException("Output directory unavailable")
            }

            if (totalNumberOfImages > maxNumberOfPages) {
                createMultiplePdfFromCbz(
                    totalNumberOfImages = totalNumberOfImages,
                    maxNumberOfPages = maxNumberOfPages,
                    zipFileEntriesList = zipFileEntriesList,
                    outputFileName = outputFileName,
                    outputDirectory = outputDirectory,
                    subStepStatusAction = subStepStatusAction,
                    zipFile = zipFile,
                    outputFiles = outputFiles,
                    contextHelper = contextHelper,
                    batchSize = batchSize,
                    compressOutputPdf = compressOutputPdf
                )
            } else {
                createSinglePdfFromCbz(
                    totalNumberOfImages = totalNumberOfImages,
                    zipFileEntriesList = zipFileEntriesList,
                    outputFileName = outputFileName,
                    outputDirectory = outputDirectory,
                    subStepStatusAction = subStepStatusAction,
                    zipFile = zipFile,
                    outputFiles = outputFiles,
                    contextHelper = contextHelper,
                    batchSize = batchSize,
                    compressOutputPdf = compressOutputPdf
                )
            }

            outputFiles
        }
    } finally {
        tempFile.delete()
    }

    return result
}

/**
 * Copy CBZ pointed by a Uri into cache as a temp file.
 */
@Throws(IOException::class)
private fun copyCbzToCacheAndCloseInputStream(
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    uri: Uri
): File {
    val tempFile = File(contextHelper.getCacheDir(), "temp.cbz")

    val inputStream = contextHelper.openInputStream(uri) ?: run {
        subStepStatusAction("Could not copy CBZ file to cache: ${uri.path}")
        throw IOException("Could not copy CBZ file to cache: ${uri.path}")
    }

    inputStream.use { stream ->
        tempFile.outputStream().use { outputStream ->
            stream.copyTo(outputStream)
        }
    }

    return tempFile
}

/**
 * Calculate an inclusive-exclusive range [start, end) for batching.
 */
internal fun calculateRange(index: Int, pageSize: Int, totalItems: Int): Pair<Int, Int> {
    val startIndex = index * pageSize
    val nextPossibleEndIndex = (index + 1) * pageSize
    val endIndex = if (nextPossibleEndIndex > totalItems) totalItems else nextPossibleEndIndex
    return Pair(startIndex, endIndex)
}

/**
 * Build a PDF from a list of ZIP image entries.
 */
private fun createPdfFromImageList(
    imagesToProcess: List<ZipEntry>,
    outputFile: DocumentFile,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    messageFormat: (Int) -> String,
    compressOutputPdf: Boolean
) {
    if (imagesToProcess.isEmpty()) return

    val outputStream = contextHelper.openOutputStream(outputFile.uri)
        ?: throw IOException("Unable to open output stream for ${outputFile.uri}")

    outputStream.use { stream ->
        createPdfWriter(stream, compressOutputPdf).use { writer ->
            PdfDocument(writer).use { pdfDoc ->
                Document(pdfDoc, PageSize.LETTER, true).use { document ->
                    setMarginForDocument(document)

                    imagesToProcess.forEachIndexed { index, imageFile ->
                        subStepStatusAction(messageFormat(index + 1))
                        extractImageAndAddToPDFDocument(
                            zipFile = zipFile,
                            zipFileEntry = imageFile,
                            document = document,
                            contextHelper = contextHelper,
                            subStepStatusAction = subStepStatusAction,
                            compressOutputPdf = compressOutputPdf
                        )
                    }
                }
            }
        }
    }
}

private fun createPdfFromImageListToFile(
    imagesToProcess: List<ZipEntry>,
    outputFile: File,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    messageFormat: (Int) -> String,
    compressOutputPdf: Boolean
) {
    if (imagesToProcess.isEmpty()) return

    createPdfWriter(outputFile, compressOutputPdf).use { writer ->
        PdfDocument(writer).use { pdfDoc ->
            Document(pdfDoc, PageSize.LETTER, true).use { document ->
                setMarginForDocument(document)

                imagesToProcess.forEachIndexed { index, imageFile ->
                    subStepStatusAction(messageFormat(index + 1))
                    extractImageAndAddToPDFDocument(
                        zipFile = zipFile,
                        zipFileEntry = imageFile,
                        document = document,
                        contextHelper = contextHelper,
                        subStepStatusAction = subStepStatusAction,
                        compressOutputPdf = compressOutputPdf
                    )
                }
            }
        }
    }
}

/**
 * Create multiple PDFs if total images exceed maxNumberOfPages.
 */
private fun createMultiplePdfFromCbz(
    totalNumberOfImages: Int,
    maxNumberOfPages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: DocumentFile,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<DocumentFile>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
) {
    val amountOfFilesToExport = ceil(totalNumberOfImages.toDouble() / maxNumberOfPages).toInt()

    for (index in 0 until amountOfFilesToExport) {
        val newOutputFileName = outputFileName.replace(".pdf", "_part-${index + 1}.pdf")
        val outputFile = contextHelper.createDocumentFile(
            outputDirectory,
            newOutputFileName,
            "application/pdf"
        )

        val (startIndex, endIndex) = calculateRange(index, maxNumberOfPages, totalNumberOfImages)
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)
        val imagesInThisPart = imagesToProcess.size

        if (imagesInThisPart > batchSize) {
            // Use memory batch processing for this part
            try {
                val processedFile = createPdfWithBatchProcessing(
                    totalNumberOfImages = imagesInThisPart,
                    zipFileEntriesList = imagesToProcess,
                    subStepStatusAction = { message ->
                        subStepStatusAction("Processing part ${index + 1} of $amountOfFilesToExport - $message")
                    },
                    zipFile = zipFile,
                    contextHelper = contextHelper,
                    batchSize = batchSize,
                    compressOutputPdf = compressOutputPdf,
                    targetDocument = outputFile
                )
                outputFiles.add(processedFile)
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            }
        } else {
            // Process normally without memory batch processing
            try {
                createPdfFromImageList(
                    imagesToProcess = imagesToProcess,
                    outputFile = outputFile,
                    zipFile = zipFile,
                    contextHelper = contextHelper,
                    subStepStatusAction = subStepStatusAction,
                    messageFormat = { currentImageIndex ->
                        "Processing part ${index + 1} of $amountOfFilesToExport - Processing image file ${startIndex + currentImageIndex} of $totalNumberOfImages"
                    },
                    compressOutputPdf = compressOutputPdf
                )
                outputFiles.add(outputFile)
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            }
        }
    }
}

/**
 * Create a single PDF using batch processing when needed to respect memory constraints.
 */
private fun createPdfWithBatchProcessing(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean,
    targetDocument: DocumentFile
): DocumentFile {
    // Used to circumvent the 512 MB max RAM usage on Android apps:
    // split the total files into memory batches and then merge them into a single PDF,
    // keeping at most 'batchSize' pages in memory at a time.
    val tempOutputFiles = mutableListOf<File>()
    val amountOfMemoryBatches = ceil(totalNumberOfImages.toDouble() / batchSize).toInt()

    for (memoryBatchIndex in 0 until amountOfMemoryBatches) {
        val tempMemoryBatchFileName = "temp_memory_batch_${memoryBatchIndex + 1}.pdf"
        val tempBatchFile = File(contextHelper.getCacheDir(), tempMemoryBatchFileName)

        val (startIndex, endIndex) = calculateRange(memoryBatchIndex, batchSize, totalNumberOfImages)
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)

        createPdfFromImageListToFile(
            imagesToProcess = imagesToProcess,
            outputFile = tempBatchFile,
            zipFile = zipFile,
            contextHelper = contextHelper,
            subStepStatusAction = subStepStatusAction,
            messageFormat = { currentImageIndex ->
                "Processing memory batch ${memoryBatchIndex + 1} of $amountOfMemoryBatches - Processing image file ${startIndex + currentImageIndex} of $totalNumberOfImages"
            },
            compressOutputPdf = compressOutputPdf
        )

        tempOutputFiles.add(tempBatchFile)
    }

    // Merge all memory batch files into the final output file
    mergePdfFiles(
        targetDocument = targetDocument,
        outputFiles = tempOutputFiles,
        compressOutputPdf = compressOutputPdf,
        contextHelper = contextHelper
    )

    // Return the final merged file
    return targetDocument
}

/**
 * Create a single PDF when within memory constraints.
 */
private fun createSinglePdfFromCbz(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: DocumentFile,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<DocumentFile>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
) {
    if (totalNumberOfImages > batchSize) {
        val target = contextHelper.createDocumentFile(
            outputDirectory,
            outputFileName,
            "application/pdf"
        )
        try {
            val processedFile = createPdfWithBatchProcessing(
                totalNumberOfImages = totalNumberOfImages,
                zipFileEntriesList = zipFileEntriesList,
                subStepStatusAction = subStepStatusAction,
                zipFile = zipFile,
                contextHelper = contextHelper,
                batchSize = batchSize,
                compressOutputPdf = compressOutputPdf,
                targetDocument = target
            )
            outputFiles.add(processedFile)
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        return
    }

    val outputFile = contextHelper.createDocumentFile(
        outputDirectory,
        outputFileName,
        "application/pdf"
    )

    try {
        createPdfFromImageList(
            imagesToProcess = zipFileEntriesList,
            outputFile = outputFile,
            zipFile = zipFile,
            contextHelper = contextHelper,
            subStepStatusAction = subStepStatusAction,
            messageFormat = { currentImageIndex ->
                "Processing image file $currentImageIndex of $totalNumberOfImages"
            },
            compressOutputPdf = compressOutputPdf
        )

        outputFiles.add(outputFile)
    } catch (t: Throwable) {
        outputFile.delete()
        throw t
    }
}

/**
 * Merge all PDFs in 'outputFiles' into the SAF target document.
 * Also clears the temporary file list once the merge completes.
 */
private fun mergePdfFiles(
    targetDocument: DocumentFile,
    outputFiles: MutableList<File>,
    compressOutputPdf: Boolean,
    contextHelper: ContextHelper
) {
    val outputStream = contextHelper.openOutputStream(targetDocument.uri)
        ?: throw IOException("Unable to open output stream for ${targetDocument.uri}")

    outputStream.use { stream ->
        createPdfWriter(stream, compressOutputPdf).use { writer ->
            PdfDocument(writer).use { finalPdfDocument ->
                val pdfMerger = PdfMerger(finalPdfDocument)

                try {
                    outputFiles.forEach { file ->
                        PdfDocument(PdfReader(file)).use { pdfDocument ->
                            pdfMerger.merge(pdfDocument, 1, pdfDocument.numberOfPages)
                            finalPdfDocument.flushCopiedObjects(pdfDocument)
                        }
                        file.delete()
                    }
                } finally {
                    pdfMerger.close()
                }
            }
        }
    }

    outputFiles.clear()
}

/**
 * Set margins for the generated PDF document.
 */
private fun setMarginForDocument(document: Document) {
    // Overriding margins, due to lots of empty space at the bottom of longer pages.
    // Top, Right, Bottom, Left
    document.setMargins(15f, 10f, 15f, 10f)
}

/**
 * Convert a WebP image file to JPEG.
 */
private fun convertWebpToJpeg(inputFile: File, quality: Int): File {
    return try {
        // Decode WebP using Android's BitmapFactory
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
            ?: throw IOException("Failed to decode WebP image")

        // Create output JPEG file
        val outputFile = File(inputFile.parentFile, "temp_converted.jpg")
        val outputStream = FileOutputStream(outputFile)

        // Compress to JPEG using the requested quality
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        outputStream.close()
        bitmap.recycle()

        outputFile
    } catch (e: Exception) {
        logger.warning("Failed to convert WebP to JPEG: ${e.message}")
        throw e
    }
}

private fun recompressImageToJpeg(source: File, quality: Int): File? {
    return try {
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options) ?: return null

        val compressedFile = File(source.parentFile, "temp_compressed.jpg")
        FileOutputStream(compressedFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        bitmap.recycle()

        compressedFile
    } catch (t: Throwable) {
        logger.warning("Failed to recompress image ${source.name}: ${t.message}")
        null
    }
}

/**
 * Extract an image from the ZIP and add it to the PDF document,
 * converting WebP to JPEG when necessary, and matching the page size to the image.
 */
private fun extractImageAndAddToPDFDocument(
    zipFile: ZipFile,
    zipFileEntry: ZipEntry,
    document: Document,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    compressOutputPdf: Boolean
) {
    try {
        // Create temp file to avoid large memory usage
        val tempFile = File(contextHelper.getCacheDir(), "temp_image")
        tempFile.outputStream().use { tempFileOutputStream ->
            zipFile.getInputStream(zipFileEntry).use { imageInputStream ->
                imageInputStream.copyTo(tempFileOutputStream)
            }
        }

        // Check if it's a WebP file and convert if necessary
        val quality = if (compressOutputPdf) COMPRESSED_JPEG_QUALITY else DEFAULT_JPEG_QUALITY
        val baseImageFile =
            if (zipFileEntry.name.lowercase().endsWith(".webp")) {
                subStepStatusAction("Converting WebP image: ${zipFileEntry.name}")
                logger.info("Converting WebP image: ${zipFileEntry.name}")
                convertWebpToJpeg(tempFile, quality)
            } else {
                tempFile
            }

        val processedImageFile = if (compressOutputPdf) {
            if (baseImageFile == tempFile) {
                recompressImageToJpeg(baseImageFile, quality) ?: baseImageFile
            } else {
                baseImageFile
            }
        } else {
            baseImageFile
        }

        val pdfImage = Image(ImageDataFactory.create(processedImageFile.absolutePath))

        // Adjust the PDF page size to match the image dimensions
        val pdfPageSize = PageSize(pdfImage.imageWidth, pdfImage.imageHeight)
        document.pdfDocument.defaultPageSize = pdfPageSize

        // Add the scaled image to the PDF document
        document.add(pdfImage)
        document.flush()

        // Clean up temporary JPEG, which was converted from WebP file
        if (processedImageFile != baseImageFile) {
            processedImageFile.delete()
        }
        if (baseImageFile != tempFile) {
            baseImageFile.delete()
        }
        tempFile.delete()
    } catch (e: Exception) {
        logger.warning("ImageExtraction $e Error processing file ${zipFileEntry.name}")
        logger.warning("Error processing file ${zipFileEntry.name}: ${e.message}")
        logger.warning("Error details: ${e::class.simpleName} - ${e.message}")
        e.cause?.let { cause ->
            logger.warning("Caused by: ${cause::class.simpleName} - ${cause.message}")
        }
    }
}