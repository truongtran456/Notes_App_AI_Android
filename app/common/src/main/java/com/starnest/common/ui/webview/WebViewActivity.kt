package com.starnest.common.ui.webview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.starnest.common.R
import com.starnest.common.databinding.ActivityWebViewBinding
import com.starnest.core.base.activity.BaseActivity
import com.starnest.core.base.viewmodel.BaseViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WebViewActivity: BaseActivity<ActivityWebViewBinding, BaseViewModel>(
    BaseViewModel::class)  {

    companion object {
        const val LINK_URL = "LINK_URL"
    }

    override fun layoutId(): Int = R.layout.activity_web_view


    @SuppressLint("SetJavaScriptEnabled")
    override fun initialize() {
        with(binding) {
            webview.settings.javaScriptEnabled = true
            webview.clearCache(true)
            webview.setBackgroundColor(Color.TRANSPARENT)
            webview.settings.loadWithOverviewMode = true
            webview.settings.defaultFontSize = 10
            webview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webview.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progress.visibility = View.GONE
                }
            }

            val bundle: Bundle? = intent.extras
            bundle?.getString(LINK_URL)?.let {
                webview.loadUrl(it)
            }
        }
    }
}