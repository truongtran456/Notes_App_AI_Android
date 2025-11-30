package com.starnest.common.extension

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.starnest.config.model.Config
import com.starnest.core.extension.imageDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

suspend fun Bitmap.saveCompressBitmap(
    context: Context,
    file: File = File(context.imageDir, "${System.currentTimeMillis()}.png"),
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = Config.COMPRESS_IMAGE_QUALITY,
): File {
    return withContext(Dispatchers.IO) {
        val fos = FileOutputStream(file)
        compress(format, quality, fos)
        fos.flush()
        fos.close()
        file
    }
}

fun Bitmap.resizeMaximumSize(maxSize: Float = Config.MAX_SERVER_IMAGE_SIZE): Bitmap {
    val targetValue = max(width, height).toFloat()
    val scaleValue = minOf(maxSize, targetValue) / targetValue

    return if (scaleValue < 1) {
        scale(
            (width * scaleValue).toInt(),
            (height * scaleValue).toInt()
        )

    } else this
}