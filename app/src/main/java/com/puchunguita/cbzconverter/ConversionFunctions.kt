package com.puchunguita.cbzconverter

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

private val logger = Logger.getLogger("com.puchunguita.cbzconverter.ConversionFunction")
private val COMBINED_TEMP_CBZ_FILE = "combined_temp.cbz"
fun convertCbzToPdf(
    fileUri: List<Uri>,
    contextHelper: ContextHelper,
    subStepStatusAction: (String) -> Unit = { status -> logger.info(status) },
    maxNumberOfPages: Int = 100,
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
            maxNumberOfPages
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
            maxNumberOfPages
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
    maxNumberOfPages: Int
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
                contextHelper = contextHelper
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
    maxNumberOfPages: Int
): MutableList<File> {
    subStepStatusAction("Creating $COMBINED_TEMP_CBZ_FILE in Cache")
    val combinedTempFile = File(contextHelper.getCacheDir(), COMBINED_TEMP_CBZ_FILE)

    ZipOutputStream(combinedTempFile.outputStream()).use { zipOutputStream ->
        fileUri.forEachIndexed() { index, uri ->
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
        contextHelper = contextHelper
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
    contextHelper: ContextHelper

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
            contextHelper
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
            contextHelper
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

private fun createMultiplePdfFromCbz(
    totalNumberOfImages: Int,
    maxNumberOfPages: Int,
    zipFileEntriesList: List<ZipEntry>,
    outputFileName: String,
    outputDirectory: File?,
    subStepStatusAction: (String) -> Unit,
    zipFile: ZipFile,
    outputFiles: MutableList<File>,
    contextHelper: ContextHelper
) {
    val amountOfFilesToExport = ceil(totalNumberOfImages.toDouble() / maxNumberOfPages).toInt()

    IntStream.range(0, amountOfFilesToExport).forEach { index ->
        val newOutputFileName = outputFileName.replace(".pdf", "_part-${index + 1}.pdf")
        val outputFile = File(outputDirectory, newOutputFileName)
        val startIndex = index.times(maxNumberOfPages)
        val nextPossibleEndIndex = index.plus(1).times(maxNumberOfPages)
        val endIndex =
            if (nextPossibleEndIndex > totalNumberOfImages) totalNumberOfImages else nextPossibleEndIndex
        val imagesToProcess = zipFileEntriesList.subList(startIndex, endIndex)

        PdfWriter(outputFile.absolutePath).use { writer ->
            PdfDocument(writer).use { pdfDoc ->
                Document(pdfDoc, PageSize.LETTER, true).use { document ->
                    setMarginForDocument(document)
                    for ((currentImageIndex, imageFile) in imagesToProcess.withIndex()) {
                        subStepStatusAction(
                            "Processing part ${index + 1} of $amountOfFilesToExport " +
                                    "- Processing image file " +
                                    "${index.times(maxNumberOfPages) + currentImageIndex + 1} " +
                                    "of $totalNumberOfImages"
                        )
                        extractImageAndAddToPDFDocument(zipFile, imageFile, document, contextHelper)
                    }
                }
            }
            outputFiles.add(outputFile)
        }
    }
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
    batchSize: Int = 300 // Default batch size of 300 pages
) {
    // Used to circumvent the 512 MB max Ram usage on android apps
    // it splits the total files into batches of 300 pages
    // then merges them into a single pdf while only having 300 pages open in memory at a time
    if (totalNumberOfImages > batchSize){
        createMultiplePdfFromCbz(
            totalNumberOfImages,
            batchSize,
            zipFileEntriesList,
            outputFileName,
            contextHelper.getCacheDir(),
            subStepStatusAction,
            zipFile,
            outputFiles,
            contextHelper
        )
        File(contextHelper.getCacheDir(), COMBINED_TEMP_CBZ_FILE).delete()
        mergePdfFiles(outputDirectory, outputFileName, outputFiles)
        return
    }

    val outputFile = File(outputDirectory, outputFileName)

    PdfWriter(outputFile.absolutePath).use { writer ->
        PdfDocument(writer).use { pdfDoc ->
            Document(pdfDoc, PageSize.LETTER, true).use { document ->
                setMarginForDocument(document)
                for ((currentImageIndex, imageFile) in zipFileEntriesList.withIndex()) {
                    subStepStatusAction(
                        "Processing image file " +
                                "${currentImageIndex + 1} " +
                                "of $totalNumberOfImages"
                    )
                    extractImageAndAddToPDFDocument(zipFile, imageFile, document, contextHelper)
                }
                pdfDoc.writer.flush()
                writer.flush()
                document.close()
            }
        }
        outputFiles.add(outputFile)
    }
}

private fun mergePdfFiles(outputDirectory: File?, outputFileName: String, outputFiles: MutableList<File>){
    val outputFile = File(outputDirectory, outputFileName)
    val outputStream = outputFile.outputStream()
    val pdfWriter = PdfWriter(outputStream)
    val finalPdfDocument = PdfDocument(pdfWriter)
    val pdfMerger = PdfMerger(finalPdfDocument)

    outputFiles.forEachIndexed { index, file ->
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

private fun extractImageAndAddToPDFDocument(
    zipFile: ZipFile,
    zipFileEntry: ZipEntry,
    document: Document,
    contextHelper: ContextHelper
) {
    try {
        // Create temp file to avoid large memory usage
        val tempFile = File(contextHelper.getCacheDir(), "temp_image")
        tempFile.outputStream().use { tempFileOutputStream ->
            zipFile.getInputStream(zipFileEntry).use {
                imageInputStream -> tempFileOutputStream.write(imageInputStream.readBytes())
            }
        }

        val pdfImage = Image(ImageDataFactory.create(tempFile.absolutePath))

        // Adjust the PDF page size to match the image dimensions
        val pdfPageSize = PageSize(
            pdfImage.imageWidth,
            pdfImage.imageHeight
        )
        document.pdfDocument.setDefaultPageSize(pdfPageSize)

        // Add the scaled image to the PDF document
        document.add(pdfImage)
        document.flush()
        tempFile.delete()
    } catch (e: Exception) {
        logger.warning("ImageExtraction $e Error processing file ${zipFileEntry.name}")
    }
}