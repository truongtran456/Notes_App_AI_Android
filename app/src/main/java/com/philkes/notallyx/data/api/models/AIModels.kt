package com.philkes.notallyx.data.api.models

import com.google.gson.annotations.SerializedName

data class SummaryResponse(
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("summaries") val summaries: Summaries? = null,
    @SerializedName("questions") val questions: List<Question>? = null,
    @SerializedName("mcqs") val mcqs: MCQs? = null,
    @SerializedName("review") val review: Review? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("processed_text") val processedText: String? = null,
    @SerializedName("sources") val sources: List<AISource>? = null,
    @SerializedName("error") val error: String? = null,
)

data class AISource(
    @SerializedName("type") val type: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("raw_text") val rawText: String? = null,
    @SerializedName("processed_text") val processedText: String? = null,
    @SerializedName("review") val review: Review? = null,
    @SerializedName("error") val error: String? = null,
)

data class Summaries(
    @SerializedName("one_sentence") val oneSentence: String? = null,
    @SerializedName("short_paragraph") val shortParagraph: String? = null,
    @SerializedName("bullet_points") val bulletPoints: List<String>? = null,
)

data class Question(
    @SerializedName("question") val question: String,
    @SerializedName("answer") val answer: String,
)

data class MCQs(
    @SerializedName("easy") val easy: List<MCQ>? = null,
    @SerializedName("medium") val medium: List<MCQ>? = null,
    @SerializedName("hard") val hard: List<MCQ>? = null,
)

data class MCQ(
    @SerializedName("question") val question: String,
    @SerializedName("options") val options: Map<String, String>,
    @SerializedName("answer") val answer: String,
    @SerializedName("explanation") val explanation: String? = null,
)

data class Review(
    @SerializedName("valid") val valid: Boolean = true,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("error") val error: String? = null,
)

data class AsyncJobResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
)

data class JobStatusResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("stage") val stage: String? = null,
    @SerializedName("result") val result: SummaryResponse? = null,
    @SerializedName("error") val error: String? = null,
)

data class UserNotesResponse(
    @SerializedName("notes") val notes: List<NoteHistory>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
)

data class NoteHistory(
    @SerializedName("id") val id: String,
    @SerializedName("note_id") val noteId: String? = null,
    @SerializedName("file_type") val fileType: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("summaries") val summaries: Summaries? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

sealed class AIResult<out T> {
    data class Success<T>(val data: T) : AIResult<T>()

    data class Error(val message: String, val code: Int? = null) : AIResult<Nothing>()

    object Loading : AIResult<Nothing>()
}

enum class JobStatus(val value: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    companion object {
        fun fromString(value: String): JobStatus {
            return values().find { it.value == value } ?: PENDING
        }
    }
}
