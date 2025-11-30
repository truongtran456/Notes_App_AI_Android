package com.starnest.common.ui.appcrop

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.starnest.common.R
import com.starnest.common.databinding.ActivityAppCropBinding
import com.starnest.common.model.Constants
import com.starnest.core.base.activity.BaseActivity
import com.starnest.core.base.viewmodel.BaseViewModel
import com.starnest.core.extension.debounceClick
import com.starnest.core.extension.gone
import com.starnest.core.extension.parcelable
import com.starnest.core.extension.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize

@Parcelize
data class CropData(
    val uri: Uri,
    val cropTitle: String? = null,
    val isCamera: Boolean = false
) : Parcelable

@AndroidEntryPoint
class AppCropActivity :
    BaseActivity<ActivityAppCropBinding, BaseViewModel>(
        BaseViewModel::class
    ) {
    private val cropData by lazy { intent?.parcelable<CropData>(Constants.Intents.CROP_DATA) }

    override fun layoutId(): Int = R.layout.activity_app_crop

    override fun initialize() {
        setupAction()
        setupUI()
    }

    override fun finish() {
        super.finish()
    }

    private fun setupAction() {
        binding.toolbar.backButton.debounceClick {
            finish()
        }
        binding.llSolve.debounceClick {
            solve()
        }

        binding.llRotate.debounceClick {
            binding.imageView.rotateImage(90)
        }
        binding.llRetake.debounceClick {
            setResult(
                RESULT_CANCELED,
                Intent().apply {
                    putExtra(Constants.Intents.IS_RETAKE, true)
                }
            )

            finish()
        }
    }

    private fun solve() {
        val cropImageOptions = CropImageOptions()
        binding.imageView.setOnCropImageCompleteListener(object :
            CropImageView.OnCropImageCompleteListener {
            override fun onCropImageComplete(
                view: CropImageView,
                result: CropImageView.CropResult
            ) {
                if (result.isSuccessful) {
                    handleResult(result.uriContent)
                } else {
                    toast("cropping template failed")
                }
            }
        })
        binding.imageView.croppedImageAsync(
            saveCompressFormat = cropImageOptions.outputCompressFormat,
            saveCompressQuality = cropImageOptions.outputCompressQuality,
            reqWidth = cropImageOptions.outputRequestWidth,
            reqHeight = cropImageOptions.outputRequestHeight,
            options = cropImageOptions.outputRequestSizeOptions,
            customOutputUri = cropImageOptions.customOutputUri,
        )
    }

    private fun handleResult(uri: Uri?) {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(Constants.Intents.IMAGE_URI, uri)
                putExtra(Constants.Intents.IS_CAMERA, cropData?.isCamera)
            }
        )

        finish()
    }

    private fun setupUI() {
        val cropData = cropData ?: return

        binding.toolbar.tvTitle.gone()
        binding.tvCrop.text = cropData.cropTitle ?: getString(com.starnest.resources.R.string.continue_title)
        binding.imageView.setImageUriAsync(cropData.uri)
    }
}