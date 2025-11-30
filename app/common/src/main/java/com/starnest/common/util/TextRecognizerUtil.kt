package com.starnest.common.util

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.starnest.common.ui.dialog.loading.ProgressDialogFragment
import com.starnest.core.extension.isTextRecognizerSupported
import com.starnest.core.extension.safeResume
import com.starnest.core.extension.showAllowingStateLoss
import com.starnest.core.utils.FileUtils
import com.starnest.resources.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.Locale


class TextRecognizerUtil {
    companion object {
        @Volatile
        private var sInstance: TextRecognizerUtil? = null

        @JvmStatic
        fun getInstance() = sInstance ?: synchronized(this) {
            sInstance ?: TextRecognizerUtil(
            ).also { sInstance = it }
        }
    }

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun getIntent(context: Context): Intent? {
        if (!context.isTextRecognizerSupported()) {
            return null
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        return intent
    }

    suspend fun extractText(
        scope: CoroutineScope,
        context: Context,
        fragmentManager: FragmentManager,
        file: File
    ): String? {
        return suspendCancellableCoroutine { continuation ->
            val dialog =
                ProgressDialogFragment.newInstance(context.getString(R.string.recognizing_text))

            dialog.showAllowingStateLoss(fragmentManager, "")

            recognizer(context, file) { text ->
                try {
                    FileUtils.deleteFile(file)
                } catch (_: Exception) {

                }
                scope.launch(Dispatchers.Main) {
                    dialog.dismissAllowingStateLoss()

                    continuation.safeResume(text)
                }
            }
        }
    }

    private fun recognizer(context: Context, file: File, callback: (String?) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, file.toUri())

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    callback.invoke(visionText?.text)
                }
                .addOnFailureListener { e ->
                    callback.invoke(null)
                }
        } catch (e: IOException) {
            e.printStackTrace()

            callback.invoke(null)
        }
    }
}