package com.philkes.notallyx.data.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val TAG = "ApiClient"

    // ==================== CONFIGURATION SERVER ====================

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/api/v1/"

    object ServerPresets {
        const val EMULATOR = "http://10.0.2.2:8000/api/v1/"
        const val LOCALHOST = "http://localhost:8000/api/v1/"
        const val LOCAL_NETWORK = "http://192.168.1.100:8000/api/v1/"
        // const val PRODUCTION = "https://your-domain.com/api/v1/"
    }

    // ==================== TIMEOUT CONFIGURATION ====================
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT = 300L
    private const val WRITE_TIMEOUT = 300L

    // ==================== PRIVATE FIELDS ====================

    @Volatile private var retrofit: Retrofit? = null

    @Volatile private var noteAIService: NoteAIService? = null

    private var currentBaseUrl: String = DEFAULT_BASE_URL

    // ==================== LOGGING INTERCEPTOR ====================

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor { message -> Log.d(TAG, message) }
            .apply { level = HttpLoggingInterceptor.Level.BODY }
    }

    // ==================== OKHTTP CLIENT ====================

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val request =
                    original
                        .newBuilder()
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .method(original.method, original.body)
                        .build()
                chain.proceed(request)
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ==================== RETROFIT INSTANCE ====================

    private fun createRetrofit(baseUrl: String): Retrofit {
        val gson = Gson()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // ==================== PUBLIC API ====================

    fun getService(): NoteAIService {
        return noteAIService
            ?: synchronized(this) { noteAIService ?: createService().also { noteAIService = it } }
    }

    private fun createService(): NoteAIService {
        val retrofitInstance = retrofit ?: createRetrofit(currentBaseUrl).also { retrofit = it }
        return retrofitInstance.create(NoteAIService::class.java)
    }

    fun setBaseUrl(newBaseUrl: String) {
        synchronized(this) {
            if (currentBaseUrl != newBaseUrl) {
                currentBaseUrl = newBaseUrl
                retrofit = null
                noteAIService = null
                Log.d(TAG, "Base URL changed to: $newBaseUrl")
            }
        }
    }

    fun getCurrentBaseUrl(): String = currentBaseUrl

    fun reset() {
        synchronized(this) {
            retrofit = null
            noteAIService = null
        }
    }

    suspend fun checkConnection(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(5000L) {
                    val response = getService().getUserNotes("test", limit = 1)
                    response.isSuccessful || response.code() == 404
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Connection check timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Connection check failed: ${e.message}")
            false
        }
    }
}

fun Context.getConfiguredApiClient(): NoteAIService {
    val prefs = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    val savedUrl = prefs.getString("server_url", null)
    if (savedUrl != null) {
        ApiClient.setBaseUrl(savedUrl)
    }
    return ApiClient.getService()
}

fun Context.saveServerUrl(url: String) {
    getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("server_url", url)
        .apply()
    ApiClient.setBaseUrl(url)
}
