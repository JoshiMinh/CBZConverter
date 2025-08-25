package com.joshiminh.cbzconverter.backend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.kernel.pdf.CompressionConstants
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.streams.asStream

private val logger = Logger.getLogger("com.joshiminh.cbzconverter.ConversionFunction")
private const val COMBINED_TEMP_CBZ_FILE = "combined_temp.cbz"

/**
 * Entry point for converting one or multiple CBZ files to PDF(s).
 */
fun convertCbzToPdf(
    fileUri: List<Uri>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit = { status -> logger.info(status) },
    maxNumberOfPages: Int = 100,
    batchSize: Int = 300,
    outputFileNames: List<String> = List(fileUri.size) { index -> "output_$index.pdf" },
    overrideSortOrderToUseOffset: Boolean = false,
    overrideMergeFiles: Boolean = false,
    compressOutputPdf: Boolean = false,
    outputDirectory: File = contextHelper.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
): List<File> {
    if (fileUri.isEmpty()) return mutableListOf()

    val outputFiles = mutableListOf<File>()
    contextHelper.getCacheDir().deleteRecursively()

    return if (overrideMergeFiles) {
        mergeFilesAndCreatePdf(
            contextHelper = contextHelper,
            fileUri = fileUri,
            subStepStatusAction = subStepStatusAction,
            outputFileNames = outputFileNames,
            outputFiles = outputFiles,
            overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
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
            overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
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
    outputFiles: MutableList<File>,
    overrideSortOrderToUseOffset: Boolean,
    outputDirectory: File,
    maxNumberOfPages: Int,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<File> {
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
                overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
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
    outputFiles: MutableList<File>,
    overrideSortOrderToUseOffset: Boolean,
    outputDirectory: File,
    maxNumberOfPages: Int,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<File> {
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
        overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
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
private fun orderZipEntriesToList(
    overrideSortOrderToUseOffset: Boolean,
    zipFile: ZipFile
): List<ZipEntry> {
    val zipFileEntriesStream = zipFile.entries().asSequence().asStream()

    // Without `.sorted`, iteration follows the original order in the ZIP (offset-based).
    // With `.sorted`, sort by filename lexicographically (ascending).
    return if (overrideSortOrderToUseOffset) {
        zipFileEntriesStream.collect(Collectors.toList())
    } else {
        zipFileEntriesStream
            .sorted { f1, f2 -> f1.name.compareTo(f2.name) }
            .collect(Collectors.toList())
    }
}

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
    try {
        val tempFile = copyCbzToCacheAndCloseInputStream(
            contextHelper = contextHelper,
            subStepStatusAction = subStepStatusAction,
            uri = uri
        )

        val zipFile = ZipFile(tempFile)
        subStepStatusAction("Adding ${zipFile.size()} entries from $fileName")

        zipFile.entries().asSequence().forEach { zipEntry ->
            try {
                // Use index for ordering by name to continue functioning correctly.
                // Add left padding so "10_" doesn't sort between "0_" and "1_".
                // Padding length 9 supports merging up to 999,999,999 files.
                // Prefix with original fileName to ensure uniqueness across files.
                val formattedIndex = index.toString().padStart(9, '0')
                val currentFileUniqueName = "${formattedIndex}_${fileName}_${zipEntry.name}"

                zipOutputStream.putNextEntry(ZipEntry(currentFileUniqueName))
                zipFile.getInputStream(zipEntry).copyTo(zipOutputStream)
                zipOutputStream.closeEntry()
                zipOutputStream.flush()
            } catch (e: Exception) {
                subStepStatusAction("Error processing file ${zipEntry.name}")
                logger.warning("Exception message: ${e.message}")
                logger.warning("Exception stacktrace: ${e.stackTrace.contentToString()}")
            }
        }

        zipFile.close()
        tempFile.delete()
    } catch (_: IOException) {
        return
    }
}

/**
 * Decide whether to create one PDF or split into multiple, then export.
 */
fun createPdfEitherSingleOrMultiple(
    tempFile: File,
    subStepStatusAction: (String) -> Unit,
    overrideSortOrderToUseOffset: Boolean,
    outputFileName: String,
    outputDirectory: File,
    maxNumberOfPages: Int,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
): MutableList<File> {
    val zipFile = ZipFile(tempFile)
    val totalNumberOfImages = zipFile.size()

    if (totalNumberOfImages == 0) {
        subStepStatusAction("No images found in CBZ file: $outputFileName")
        return mutableListOf()
    }

    val zipFileEntriesList = orderZipEntriesToList(
        overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
        zipFile = zipFile
    )

    if (!outputDirectory.exists()) outputDirectory.mkdirs()

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

    zipFile.close()
    tempFile.delete()

    return outputFiles
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

    tempFile.outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
    }
    inputStream.close()

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
    outputFile: File,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    messageFormat: (Int) -> String,
    compressOutputPdf: Boolean
) {
    val writerProperties = if (compressOutputPdf) {
        WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
    } else null
    val pdfWriter = if (writerProperties != null) {
        PdfWriter(outputFile.absolutePath, writerProperties)
    } else {
        PdfWriter(outputFile.absolutePath)
    }

    pdfWriter.use { writer ->
        PdfDocument(writer).use { pdfDoc ->
            Document(pdfDoc, PageSize.LETTER, true).use { document ->
                setMarginForDocument(document)

                for ((currentImageIndex, imageFile) in imagesToProcess.withIndex()) {
                    subStepStatusAction(messageFormat(currentImageIndex + 1))
                    extractImageAndAddToPDFDocument(
                        zipFile = zipFile,
                        zipFileEntry = imageFile,
                        document = document,
                        contextHelper = contextHelper,
                        subStepStatusAction = subStepStatusAction
                    )
                }

                pdfDoc.writer.flush()
                writer.flush()
                document.close()
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
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
) {
    val amountOfFilesToExport = ceil(totalNumberOfImages.toDouble() / maxNumberOfPages).toInt()

    IntStream.range(0, amountOfFilesToExport).forEach { index ->
        val newOutputFileName = outputFileName.replace(".pdf", "_part-${index + 1}.pdf")
        val outputFile = File(outputDirectory, newOutputFileName)

        val (startIndex, endIndex) = calculateRange(index, maxNumberOfPages, totalNumberOfImages)
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)
        val imagesInThisPart = imagesToProcess.size

        if (imagesInThisPart > batchSize) {
            // Use memory batch processing for this part
            val processedFile = createPdfWithBatchProcessing(
                totalNumberOfImages = imagesInThisPart,
                zipFileEntriesList = imagesToProcess,
                outputFileName = newOutputFileName,
                outputDirectory = outputDirectory,
                subStepStatusAction = { message ->
                    subStepStatusAction("Processing part ${index + 1} of $amountOfFilesToExport - $message")
                },
                zipFile = zipFile,
                contextHelper = contextHelper,
                batchSize = batchSize,
                compressOutputPdf = compressOutputPdf
            )
            outputFiles.add(processedFile)
        } else {
            // Process normally without memory batch processing
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
        }
    }
}

/**
 * Create a single PDF using batch processing when needed to respect memory constraints.
 */
private fun createPdfWithBatchProcessing(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
): File {
    // Used to circumvent the 512 MB max RAM usage on Android apps:
    // split the total files into memory batches and then merge them into a single PDF,
    // keeping at most 'batchSize' pages in memory at a time.
    val tempOutputFiles = mutableListOf<File>()
    val amountOfMemoryBatches = ceil(totalNumberOfImages.toDouble() / batchSize).toInt()

    IntStream.range(0, amountOfMemoryBatches).forEach { memoryBatchIndex ->
        val tempMemoryBatchFileName = "temp_memory_batch_${memoryBatchIndex + 1}.pdf"
        val tempBatchFile = File(contextHelper.getCacheDir(), tempMemoryBatchFileName)

        val (startIndex, endIndex) = calculateRange(memoryBatchIndex, batchSize, totalNumberOfImages)
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)

        createPdfFromImageList(
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
        outputDirectory = outputDirectory,
        outputFileName = outputFileName,
        outputFiles = tempOutputFiles,
        compressOutputPdf = compressOutputPdf
    )

    // Return the final merged file
    return File(outputDirectory, outputFileName)
}

/**
 * Create a single PDF when within memory constraints.
 */
private fun createSinglePdfFromCbz(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper,
    batchSize: Int,
    compressOutputPdf: Boolean
) {
    if (totalNumberOfImages > batchSize) {
        val processedFile = createPdfWithBatchProcessing(
            totalNumberOfImages = totalNumberOfImages,
            zipFileEntriesList = zipFileEntriesList,
            outputFileName = outputFileName,
            outputDirectory = outputDirectory,
            subStepStatusAction = subStepStatusAction,
            zipFile = zipFile,
            contextHelper = contextHelper,
            batchSize = batchSize,
            compressOutputPdf = compressOutputPdf
        )
        outputFiles.add(processedFile)
        return
    }

    val outputFile = File(outputDirectory, outputFileName)

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
}

/**
 * Merge all PDFs in 'outputFiles' into a single PDF 'outputFileName' inside 'outputDirectory'.
 * Also clears and updates 'outputFiles' to contain only the merged file.
 */
private fun mergePdfFiles(
    outputDirectory: File?,
    outputFileName: String,
    outputFiles: MutableList<File>,
    compressOutputPdf: Boolean
) {
    val outputFile = File(outputDirectory, outputFileName)
    val outputStream = outputFile.outputStream()
    val writerProperties = if (compressOutputPdf) {
        WriterProperties().setCompressionLevel(CompressionConstants.BEST_COMPRESSION)
    } else null
    val pdfWriter = if (writerProperties != null) {
        PdfWriter(outputStream, writerProperties)
    } else {
        PdfWriter(outputStream)
    }
    val finalPdfDocument = PdfDocument(pdfWriter)
    val pdfMerger = PdfMerger(finalPdfDocument)

    outputFiles.forEach { file ->
        PdfDocument(PdfReader(file)).use { pdfDocument ->
            pdfMerger.merge(pdfDocument, 1, pdfDocument.numberOfPages)
            finalPdfDocument.flushCopiedObjects(pdfDocument)
            outputStream.flush()
            pdfWriter.flush()
        }
        file.delete()
    }

    pdfMerger.close()
    outputFiles.clear()
    outputFiles.add(outputFile)
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
private fun convertWebpToJpeg(inputFile: File): File {
    return try {
        // Decode WebP using Android's BitmapFactory
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
            ?: throw IOException("Failed to decode WebP image")

        // Create output JPEG file
        val outputFile = File(inputFile.parentFile, "temp_converted.jpg")
        val outputStream = FileOutputStream(outputFile)

        // Compress to JPEG with 90% quality
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        bitmap.recycle()

        outputFile
    } catch (e: Exception) {
        logger.warning("Failed to convert WebP to JPEG: ${e.message}")
        throw e
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
    subStepStatusAction: (String) -> Unit
) {
    try {
        // Create temp file to avoid large memory usage
        val tempFile = File(contextHelper.getCacheDir(), "temp_image")
        tempFile.outputStream().use { tempFileOutputStream ->
            zipFile.getInputStream(zipFileEntry).use { imageInputStream ->
                tempFileOutputStream.write(imageInputStream.readBytes())
            }
        }

        // Check if it's a WebP file and convert if necessary
        val imageFileToProcess =
            if (zipFileEntry.name.lowercase().endsWith(".webp")) {
                subStepStatusAction("Converting WebP image: ${zipFileEntry.name}")
                logger.info("Converting WebP image: ${zipFileEntry.name}")
                convertWebpToJpeg(tempFile)
            } else {
                tempFile
            }

        val pdfImage = Image(ImageDataFactory.create(imageFileToProcess.absolutePath))

        // Adjust the PDF page size to match the image dimensions
        val pdfPageSize = PageSize(pdfImage.imageWidth, pdfImage.imageHeight)
        document.pdfDocument.defaultPageSize = pdfPageSize

        // Add the scaled image to the PDF document
        document.add(pdfImage)
        document.flush()

        // Clean up temporary JPEG, which was converted from WebP file
        if (imageFileToProcess != tempFile) {
            imageFileToProcess.delete()
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