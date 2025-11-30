package com.starnest.common.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.starnest.core.app.AbstractApplication
import java.io.File
import java.io.FileInputStream
import java.io.IOException


fun File.getMimeType(context: Context): String? {
    val type = context.contentResolver.getType(toUri())

    val extension = MimeTypeMap.getFileExtensionFromUrl(toUri().path)
    return type ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}


fun File.saveTo(context: Context, destination: Uri) {
    FileInputStream(this).use { inputStream ->
        context.contentResolver.openOutputStream(destination)?.use {
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                it.write(buffer, 0, bytesRead)
            }
        }
    }
}

fun File.shareFile(context: Context, type: String? = null): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", this)

    val intent = Intent(Intent.ACTION_SEND)
    intent.type = type ?: "*/*"
    intent.putExtra(Intent.EXTRA_STREAM, uri)

    (context.applicationContext as? AbstractApplication)?.shouldShowOpenAd = false

    return intent
}



fun File.copyFileTo(destinationFile: File): File? {
    return try {
        // Tạo thư mục đích nếu chưa tồn tại
        destinationFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        // Sử dụng hàm copyTo() để copy file
        this.copyTo(destinationFile, overwrite = true)
    } catch (e: IOException) {
        println("error in copy a file")
        e.printStackTrace()
        null
    }
}

// Xóa thư mục và file ở trong đó
fun File.deleteFileRecursively() {
    if (this.exists()) {
        try {
            this.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Chỉ xóa file
fun File.deleteFile() {
    if (this.exists()) {
        try {
            this.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun createSaveFileIntent(fileName: String, type: String? = null): Intent {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        this.type = type ?: "*/*"
        putExtra(Intent.EXTRA_TITLE, fileName)
    }
    return intent
}

