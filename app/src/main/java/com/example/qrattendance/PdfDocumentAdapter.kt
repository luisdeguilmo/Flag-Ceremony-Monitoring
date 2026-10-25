package com.example.qrattendance

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfDocumentAdapter(private val context: Context, private val filePath: String) :
    PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle
    ) {
        Log.d("PdfDocumentAdapter", "onLayout called") // Add this line

        // Check if the operation was canceled
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        // Verify that the file exists
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            Log.e("PdfDocumentAdapter", "File not found or not readable: $filePath")
            callback.onLayoutFailed("File not found or inaccessible.")
            return
        }

        // Provide layout information to the print framework
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        Log.d("PdfDocumentAdapter", "onWrite called") // Add this line

        try {
            FileInputStream(filePath).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    // Write the file contents while checking for cancellation
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (cancellationSignal.isCanceled) {
                            Log.w("PdfDocumentAdapter", "Write operation canceled by user")
                            callback.onWriteCancelled()
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Notify the framework that writing was successful
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("PdfDocumentAdapter", "Error during write: ${e.message}", e)
            callback.onWriteFailed(e.message)
        }
    }

}
