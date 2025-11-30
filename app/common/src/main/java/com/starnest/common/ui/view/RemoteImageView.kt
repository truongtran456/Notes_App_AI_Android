package com.starnest.common.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Size
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.starnest.common.R
import com.starnest.common.databinding.ItemRemoteImageViewBinding
import com.starnest.common.extension.isAvailable
import com.starnest.common.ui.view.glide.svg.SvgLoaderListener
import com.starnest.common.ui.view.glide.svg.SvgSoftwareLayerSetter
import com.starnest.common.ui.view.glide.svg.asSvg
import com.starnest.core.extension.gone
import com.starnest.core.extension.show
import com.starnest.core.ui.widget.AbstractView
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.math.min


@AndroidEntryPoint
class RemoteImageView(context: Context, attrs: AttributeSet?) : AbstractView(context, attrs) {

    override fun layoutId(): Int = R.layout.item_remote_image_view

    override fun viewBinding() = binding as ItemRemoteImageViewBinding

    enum class LoadingSize {
        SMALL, MEDIUM, LARGE
    }

    private var isFullImageLoaded = false


    override fun viewInitialized() {
    }

    fun setBitmap(bitmap: Bitmap) {
        viewBinding().imageView.setImageBitmap(bitmap)
    }

    fun loadImageWithResId(resId: Int) {
        viewBinding().imageView.setImageResource(resId)
    }

    fun loadImageWithFitCenter(uri: Uri?, loadingSize: LoadingSize = LoadingSize.MEDIUM, size: Size? = null) {
        setupLoadingSize(loadingSize)
        uri?.let { load(it, size, false) } ?: viewBinding().imageView.setImageDrawable(null)
    }

    fun loadImage(uri: Uri?, loadingSize: LoadingSize = LoadingSize.MEDIUM, size: Size? = null) {
        setupLoadingSize(loadingSize)
        uri?.let { load(it, size) } ?: viewBinding().imageView.setImageDrawable(null)
    }


    fun loadImageWithFile(file: File?, loadingSize: LoadingSize = LoadingSize.MEDIUM, size: Size? = null) {
        setupLoadingSize(loadingSize)
        file?.let { load(it, size) }
    }

    fun loadImageWithUri(
        uri: Uri?,
        cornerRadius: Int?,
        placeHolder: Drawable?,
        isCenterInside: Boolean?
    ) {
        val loadingSize = when (min(height, width).toFloat()) {
            in context.resources.getDimension(com.starnest.core.R.dimen.dp_144)
                    ..Float.MAX_VALUE -> LoadingSize.LARGE

            in context.resources.getDimension(
                com.starnest.core.R.dimen.dp_88
            )..context.resources.getDimension(
                com.starnest.core.R.dimen.dp_144
            ) -> LoadingSize.MEDIUM

            else -> LoadingSize.SMALL
        }
        setupLoadingSize(loadingSize)
        uri?.let { load(it, cornerRadius, placeHolder, isCenterInside) }
    }

    fun setDrawableRes(drawable: Drawable) {
        viewBinding().imageView.setImageDrawable(drawable)
    }

    private fun load(
        uri: Uri,
        cornerRadius: Int?,
        placeHolder: Drawable?,
        isCenterInside: Boolean?
    ) {
        val ivRemote = viewBinding().imageView
        showLoading()
        val glide = Glide.with(context).load(uri)

        glide
            .run { placeHolder?.let(::placeholder) ?: this }
            .listener(
                object : RequestListener<Drawable> {

                    override fun onLoadFailed(
                        p0: GlideException?,
                        p1: Any?,
                        p2: Target<Drawable>,
                        p3: Boolean
                    ): Boolean {
                        hideLoading()
                        return false
                    }

                    override fun onResourceReady(
                        p0: Drawable,
                        p1: Any,
                        p2: Target<Drawable>?,
                        p3: DataSource,
                        p4: Boolean
                    ): Boolean {
                        hideLoading()
                        return false
                    }
                }
            )
            .run {
                when {
                    isCenterInside == true -> transform(CenterInside())

                    (cornerRadius
                        ?: 0).toFloat() >= resources.getDimensionPixelSize(com.starnest.core.R.dimen.dp_50) -> circleCrop()

                    else -> cornerRadius?.let(::RoundedCorners)?.let {
                        transform(CenterCrop(), it)
                    } ?: transform(CenterCrop())
                }
            }
            .into(ivRemote)
    }

    private fun load(uri: Uri, size: Size? = null, isCenterCrop: Boolean = true) {
        showLoading()

        val isSvg = uri.path?.endsWith(".svg", ignoreCase = true) == true

        if (isSvg) {
            val glide = size?.let {
                Glide.with(context)
                    .asSvg()
                    .load(uri)
                    .override(it.width, it.height)
            } ?: Glide.with(context)
                .asSvg()
                .load(uri)

            val listener = SvgSoftwareLayerSetter(object : SvgLoaderListener {
                override fun onLoadFailed() {
                    hideLoading()
                }

                override fun onResourceReady() {
                    hideLoading()
                }
            })

            val builder = if (isCenterCrop) glide.centerCrop() else glide.fitCenter()

            builder
                .listener(listener)
                .into(viewBinding().imageView)
        } else {
            val glide = size?.let {
                Glide
                    .with(context)
                    .load(uri)
                    .override(it.width, it.height)
            } ?: Glide
                .with(context).load(uri)

            val listener = object : RequestListener<Drawable> {

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }
            }

            val builder = if (isCenterCrop) glide.centerCrop() else glide.fitCenter()

            builder
                .listener(listener)
                .into(viewBinding().imageView)
        }
    }

    private fun load(file: File, size: Size? = null) {
        showLoading()

        val isSvg = file.path.endsWith(".svg", ignoreCase = true) == true

        if (isSvg) {
            val glide = size?.let {
                Glide.with(context)
                    .asSvg()
                    .load(file)
                    .override(it.width, it.height)
            } ?: Glide.with(context)
                .asSvg()
                .load(file)

            val listener = SvgSoftwareLayerSetter(object : SvgLoaderListener {
                override fun onLoadFailed() {
                    hideLoading()
                }

                override fun onResourceReady() {
                    hideLoading()
                }
            })

            glide.centerCrop()
                .listener(listener)
                .into(viewBinding().imageView)
        } else {
            val glide = size?.let {
                Glide
                    .with(context)
                    .load(file)
                    .override(it.width, it.height)
            } ?: Glide
                .with(context).load(file)

            val listener = object : RequestListener<Drawable> {

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    hideLoading()
                    return false
                }
            }

            glide
                .centerCrop()
                .listener(listener)
                .into(viewBinding().imageView)
        }
    }

    private fun showLoading() {
        if(!context.isAvailable()) {
            return
        }
        viewBinding().ivLoading.show()
        Glide.with(context).load(com.starnest.resources.R.drawable.ic_cupertino_indicator_small)
            .into(viewBinding().ivLoading)
    }

    fun hideLoading() {
        viewBinding().tvProgress.gone()
        viewBinding().ivLoading.gone()
    }

    private fun setupLoadingSize(size: LoadingSize) {
        val layoutParams = viewBinding().ivLoading.layoutParams
        when (size) {
            LoadingSize.SMALL -> {
                layoutParams.height =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_20).toInt()
                layoutParams.width =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_20).toInt()
            }

            LoadingSize.MEDIUM -> {
                layoutParams.height =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_44).toInt()
                layoutParams.width =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_44).toInt()
            }

            LoadingSize.LARGE -> {
                layoutParams.height =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_72).toInt()
                layoutParams.width =
                    context.resources.getDimension(com.starnest.core.R.dimen.dp_72).toInt()
            }
        }

        viewBinding().ivLoading.layoutParams = layoutParams
    }

    fun setScaleType(scaleType: ImageView.ScaleType) {
        viewBinding().imageView.scaleType = scaleType
    }
}