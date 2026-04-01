package ru.ainetico.honestprice.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports scan data as CSV + ZIP archive with images.
 * Image filenames = MD5 hash of file content.
 * CSV references images by hash.
 */
class DataExporter(private val context: Context) {

    companion object {
        private const val TAG = "DataExporter"
    }

    data class ExportResult(val csvFile: File, val zipFile: File?)

    suspend fun export(scans: List<Scan>): ExportResult = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }

        // Clean previous exports
        exportDir.listFiles()?.forEach { it.delete() }

        // Build CSV
        val csvFile = File(exportDir, "honest_price_export.csv")
        val imageFiles = mutableMapOf<String, File>() // hash → original file

        csvFile.bufferedWriter().use { writer ->
            writer.write("product_name,price_regular,price_discount,weight_value,weight_unit,store_name,latitude,longitude,image_hash,created_at")
            writer.newLine()

            for (scan in scans) {
                val imageHash = scan.imagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val hash = md5(file)
                        imageFiles[hash] = file
                        hash
                    } else null
                } ?: ""

                writer.write(buildCsvLine(
                    escapeCsv(scan.productName ?: ""),
                    scan.priceRegular ?: "",
                    scan.priceDiscount ?: "",
                    scan.weightValue ?: "",
                    scan.weightUnit ?: "",
                    escapeCsv(scan.storeName ?: ""),
                    scan.latitude?.toString() ?: "",
                    scan.longitude?.toString() ?: "",
                    imageHash,
                    scan.createdAt.toString()
                ))
                writer.newLine()
            }
        }

        Log.i(TAG, "CSV exported: ${scans.size} rows, ${imageFiles.size} images")

        // Build ZIP with images
        val zipFile = if (imageFiles.isNotEmpty()) {
            val zip = File(exportDir, "honest_price_images.zip")
            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                for ((hash, file) in imageFiles) {
                    zos.putNextEntry(ZipEntry("$hash.jpg"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            Log.i(TAG, "ZIP exported: ${imageFiles.size} images")
            zip
        } else null

        ExportResult(csvFile, zipFile)
    }

    fun shareFiles(files: List<File>) {
        val uris = files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Экспорт данных")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var len: Int
            while (input.read(buffer).also { len = it } > 0) {
                digest.update(buffer, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun buildCsvLine(vararg fields: String): String = fields.joinToString(",")
}
