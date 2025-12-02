package com.philkes.notallyx.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.philkes.notallyx.data.api.ApiClient
import com.philkes.notallyx.data.api.NoteAIService
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.JobStatus
import com.philkes.notallyx.data.api.models.NoteHistory
import com.philkes.notallyx.data.api.models.SummaryResponse
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AIRepository(private val context: Context) {

    companion object {
        private const val TAG = "AIRepository"
        private const val POLLING_INTERVAL_MS = 1500L
        private const val MAX_POLLING_ATTEMPTS = 200
    }

    private val service: NoteAIService = ApiClient.getService()

    // ==================== CACHE CHECK ====================
    private suspend fun getNoteFromCache(userId: String?, noteId: String?): SummaryResponse? =
        withContext(Dispatchers.IO) {
            if (userId.isNullOrBlank() || noteId.isNullOrBlank()) {
                return@withContext null
            }

            try {
                val response = service.getNoteById(noteId, userId)
                if (response.isSuccessful && response.body() != null) {
                    val cachedNote = response.body()!!
                    if (
                        !cachedNote.summary.isNullOrBlank() ||
                            !cachedNote.processedText.isNullOrBlank()
                    ) {
                        Log.d(TAG, "Found cached note: $noteId")
                        return@withContext cachedNote
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Cache check failed (will call API): ${e.message}")
            }
            null
        }

    // ==================== SUMMARIZE TEXT ====================

    suspend fun summarizeNote(
        noteText: String,
        userId: String? = null,
        noteId: String? = null,
        useCache: Boolean = true,
    ): AIResult<SummaryResponse> =
        withContext(Dispatchers.IO) {
            try {
                if (useCache) {
                    val cached = getNoteFromCache(userId, noteId)
                    if (cached != null) {
                        Log.d(TAG, "Using cached result for note: $noteId")
                        return@withContext AIResult.Success(cached)
                    }
                }

                Log.d(TAG, "Summarizing note: ${noteText.take(100)}...")

                val response =
                    service.summarizeText(note = noteText, userId = userId, noteId = noteId)

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Summarize success")
                    AIResult.Success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Summarize failed: ${response.code()} - $errorMsg")
                    AIResult.Error(errorMsg, response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Summarize exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            }
        }

    // ==================== PROCESS FILE (SYNC) ====================

    suspend fun processFile(
        fileUri: Uri,
        userId: String? = null,
        noteId: String? = null,
    ): AIResult<SummaryResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing file: $fileUri")

                val file = uriToTempFile(fileUri)
                val mimeType =
                    context.contentResolver.getType(fileUri) ?: "application/octet-stream"

                val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val userIdPart = userId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val noteIdPart = noteId?.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = service.processFile(filePart, userIdPart, noteIdPart)

                // Cleanup temp file
                file.delete()

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Process file success")
                    AIResult.Success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Process file failed: ${response.code()} - $errorMsg")
                    AIResult.Error(errorMsg, response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process file exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            }
        }

    // ==================== PROCESS FILE (ASYNC WITH PROGRESS) ====================

    data class ProcessingProgress(val progress: Int, val stage: String?, val status: JobStatus)

    fun processFileWithProgress(
        fileUri: Uri,
        userId: String? = null,
        noteId: String? = null,
    ): Flow<AIResult<Any>> =
        flow {
                try {
                    Log.d(TAG, "Processing file async: $fileUri")

                    emit(AIResult.Loading)

                    val file = uriToTempFile(fileUri)
                    val mimeType =
                        context.contentResolver.getType(fileUri) ?: "application/octet-stream"

                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    val userIdPart = userId?.toRequestBody("text/plain".toMediaTypeOrNull())
                    val noteIdPart = noteId?.toRequestBody("text/plain".toMediaTypeOrNull())

                    val asyncResponse = service.processFileAsync(filePart, userIdPart, noteIdPart)

                    // Cleanup temp file
                    file.delete()

                    if (!asyncResponse.isSuccessful || asyncResponse.body() == null) {
                        emit(AIResult.Error("Failed to start processing: ${asyncResponse.code()}"))
                        return@flow
                    }

                    val jobId = asyncResponse.body()!!.jobId
                    Log.d(TAG, "Job started: $jobId")

                    var attempts = 0
                    while (attempts < MAX_POLLING_ATTEMPTS) {
                        delay(POLLING_INTERVAL_MS)
                        attempts++

                        val statusResponse = service.getJobStatus(jobId)
                        if (!statusResponse.isSuccessful || statusResponse.body() == null) {
                            continue
                        }

                        val status = statusResponse.body()!!
                        val jobStatus = JobStatus.fromString(status.status)

                        emit(
                            AIResult.Success(
                                ProcessingProgress(
                                    progress = status.progress ?: 0,
                                    stage = status.stage,
                                    status = jobStatus,
                                )
                            )
                        )

                        when (jobStatus) {
                            JobStatus.COMPLETED -> {
                                // L?y k?t qu?
                                val resultResponse = service.getJobResult(jobId)
                                if (resultResponse.isSuccessful && resultResponse.body() != null) {
                                    emit(AIResult.Success(resultResponse.body()!!))
                                } else {
                                    emit(AIResult.Error("Failed to get result"))
                                }
                                return@flow
                            }
                            JobStatus.FAILED -> {
                                emit(AIResult.Error(status.error ?: "Processing failed"))
                                return@flow
                            }
                            else -> {
                                // Continue polling
                            }
                        }
                    }

                    emit(AIResult.Error("Processing timeout"))
                } catch (e: Exception) {
                    Log.e(TAG, "Process file async exception: ${e.message}", e)
                    emit(AIResult.Error(e.message ?: "Network error"))
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun processFileAsync(
        fileUri: Uri,
        userId: String? = null,
        noteId: String? = null,
        onProgress: ((progress: Int, stage: String?) -> Unit)? = null,
    ): AIResult<SummaryResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing file async: $fileUri")

                val file = uriToTempFile(fileUri)
                val mimeType =
                    context.contentResolver.getType(fileUri) ?: "application/octet-stream"

                val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val userIdPart = userId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val noteIdPart = noteId?.toRequestBody("text/plain".toMediaTypeOrNull())

                val asyncResponse = service.processFileAsync(filePart, userIdPart, noteIdPart)

                // Cleanup temp file
                file.delete()

                if (!asyncResponse.isSuccessful || asyncResponse.body() == null) {
                    return@withContext AIResult.Error(
                        "Failed to start processing: ${asyncResponse.code()}"
                    )
                }

                val jobId = asyncResponse.body()!!.jobId
                Log.d(TAG, "Job started: $jobId")

                var attempts = 0
                while (attempts < MAX_POLLING_ATTEMPTS) {
                    delay(POLLING_INTERVAL_MS)
                    attempts++

                    val statusResponse = service.getJobStatus(jobId)
                    if (!statusResponse.isSuccessful || statusResponse.body() == null) {
                        continue
                    }

                    val status = statusResponse.body()!!
                    val jobStatus = JobStatus.fromString(status.status)

                    onProgress?.invoke(status.progress ?: 0, status.stage)

                    when (jobStatus) {
                        JobStatus.COMPLETED -> {
                            val resultResponse = service.getJobResult(jobId)
                            return@withContext if (
                                resultResponse.isSuccessful && resultResponse.body() != null
                            ) {
                                AIResult.Success(resultResponse.body()!!)
                            } else {
                                AIResult.Error("Failed to get result")
                            }
                        }
                        JobStatus.FAILED -> {
                            return@withContext AIResult.Error(status.error ?: "Processing failed")
                        }
                        else -> {
                            // Continue polling
                        }
                    }
                }

                AIResult.Error("Processing timeout")
            } catch (e: Exception) {
                Log.e(TAG, "Process file async exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            }
        }

    suspend fun processCombinedInputs(
        noteText: String?,
        attachments: List<Uri>,
        userId: String?,
        noteId: String?,
        useCache: Boolean = true,
    ): AIResult<SummaryResponse> =
        withContext(Dispatchers.IO) {
            if ((noteText.isNullOrBlank()) && attachments.isEmpty()) {
                return@withContext AIResult.Error("Please provide text or at least one file")
            }

            if (useCache) {
                val cached = getNoteFromCache(userId, noteId)
                if (cached != null) {
                    Log.d(TAG, "Using cached result for combined note: $noteId")
                    return@withContext AIResult.Success(cached)
                }
            }

            val tempFiles = mutableListOf<File>()
            try {
                val textBody =
                    noteText
                        ?.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val userIdBody =
                    userId
                        ?.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val noteIdBody =
                    noteId
                        ?.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull())

                val fileParts =
                    attachments.map { uri ->
                        val file = uriToTempFile(uri).also { tempFiles.add(it) }
                        val mimeType =
                            context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("files", file.name, requestFile)
                    }

                Log.d(TAG, "Calling API for combined processing: noteId=$noteId")
                val response = service.processCombined(textBody, fileParts, userIdBody, noteIdBody)

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Combined processing success")
                    AIResult.Success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    AIResult.Error(errorMsg, response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "processCombinedInputs exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            } finally {
                tempFiles.forEach { file ->
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                }
            }
        }

    // ==================== HISTORY ====================

    suspend fun getUserHistory(
        userId: String,
        limit: Int = 50,
        offset: Int = 0,
        fileType: String? = null,
    ): AIResult<List<NoteHistory>> =
        withContext(Dispatchers.IO) {
            try {
                val response = service.getUserNotes(userId, limit, offset, fileType)

                if (response.isSuccessful && response.body() != null) {
                    AIResult.Success(response.body()!!.notes)
                } else {
                    AIResult.Error("Failed to get history: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get history exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            }
        }

    suspend fun searchHistory(userId: String, query: String): AIResult<List<NoteHistory>> =
        withContext(Dispatchers.IO) {
            try {
                val response = service.searchUserNotes(userId, query)

                if (response.isSuccessful && response.body() != null) {
                    AIResult.Success(response.body()!!.notes)
                } else {
                    AIResult.Error("Search failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search exception: ${e.message}", e)
                AIResult.Error(e.message ?: "Network error")
            }
        }

    // ==================== UTILITY ====================
    private fun uriToTempFile(uri: Uri): File {
        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val fileName = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}"
        val tempFile = File(context.cacheDir, fileName)

        FileOutputStream(tempFile).use { output ->
            inputStream.use { input -> input.copyTo(output) }
        }

        return tempFile
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }

        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }

        return name
    }

    suspend fun checkServerConnection(): Boolean {
        return ApiClient.checkConnection()
    }
}
