package com.starnest.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.starnest.core.extension.safeResume
import com.starnest.core.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager


object DownloadUtil {

    suspend fun downloadBitmap(context: Context, uri: Uri): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            Glide.with(context).asBitmap().load(uri).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap, transition: Transition<in Bitmap>?
                ) {
                    continuation.safeResume(resource)

                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    continuation.safeResume(null)
                }
            })
        }
    }

    fun downloadBitmap(context: Context, uri: Uri, callback: (Size?) -> Unit) {
        Glide.with(context).asBitmap().load(uri).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(
                resource: Bitmap, transition: Transition<in Bitmap>?,
            ) {
                val size = Size(
                    resource.width,
                    resource.height
                )
                callback.invoke(size)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                callback.invoke(null)
            }
        })
    }

    @SuppressLint("CheckResult")
    suspend fun downloadBitmap(
        context: Context, url: String,
        size: Size? = null,
    ): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            Glide.with(context).asBitmap().load(url)
                .apply {
                    if (size != null) {
                        override(size.width, size.height)
                    }
                }.into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap, transition: Transition<in Bitmap>?
                    ) {
                        continuation.safeResume(resource)

                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        continuation.safeResume(null)
                    }
                })
        }
    }

    suspend fun download(
        urlString: String,
        dest: String,
        progress: (Int) -> Unit
    ): String? {
        return withContext(Dispatchers.IO) {
            var count: Int
            try {
                HttpsURLConnection.setDefaultHostnameVerifier { hostname, session -> true }
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf<X509TrustManager>(object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate?>?,
                        authType: String?
                    ) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate?>?,
                        authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate?> {
                        return arrayOfNulls<X509Certificate>(0)
                    }
                }), SecureRandom())

                HttpsURLConnection.setDefaultSSLSocketFactory(
                    context.socketFactory
                )

                val url = URL(urlString)
                val connection = url.openConnection()

                connection.connect()

                val contentLength = connection.contentLength

                val input = BufferedInputStream(url.openStream(), 8192)

                val output = FileOutputStream(dest)
                val data = ByteArray(1024)
                var total = 0L
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()

                    withContext(Dispatchers.Main) {
                        progress.invoke((total * 100 / contentLength).toInt())
                    }

                    output.write(data, 0, count)
                }

                output.flush()

                output.close()
                input.close()

                return@withContext dest

            } catch (e: Exception) {
                FileUtils.deleteFile(dest)
                e.printStackTrace()

                return@withContext null
            }
        }
    }



}


