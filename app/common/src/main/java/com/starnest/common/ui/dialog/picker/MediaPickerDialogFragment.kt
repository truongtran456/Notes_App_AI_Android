package com.starnest.common.ui.dialog.picker

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import com.starnest.common.R
import com.starnest.common.databinding.FragmentMediaPickerDialogBinding
import com.starnest.common.model.Constants
import com.starnest.common.ui.appcamera.AppCameraActivity
import com.starnest.common.ui.appcrop.AppCropActivity
import com.starnest.common.ui.appcrop.CropData
import com.starnest.config.model.Config
import com.starnest.core.app.AbstractApplication
import com.starnest.core.base.fragment.BaseDialogFragment
import com.starnest.core.extension.debounceClick
import com.starnest.core.extension.dialogWidth
import com.starnest.core.extension.fileExtension
import com.starnest.core.extension.fileName
import com.starnest.core.extension.imageDir
import com.starnest.core.extension.parcelable
import com.starnest.core.extension.putExtras
import com.starnest.core.extension.runDelayed
import com.starnest.core.extension.saveTo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.UUID

data class FileData(
    val file: File, val fileName: String, val originalName: String
)

@Parcelize
data class MediaPickerOption(
    val shouldResizeImage: Boolean = false,
    val allowPickMultiple: Boolean = false,
    val folder: String? = null
) : Parcelable

@AndroidEntryPoint
class MediaPickerDialogFragment :
    BaseDialogFragment<FragmentMediaPickerDialogBinding, MediaPickerViewModel>(MediaPickerViewModel::class) {
    override fun layoutId(): Int = R.layout.fragment_media_picker_dialog

    interface OnMediaPickerDialogFragmentListener {
        fun onPick(files: List<FileData>)

        fun onDismiss() {

        }
    }

    private val pickerOption by lazy {
        arguments?.parcelable<MediaPickerOption>(Constants.Intents.MEDIA_PICKER_OPTION)
    }

    var listener: OnMediaPickerDialogFragmentListener? = null

    private var isPickFileSucceed = false

    private var cropResultLauncher: ActivityResultLauncher<Intent>? = null

    private var cameraResultLauncher: ActivityResultLauncher<Intent>? = null

    private var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private var multiplePickMedia: ActivityResultLauncher<String>? = null

    override fun initialize() {
        setSize(requireActivity().dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        setupAction()
        setupLauncher()
    }

    private fun destroyLauncher() {
        pickMedia = null
        multiplePickMedia = null
        cropResultLauncher = null
        cameraResultLauncher = null
    }

    private fun setupLauncher() {
        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            val selectedImageUri = uri ?: return@registerForActivityResult

            runDelayed(500) {
                showCrop(selectedImageUri)
            }
        }
        multiplePickMedia =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { results: List<Uri> ->
                saveImages(uris = results)
            }

        cropResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    val data = result.data ?: return@registerForActivityResult

                    val selectedImageUri = data.parcelable<Uri>(Constants.Intents.IMAGE_URI)
                        ?: return@registerForActivityResult

                    saveImage(selectedImageUri)
                }
            }

        cameraResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    val selectedImageUri = result.data?.parcelable<Uri>(Constants.Intents.IMAGE_URI)
                        ?: return@registerForActivityResult

                    saveImage(selectedImageUri)
                }
            }
    }

    private fun showCrop(uri: Uri) {
        val intent = Intent(requireContext(), AppCropActivity::class.java)
        intent.putExtras(
            Constants.Intents.CROP_DATA to CropData(
                uri = uri
            )
        )
        cropResultLauncher?.launch(intent)
    }

    private fun setupAction() {
        binding.apply {
            ivClose.setOnClickListener {
                dismissAllowingStateLoss()
            }
        }
        binding.ivCamera.debounceClick {
            takePicture()
        }

        binding.ivImage.debounceClick {
            pickImage()
        }
    }

    private fun takePicture() {
        (context?.applicationContext as? AbstractApplication)?.shouldShowOpenAd = false

        val intent = Intent(requireContext(), AppCameraActivity::class.java)
        cameraResultLauncher?.launch(intent)
    }

    private fun pickImage() {
        (context?.applicationContext as? AbstractApplication)?.shouldShowOpenAd = false

        val allowPickMultiple = pickerOption?.allowPickMultiple == true

        if (allowPickMultiple) {
            multiplePickMedia?.launch(
                "image/*"
            )
        } else {

            pickMedia?.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun saveImages(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = uris.map {
                async {
                    saveFile(it)
                }
            }.awaitAll()

            val succeedFiles = files.mapNotNull { it }

            withContext(Dispatchers.Main) {
                listener?.onPick(succeedFiles)
                dismissAllowingStateLoss()
            }
        }
    }


    private suspend fun saveFile(uri: Uri): FileData? {
        val folder = pickerOption?.folder

        return withContext(Dispatchers.IO) {
            val originalFileName = uri.fileName(requireContext()) ?: ""
            val fileName = UUID.randomUUID().toString() + "." + originalFileName.fileExtension


            val file = uri.saveTo(
                requireContext(), fileName, getSaveFolder()
            )

            if (file != null) {
                return@withContext FileData(
                    file = file, fileName = if (folder != null) {
                        "$folder/$fileName"
                    } else {
                        fileName
                    }, originalName = originalFileName
                )
            }

            null
        }
    }

    private fun saveImage(uri: Uri) {
        val shouldResizeImage = pickerOption?.shouldResizeImage == true
        val folder = pickerOption?.folder
        val saveFolder = getSaveFolder()

        val savedFile: File?

        val fileName = "${System.currentTimeMillis()}.png"
        val originalFileName = uri.fileName(requireContext()) ?: fileName
        val cropFileName = fileName.replace(".png", "_crop.png")

        if (shouldResizeImage) {
            try {
                val inputStream = context?.contentResolver?.openInputStream(uri) ?: return

                val bitmap = BitmapFactory.decodeStream(inputStream)

                val width = bitmap.width
                val height = bitmap.height
                val minOriginalSize = minOf(width, height)
                val maxOriginalSize = maxOf(width, height)
                var maxScaleSize = Config.SCALE_MAX_SIZE

                var shouldResizeImage = true

                if (maxOriginalSize > 600 && minOriginalSize > 100) {
                    maxScaleSize = maxOf(50, (minOriginalSize.toFloat() / 2).toInt())
                    shouldResizeImage = true
                } else if (minOriginalSize.toFloat() / Config.SCALE_MAX_SIZE >= 2) {
                    maxScaleSize = minOf(minOriginalSize / 2, 1000)
                    shouldResizeImage = true
                } else if (maxScaleSize > minOriginalSize) {
                    shouldResizeImage = false
                }

                if (shouldResizeImage) {
                    if (width > maxScaleSize || height > maxScaleSize) {
                        val targetWidth = maxScaleSize
                        val targetHeight = maxScaleSize

                        val widthScale = targetWidth.toFloat() / width
                        val heightScale = targetHeight.toFloat() / height

                        val scale = maxOf(widthScale, heightScale)

                        val scaledWidth = (width * scale).toInt()
                        val scaledHeight = (height * scale).toInt()

                        val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight)

                        val cropFile = File("${saveFolder}/${cropFileName}")

                        scaledBitmap.saveTo(cropFile)
                    }
                }

                inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }


        savedFile = uri.saveTo(
            requireContext(), fileName, saveFolder
        )

        savedFile?.let {
            listener?.onPick(
                arrayListOf(
                    FileData(
                        file = it, fileName = if (folder != null) {
                            "$folder/$fileName"
                        } else {
                            fileName
                        }, originalName = originalFileName
                    )
                )
            )
            isPickFileSucceed = true
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        destroyLauncher()
        if (!isPickFileSucceed) listener?.onDismiss()
        super.onDestroy()
    }

    private fun getSaveFolder(): String {
        val folder = pickerOption?.folder

        return if (folder != null) {
            "${requireContext().imageDir}/${folder}"
        } else {
            requireContext().imageDir
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            shouldResizeImage: Boolean = false,
            allowPickMultiple: Boolean = false,
            folder: String? = null
        ) = MediaPickerDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelable(
                    Constants.Intents.MEDIA_PICKER_OPTION, MediaPickerOption(
                        shouldResizeImage = shouldResizeImage,
                        allowPickMultiple = allowPickMultiple,
                        folder = folder
                    )
                )
            }
        }
    }

}