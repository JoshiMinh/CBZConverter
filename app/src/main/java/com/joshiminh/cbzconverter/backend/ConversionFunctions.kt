package com.joshiminh.cbzconverter.backend

import android.net.Uri
import android.os.Environment
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.File
import java.io.IOException
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.streams.asStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream

private val logger = Logger.getLogger("com.puchunguita.cbzconverter.ConversionFunction")
private val COMBINED_TEMP_CBZ_FILE = "combined_temp.cbz"
fun convertCbzToPdf(
    fileUri: List<Uri>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit = { status -> logger.info(status) },
    maxNumberOfPages: Int = 100,
    batchSize: Int = 300,
    outputFileNames: List<String> = List(fileUri.size) { index -> "output_$index.pdf" },
    overrideSortOrderToUseOffset: Boolean = false,
    overrideMergeFiles: Boolean = false,
    outputDirectory: File = contextHelper.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
): List<File> {
    if (fileUri.isEmpty()) { return mutableListOf() }
    val outputFiles = mutableListOf<File>()
    contextHelper.getCacheDir().deleteRecursively()

    if (overrideMergeFiles) {
        return mergeFilesAndCreatePdf(
            contextHelper,
            fileUri,
            subStepStatusAction,
            outputFileNames,
            outputFiles,
            overrideSortOrderToUseOffset,
            outputDirectory,
            maxNumberOfPages,
            batchSize
        )
    } else {
        return applyEachFileAndCreatePdf(
            fileUri,
            outputFileNames,
            contextHelper,
            subStepStatusAction,
            outputFiles,
            overrideSortOrderToUseOffset,
            outputDirectory,
            maxNumberOfPages,
            batchSize
        )
    }
}

private fun applyEachFileAndCreatePdf(
    fileUri: List<Uri>,
    outputFileNames: List<String>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    outputFiles: MutableList<File>,
    overrideSortOrderToUseOffset: Boolean,
    outputDirectory: File,
    maxNumberOfPages: Int,
    batchSize: Int
): MutableList<File> {
    fileUri.forEachIndexed { index, uri ->
        val outputFileName = outputFileNames[index]

        try {
            val tempFile = copyCbzToCacheAndCloseInputStream(contextHelper, subStepStatusAction, uri)

            createPdfEitherSingleOrMultiple(
                tempFile = tempFile,
                subStepStatusAction = subStepStatusAction,
                overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
                outputFileName = outputFileName,
                outputDirectory = outputDirectory,
                maxNumberOfPages = maxNumberOfPages,
                outputFiles = outputFiles,
                contextHelper = contextHelper,
                batchSize = batchSize
            )
        } catch (ioException: IOException) {
            return@forEachIndexed
        }
    }
    return outputFiles
}

private fun mergeFilesAndCreatePdf(
    contextHelper: ContextHelper,
    fileUri: List<Uri>,
    subStepStatusAction: (String) -> Unit,
    outputFileNames: List<String>,
    outputFiles: MutableList<File>,
    overrideSortOrderToUseOffset: Boolean,
    outputDirectory: File,
    maxNumberOfPages: Int,
    batchSize: Int
): MutableList<File> {
    subStepStatusAction("Creating $COMBINED_TEMP_CBZ_FILE in Cache")
    val combinedTempFile = File(contextHelper.getCacheDir(), COMBINED_TEMP_CBZ_FILE)

    ZipOutputStream(combinedTempFile.outputStream()).use { zipOutputStream ->
        fileUri.forEachIndexed { index, uri ->
            addEntriesToZip(zipOutputStream, outputFileNames[index], index, subStepStatusAction, contextHelper, uri)
        }
    }

    val outputFileName = outputFileNames[0]

    createPdfEitherSingleOrMultiple(
        tempFile = combinedTempFile,
        subStepStatusAction = subStepStatusAction,
        overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
        outputFileName = outputFileName,
        outputDirectory = outputDirectory,
        maxNumberOfPages = maxNumberOfPages,
        outputFiles = outputFiles,
        contextHelper = contextHelper,
        batchSize = batchSize
    )

    return outputFiles
}

private fun orderZipEntriesToList(
    overrideSortOrderToUseOffset: Boolean,
    zipFile: ZipFile
): List<ZipEntry> {
    val zipFileEntriesStream = zipFile.entries().asSequence().asStream()

    // Without `.sorted` it goes based upon order in zip which uses a field called offset,
        // this order is inherited through zipFile.stream().
    // Using `.sorted`, sorts by file name in ascending order
    return if (overrideSortOrderToUseOffset) {
        zipFileEntriesStream.collect(Collectors.toList())
    } else {
        zipFileEntriesStream
            .sorted { f1, f2 -> f1.name.compareTo(f2.name) }
            .collect(Collectors.toList())
    }
}

private fun addEntriesToZip(
    zipOutputStream: ZipOutputStream,
    fileName: String,
    index: Int,
    subStepStatusAction: (String) -> Unit,
    contextHelper: ContextHelper,
    uri: Uri
) {
    try {
        val tempFile = copyCbzToCacheAndCloseInputStream(contextHelper, subStepStatusAction, uri)
        val zipFile = ZipFile(tempFile)
        subStepStatusAction("Adding ${zipFile.size()} entries from $fileName")

        zipFile.entries().asSequence().forEach { zipEntry ->
            try {
                // Using index for ordering by name to continue functioning correctly.
                // Adding padding to start, without it passing 10_ is between 0_ and 1_.
                // Padding length being 9, allows correct order when merging up to 999,999,999 files.
                // filename is added as prefix to ensure unique naming per file, otherwise duplication error.
                val formattedIndex = index.toString().padStart(9, '0')
                val currentFileUniqueName = "${formattedIndex}_${fileName}_${zipEntry.name}"

                // Add entry to the output ZIP
                zipOutputStream.putNextEntry(ZipEntry(currentFileUniqueName))

                // Stream the entry's data into the output stream
                zipFile.getInputStream(zipEntry).copyTo(zipOutputStream)

                // Close the current entry
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
    } catch (ioException: IOException) {
        return
    }
}

fun createPdfEitherSingleOrMultiple(
    tempFile: File,
    subStepStatusAction: (String) -> Unit,
    overrideSortOrderToUseOffset: Boolean,
    outputFileName: String,
    outputDirectory: File,
    maxNumberOfPages: Int,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper,
    batchSize: Int
): MutableList<File> {
    val zipFile = ZipFile(tempFile)

    val totalNumberOfImages = zipFile.size()
    if (totalNumberOfImages == 0) { subStepStatusAction("No images found in CBZ file: $outputFileName"); return mutableListOf() }

    val zipFileEntriesList = orderZipEntriesToList(overrideSortOrderToUseOffset, zipFile)

    if (!outputDirectory.exists()) { outputDirectory.mkdirs() }

    if (totalNumberOfImages > maxNumberOfPages) {
        createMultiplePdfFromCbz(
            totalNumberOfImages,
            maxNumberOfPages,
            zipFileEntriesList,
            outputFileName,
            outputDirectory,
            subStepStatusAction,
            zipFile,
            outputFiles,
            contextHelper,
            batchSize
        )
    } else {
        createSinglePdfFromCbz(
            totalNumberOfImages,
            zipFileEntriesList,
            outputFileName,
            outputDirectory,
            subStepStatusAction,
            zipFile,
            outputFiles,
            contextHelper,
            batchSize
        )
    }

    zipFile.close()
    tempFile.delete()

    return outputFiles
}

@Throws(IOException::class)
private fun copyCbzToCacheAndCloseInputStream(contextHelper: ContextHelper, subStepStatusAction: (String) -> Unit, uri: Uri): File {
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

internal fun calculateRange(index: Int, pageSize: Int, totalItems: Int): Pair<Int, Int> {
    val startIndex = index.times(pageSize)
    val nextPossibleEndIndex = index.plus(1).times(pageSize)
    val endIndex = if (nextPossibleEndIndex > totalItems) totalItems else nextPossibleEndIndex
    return Pair(startIndex, endIndex)
}

private fun createPdfFromImageList(
    imagesToProcess: List<ZipEntry>,
    outputFile: File,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit,
    messageFormat: (Int) -> String
) {
    PdfWriter(outputFile.absolutePath).use { writer ->
        PdfDocument(writer).use { pdfDoc ->
            Document(pdfDoc, PageSize.LETTER, true).use { document ->
                setMarginForDocument(document)
                for ((currentImageIndex, imageFile) in imagesToProcess.withIndex()) {
                    subStepStatusAction(messageFormat(currentImageIndex + 1))
                    extractImageAndAddToPDFDocument(zipFile, imageFile, document, contextHelper, subStepStatusAction)
                }
                pdfDoc.writer.flush()
                writer.flush()
                document.close()
            }
        }
    }
}

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
    batchSize: Int
) {
    val amountOfFilesToExport = ceil(totalNumberOfImages.toDouble() / maxNumberOfPages).toInt()

    IntStream.range(0, amountOfFilesToExport).forEach { index ->
        val newOutputFileName = outputFileName.replace(".pdf", "_part-${index + 1}.pdf")
        val outputFile = File(outputDirectory, newOutputFileName)
        val (startIndex, endIndex) = calculateRange(index, maxNumberOfPages, totalNumberOfImages)
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)
        val imagesInThisPart = imagesToProcess.size

        // Check if this part needs memory batch processing to avoid memory issues
        if (imagesInThisPart > batchSize) {
            // Use memory batch processing for this part
            val processedFile = createPdfWithBatchProcessing(
                totalNumberOfImages = imagesInThisPart,
                zipFileEntriesList = imagesToProcess,
                outputFileName = newOutputFileName,
                outputDirectory = outputDirectory,
                subStepStatusAction = { message ->
                    subStepStatusAction(
                        "Processing part ${index + 1} of $amountOfFilesToExport - $message"
                    )
                },
                zipFile = zipFile,
                contextHelper = contextHelper,
                batchSize = batchSize
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
                    "Processing part ${index + 1} of $amountOfFilesToExport " +
                            "- Processing image file " +
                            "${startIndex + currentImageIndex} " +
                            "of $totalNumberOfImages"
                }
            )
            outputFiles.add(outputFile)
        }
    }
}

private fun createPdfWithBatchProcessing(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    contextHelper: ContextHelper,
    batchSize: Int
): File {
    // Used to circumvent the 512 MB max Ram usage on android apps
    // it splits the total files into memory batches and then merges them into a single pdf
    // while only having batchSize pages open in memory at a time
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
                "Processing memory batch ${memoryBatchIndex + 1} of $amountOfMemoryBatches " +
                        "- Processing image file " +
                        "${startIndex + currentImageIndex} " +
                        "of $totalNumberOfImages"
            }
        )
        tempOutputFiles.add(tempBatchFile)
    }
    
    // Merge all memory batch files into the final output file
    mergePdfFiles(outputDirectory, outputFileName, tempOutputFiles)
    
    // Return the final merged file
    return File(outputDirectory, outputFileName)
}

private fun createSinglePdfFromCbz(
    totalNumberOfImages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper,
    batchSize: Int
) {
    if (totalNumberOfImages > batchSize){
        val processedFile = createPdfWithBatchProcessing(
            totalNumberOfImages,
            zipFileEntriesList,
            outputFileName,
            outputDirectory,
            subStepStatusAction,
            zipFile,
            contextHelper,
            batchSize
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
            "Processing image file " +
                    "$currentImageIndex " +
                    "of $totalNumberOfImages"
        }
    )
    outputFiles.add(outputFile)
}

private fun mergePdfFiles(outputDirectory: File?, outputFileName: String, outputFiles: MutableList<File>){
    val outputFile = File(outputDirectory, outputFileName)
    val outputStream = outputFile.outputStream()
    val pdfWriter = PdfWriter(outputStream)
    val finalPdfDocument = PdfDocument(pdfWriter)
    val pdfMerger = PdfMerger(finalPdfDocument)

    outputFiles.forEachIndexed { _, file ->
        PdfDocument(PdfReader(file)).use{ pdfDocument ->
            pdfMerger.merge(pdfDocument, 1, pdfDocument.numberOfPages)
            finalPdfDocument.flushCopiedObjects(pdfDocument)
            pdfDocument.close()
            outputStream.flush()
            pdfWriter.flush()
        }
        file.delete()
    }
    pdfMerger.close()
    outputFiles.clear()
    outputFiles.add(outputFile)
}

private fun setMarginForDocument(document: Document){
    // Overriding margins, due to lots of empty space at the bottom of longer pages
    document.setMargins(15f, 10f, 15f, 10f)  // Top, Right, Bottom, Left
}

private fun convertWebpToJpeg(inputFile: File): File {
    try {
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
        
        return outputFile
    } catch (e: Exception) {
        logger.warning("Failed to convert WebP to JPEG: ${e.message}")
        throw e
    }
}

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
            zipFile.getInputStream(zipFileEntry).use {
                imageInputStream -> tempFileOutputStream.write(imageInputStream.readBytes())
            }
        }

        // Check if it's a WebP file and convert if necessary
        val imageFileToProcess = if (zipFileEntry.name.lowercase().endsWith(".webp")) {
            subStepStatusAction("Converting WebP image: ${zipFileEntry.name}")
            logger.info("Converting WebP image: ${zipFileEntry.name}")
            convertWebpToJpeg(tempFile)
        } else {
            tempFile
        }

        val pdfImage = Image(ImageDataFactory.create(imageFileToProcess.absolutePath))

        // Adjust the PDF page size to match the image dimensions
        val pdfPageSize = PageSize(
            pdfImage.imageWidth,
            pdfImage.imageHeight
        )
        document.pdfDocument.setDefaultPageSize(pdfPageSize)

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
        // Log more details about the error
        logger.warning("Error processing file ${zipFileEntry.name}: ${e.message}")
        logger.warning("Error details: ${e::class.simpleName} - ${e.message}")
        e.cause?.let { cause ->
            logger.warning("Caused by: ${cause::class.simpleName} - ${cause.message}")
        }
    }
}