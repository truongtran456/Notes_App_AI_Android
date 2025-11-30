package com.starnest.common.app

import com.starnest.core.app.AbstractApplication
import com.starnest.core.extension.toast
import com.starnest.data.common.datasource.AppSharePrefs
import com.starnest.resources.R
import javax.inject.Inject

abstract class BaseApp : AbstractApplication() {
    @Inject
    lateinit var appSharePrefs: AppSharePrefs

    fun reportGpt() {
        toast(getString(R.string.thanks_for_your_feedback))
    }
}