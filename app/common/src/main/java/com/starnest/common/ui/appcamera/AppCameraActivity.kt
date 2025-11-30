package com.starnest.common.ui.appcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.Surface
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.starnest.common.databinding.ActivityAppCameraBinding
import com.starnest.common.extension.saveCompressBitmap
import com.starnest.common.extension.showDefaultDialog
import com.starnest.common.model.Constants
import com.starnest.common.ui.appcrop.AppCropActivity
import com.starnest.common.ui.appcrop.CropData
import com.starnest.config.model.Config
import com.starnest.core.base.activity.BaseActivity
import com.starnest.core.extension.debounceClick
import com.starnest.core.extension.imageDir
import com.starnest.core.extension.parcelable
import com.starnest.core.extension.saveTo
import com.starnest.core.extension.tempDir
import com.starnest.core.utils.FileUtils
import com.starnest.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


enum class CameraActionType {
    ACTION_START_CAMERA,
    ACTION_TAKE_PICTURE,
    ACTION_FLASH
}

@AndroidEntryPoint
class AppCameraActivity : BaseActivity<ActivityAppCameraBinding, AppCameraViewModel>(
    AppCameraViewModel::class
) {

    override fun layoutId(): Int = com.starnest.common.R.layout.activity_app_camera

    private var imageCapture: ImageCapture? = null

    private var cameraExecutor: ExecutorService? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var activityResultLauncher: ActivityResultLauncher<Array<String>>? = null

    private var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private var cropLauncher: ActivityResultLauncher<Intent>? = null

    private var settingLauncher: ActivityResultLauncher<Intent>? = null

    private var actionType: CameraActionType = CameraActionType.ACTION_START_CAMERA


    override fun initialize() {
        binding.lifecycleOwner = this
        settingLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (!allPermissionsGranted()) {
                showDialogMessage(isOpenSetting = true)
            } else {
                onPermissionsResult()
            }
        }

        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                showDialogMessage(isOpenSetting = true)
            } else {
                onPermissionsResult()
            }
        }

        cropLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val imageUri = result.data?.parcelable<Uri>(
                        Constants.Intents.IMAGE_URI
                    ) ?: return@registerForActivityResult

                    val isCropImage = result.data?.getBooleanExtra(
                        Constants.Intents.IS_CAMERA, false
                    ) ?: false

                    saveImageResult(
                        imageUri,
                        Config.MAX_RESULT_IMAGE_SIZE,
                        isCropImage
                    )
                }
            }

        pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                // Callback is invoked after the user selects a media item or closes the
                // photo picker.
                val selectedImageUri = uri ?: return@registerForActivityResult

                handleResult(selectedImageUri, false)
            }

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        FileUtils.ensureExistDir(tempDir)
        FileUtils.ensureExistDir(imageDir)

        checkPermission(
            actionType = CameraActionType.ACTION_START_CAMERA,
            action = ::startCamera
        )

        setupUI()
        setupAction()
        observe()
    }

    private fun observe() {
    }

    private fun saveImageResult(
        imageUri: Uri,
        targetSize: Int = Config.MAX_RESULT_IMAGE_SIZE,
        isCropImage: Boolean
    ) {
        lifecycleScope.launch(Dispatchers.IO) {

            val fileName = "${System.currentTimeMillis()}.png"

            val result = imageUri.saveTo(
                this@AppCameraActivity,
                fileName,
                imageDir
            )

            if (isCropImage) {
                try {
                    val inputStream = contentResolver?.openInputStream(imageUri) ?: return@launch

                    val cropFileName = fileName.replace(".png", "_crop.png")

                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    val width = bitmap.width
                    val height = bitmap.height

                    val cropFile = File("${imageDir}/${cropFileName}")

                    bitmap.saveCompressBitmap(
                        context = this@AppCameraActivity,
                        file = cropFile
                    )

                    if (width > targetSize || height > targetSize) {

                        val widthScale = targetSize.toFloat() / width
                        val heightScale = targetSize.toFloat() / height

                        val scale = minOf(widthScale, heightScale)

                        val scaledWidth = (width * scale).toInt()
                        val scaledHeight = (height * scale).toInt()

                        bitmap.scale(scaledWidth, scaledHeight).saveCompressBitmap(
                            context = this@AppCameraActivity,
                            file = cropFile
                        )
                    }

                    inputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra(Constants.Intents.IMAGE_URI, result?.toUri())
                    }
                )
                finish()
            }
        }
    }


    private fun handleResult(
        selectedImageUri: Uri?,
        isCamera: Boolean
    ) {
        if (selectedImageUri == null) return
        viewModel.isCapturing.postValue(false)
        val intent = Intent(this, AppCropActivity::class.java).apply {
            putExtra(
                Constants.Intents.CROP_DATA, CropData(
                    uri = selectedImageUri,
                    cropTitle = getString(R.string.crop),
                    isCamera = isCamera
                )
            )
        }
        cropLauncher?.launch(intent)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        pickMedia = null
        activityResultLauncher = null
        cropLauncher = null
        super.onDestroy()
    }

    private fun setupUI() {
        binding.lifecycleOwner = this
    }

    private fun showDialogMessage(isOpenSetting: Boolean = false) {
        showDefaultDialog(
            supportFragmentManager,
            title = getString(R.string.camera_access_is_denied),
            message = getString(R.string.please_enable_camera_permission_in_settings_to_continue),
            positiveTitle = getString(R.string.permission),
            positiveCallback = {
                if (isOpenSetting) {
                    openSetting()
                } else {
                    activityResultLauncher?.launch(REQUIRED_PERMISSIONS)
                }
            },
            negativeTitle = getString(com.starnest.core.R.string.cancel),
            negativeCallback = {
            }
        )
    }

    private fun onPermissionsResult() {
        startCamera()
//        runDelayed(200) {
//            when (actionType) {
//                MathCameraActionType.ACTION_START_CAMERA -> return@runDelayed
//                MathCameraActionType.ACTION_TAKE_PICTURE -> takePhoto()
//                MathCameraActionType.ACTION_FLASH -> turnOnFlash()
//            }
//        }

    }

    private fun openSetting() {
        val intent = Intent().apply {
            setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            setData(Uri.fromParts("package", packageName, null))
        }

        settingLauncher?.launch(intent)
    }

    @SuppressLint("RestrictedApi")
    private fun setupAction() {
        binding.apply {
            ivTake.debounceClick {
                checkPermission(
                    actionType = CameraActionType.ACTION_TAKE_PICTURE,
                    action = ::takePhoto
                )
            }

            ivFlash.debounceClick {
                checkPermission(
                    actionType = CameraActionType.ACTION_FLASH,
                    action = ::turnOnFlash
                )
            }

            ivGallery.debounceClick {
                pickMedia?.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            toolbar.backButton.debounceClick {
                finish()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun turnOnFlash() {
        val currentState = !(viewModel.isFlashTurnOn.value ?: false)
        imageCapture?.camera?.cameraControl?.enableTorch(currentState)
        viewModel.isFlashTurnOn.postValue(currentState)
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable template capture use case
        val imageCapture = imageCapture ?: return

        viewModel.isCapturing.postValue(true)


        val fileName = "${System.currentTimeMillis()}.png"

        val file = File(imageDir, fileName)

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    exc.printStackTrace()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    handleOnImageSaved(output, file)
                }
            }
        )

    }

    fun handleOnImageSaved(output: ImageCapture.OutputFileResults, file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cropBounder = Rect(0, 0, binding.preview.width, binding.preview.height)

            val savedUri = output.savedUri ?: return@launch

            val bitmap = getCorrectlyOrientedBitmapFromUri(
                this@AppCameraActivity,
                savedUri
            ) ?: return@launch

            val scale = bitmap.width.toFloat() / binding.preview.width

            val scaledLeft = (cropBounder.left * scale).toInt()
            val scaledTop = (cropBounder.top * scale).toInt()
            val scaledWidth =
                minOf((cropBounder.width() * scale).toInt(), bitmap.width - scaledLeft)
            val scaledHeight =
                minOf((cropBounder.height() * scale).toInt(), bitmap.height - scaledTop)

            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                scaledLeft,
                scaledTop,
                scaledWidth,
                scaledHeight,
            )

            viewModel.resultUri = croppedBitmap.saveCompressBitmap(
                this@AppCameraActivity,
                file,
                quality = 100
            ).toUri()

            withContext(Dispatchers.Main) {
                handleResult(viewModel.resultUri, true)
            }
        }
    }

    private fun startCamera() {

        cameraProviderFuture?.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture?.get() ?: return@addListener

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.preview.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

            val currentImageCapture = imageCapture ?: return@addListener

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val viewPort = ViewPort.Builder(
                Rational(
                    binding.preview.width,
                    binding.preview.height
                ), binding.preview.display?.rotation ?: Surface.ROTATION_0
            ).build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(currentImageCapture)
                .setViewPort(viewPort)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, useCaseGroup
                )

            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun checkPermission(
        actionType: CameraActionType,
        action: () -> Unit
    ) {
        this.actionType = actionType
        if (allPermissionsGranted()) {
            action()
        } else {
            requestPermissions()
        }

    }

    private fun requestPermissions() {
        when {
            allPermissionsGranted() -> {
                onPermissionsResult()
            }

            REQUIRED_PERMISSIONS.map(::shouldShowRequestPermissionRationale).any { it } -> {
                showDialogMessage()
            }

            else -> {
                // You can directly ask for the permission.
                activityResultLauncher?.launch(REQUIRED_PERMISSIONS)
            }
        }

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCorrectlyOrientedBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val exifInputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(exifInputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        exifInputStream.close()

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}