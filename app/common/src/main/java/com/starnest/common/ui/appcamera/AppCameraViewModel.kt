package com.starnest.common.ui.appcamera

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import android.app.Application
import com.starnest.core.ui.viewmodel.TMVVMViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppCameraViewModel @Inject constructor(
    override val application: Application,
) : TMVVMViewModel(application) {
    val isFlashTurnOn = MutableLiveData(false)
    var isCapturing = MutableLiveData(false)

    var resultUri: Uri? = null
}