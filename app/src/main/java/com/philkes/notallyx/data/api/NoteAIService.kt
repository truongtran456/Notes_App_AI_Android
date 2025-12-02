package com.philkes.notallyx.data.api

import com.philkes.notallyx.data.api.models.AsyncJobResponse
import com.philkes.notallyx.data.api.models.JobStatusResponse
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.api.models.UserNotesResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit service interface cho Note AI Backend API Base URL: http://<server>:8000/api/v1/ */
interface NoteAIService {

    // ==================== SYNCHRONOUS ENDPOINTS ====================

    @FormUrlEncoded
    @POST("summarize")
    suspend fun summarizeText(
        @Field("note") note: String,
        @Field("user_id") userId: String? = null,
        @Field("note_id") noteId: String? = null,
    ): Response<SummaryResponse>

    @Multipart
    @POST("process")
    suspend fun processFile(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody? = null,
        @Part("note_id") noteId: RequestBody? = null,
    ): Response<SummaryResponse>

    @FormUrlEncoded
    @POST("process")
    suspend fun processText(
        @Field("text") text: String,
        @Field("user_id") userId: String? = null,
        @Field("note_id") noteId: String? = null,
    ): Response<SummaryResponse>

    @Multipart
    @POST("process/combined")
    suspend fun processCombined(
        @Part("text_note") textNote: RequestBody?,
        @Part files: List<MultipartBody.Part>,
        @Part("user_id") userId: RequestBody? = null,
        @Part("note_id") noteId: RequestBody? = null,
    ): Response<SummaryResponse>

    // ==================== ASYNCHRONOUS ENDPOINTS ====================

    @Multipart
    @POST("process/async")
    suspend fun processFileAsync(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody? = null,
        @Part("note_id") noteId: RequestBody? = null,
    ): Response<AsyncJobResponse>

    @FormUrlEncoded
    @POST("process/async")
    suspend fun processTextAsync(
        @Field("text") text: String,
        @Field("user_id") userId: String? = null,
        @Field("note_id") noteId: String? = null,
    ): Response<AsyncJobResponse>

    @GET("jobs/{job_id}/status")
    suspend fun getJobStatus(@Path("job_id") jobId: String): Response<JobStatusResponse>

    @GET("jobs/{job_id}/result")
    suspend fun getJobResult(@Path("job_id") jobId: String): Response<SummaryResponse>

    // ==================== HISTORY ENDPOINTS ====================

    @GET("users/{user_id}/notes")
    suspend fun getUserNotes(
        @Path("user_id") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("file_type") fileType: String? = null,
    ): Response<UserNotesResponse>

    @GET("users/{user_id}/notes/search")
    suspend fun searchUserNotes(
        @Path("user_id") userId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<UserNotesResponse>

    @GET("notes/{note_id}")
    suspend fun getNoteById(
        @Path("note_id") noteId: String,
        @Query("user_id") userId: String? = null,
    ): Response<SummaryResponse>

    @DELETE("notes/{note_id}")
    suspend fun deleteNote(@Path("note_id") noteId: String): Response<Map<String, Any>>
}
