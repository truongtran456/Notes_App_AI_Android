package com.starnest.common.ui.dialog.picker

import android.app.Application
import com.starnest.core.ui.viewmodel.TMVVMViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MediaPickerViewModel@Inject constructor(
    override val application: Application,
) : TMVVMViewModel(application) {
}