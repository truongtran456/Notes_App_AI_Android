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
    @SerializedName("vocab_story") val vocabStory: VocabStory? = null,
    @SerializedName("vocab_mcqs") val vocabMcqs: List<VocabQuiz>? = null,
    @SerializedName("flashcards") val flashcards: List<Flashcard>? = null,
    @SerializedName("mindmap") val mindmap: MindmapBundle? = null,
    @SerializedName("summary_table") val summaryTable: List<VocabSummaryRow>? = null,
    @SerializedName("cloze_tests") val clozeTests: List<ClozeTest>? = null,
    @SerializedName("match_pairs") val matchPairs: List<MatchPair>? = null,
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
    @SerializedName("vocab_story") val vocabStory: VocabStory? = null,
    @SerializedName("vocab_mcqs") val vocabMcqs: List<VocabQuiz>? = null,
    @SerializedName("flashcards") val flashcards: List<Flashcard>? = null,
    @SerializedName("mindmap") val mindmap: MindmapBundle? = null,
    @SerializedName("summary_table") val summaryTable: List<VocabSummaryRow>? = null,
    @SerializedName("cloze_tests") val clozeTests: List<ClozeTest>? = null,
    @SerializedName("match_pairs") val matchPairs: List<MatchPair>? = null,
)

data class VocabStory(
    @SerializedName("title") val title: String? = null,
    @SerializedName("paragraphs") val paragraphs: List<String>? = null,
    @SerializedName("used_words") val usedWords: List<VocabUsedWord>? = null,
)

data class VocabUsedWord(
    @SerializedName("word") val word: String? = null,
    @SerializedName("bolded") val bolded: Boolean? = null,
)

data class VocabQuiz(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("set_id") val setId: Int? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("question_type") val questionType: String? = null,
    @SerializedName("vocab_target") val vocabTarget: String? = null,
    @SerializedName("question") val question: String? = null,
    @SerializedName("options") val options: Map<String, String>? = null,
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("explanation") val explanation: String? = null,
    @SerializedName("when_wrong") val whenWrong: String? = null,
)

data class Flashcard(
    @SerializedName("word") val word: String? = null,
    @SerializedName("front") val front: String? = null,
    @SerializedName("back") val back: FlashcardBack? = null,
    @SerializedName("srs_schedule") val srsSchedule: SRSSchedule? = null,
)

data class FlashcardBack(
    @SerializedName("meaning") val meaning: String? = null,
    @SerializedName("example") val example: String? = null,
    @SerializedName("usage_note") val usageNote: String? = null,
    @SerializedName("synonyms") val synonyms: List<String>? = null,
    @SerializedName("antonyms") val antonyms: List<String>? = null,
    @SerializedName("quick_tip") val quickTip: String? = null,
)

data class SRSSchedule(
    @SerializedName("initial_prompt") val initialPrompt: String? = null,
    @SerializedName("intervals") val intervals: List<Int>? = null,
    @SerializedName("recall_task") val recallTask: String? = null,
)

data class MindmapBundle(
    @SerializedName("by_topic") val byTopic: List<MindmapGroup>? = null,
    @SerializedName("by_difficulty") val byDifficulty: List<MindmapDifficultyGroup>? = null,
    @SerializedName("by_pos") val byPos: List<MindmapPosGroup>? = null,
    @SerializedName("by_relation") val byRelation: List<MindmapRelationGroup>? = null,
)

data class MindmapGroup(
    @SerializedName("topic") val topic: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("words") val words: List<String>? = null,
)

data class MindmapDifficultyGroup(
    @SerializedName("level") val level: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("words") val words: List<String>? = null,
)

data class MindmapPosGroup(
    @SerializedName("pos") val pos: String? = null,
    @SerializedName("words") val words: List<String>? = null,
)

data class MindmapRelationGroup(
    @SerializedName("group_name") val groupName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("words") val words: List<String>? = null,
    @SerializedName("clusters") val clusters: List<List<String>>? = null, // For synonyms
    @SerializedName("pairs") val pairs: List<List<String>>? = null, // For antonyms
)

data class VocabSummaryRow(
    @SerializedName("word") val word: String? = null,
    @SerializedName("translation") val translation: String? = null,
    @SerializedName("phonetic") val phonetic: String? = null,
    @SerializedName("part_of_speech") val partOfSpeech: String? = null,
    @SerializedName("definition") val definition: String? = null,
    @SerializedName("usage_note") val usageNote: String? = null,
    @SerializedName("common_structures") val commonStructures: List<String>? = null,
    @SerializedName("collocations") val collocations: List<String>? = null,
)

data class ClozeTest(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("set_id") val setId: Int? = null,
    @SerializedName("vocab") val vocab: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("paragraph") val paragraph: String? = null,
    @SerializedName("blanks") val blanks: List<ClozeBlank>? = null,
)

data class ClozeBlank(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("explanation") val explanation: String? = null,
    @SerializedName("on_correct_example") val onCorrectExample: String? = null,
)

data class MatchPair(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("set_id") val setId: Int? = null,
    @SerializedName("word") val word: String? = null,
    @SerializedName("meaning") val meaning: String? = null,
    @SerializedName("hint") val hint: String? = null,
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

data class TranslateResponse(
    @SerializedName("original_text") val originalText: String? = null,
    @SerializedName("translated_text") val translatedText: String? = null,
    @SerializedName("target_language") val targetLanguage: String? = null,
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
