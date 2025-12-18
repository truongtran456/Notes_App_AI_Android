package com.philkes.notallyx.presentation.activity.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.ClozeBlank
import com.philkes.notallyx.data.api.models.ClozeTest
import com.philkes.notallyx.data.api.models.Flashcard
import com.philkes.notallyx.data.api.models.MCQ
import com.philkes.notallyx.data.api.models.MatchPair
import com.philkes.notallyx.data.api.models.MindmapBundle
import com.philkes.notallyx.data.api.models.Question
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.api.models.VocabQuiz
import com.philkes.notallyx.data.api.models.VocabStory
import com.philkes.notallyx.data.api.models.VocabSummaryRow
import com.philkes.notallyx.data.preferences.AIUserPreferences
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.ActivityAiSummaryBinding
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class AISummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSummaryBinding
    private lateinit var aiRepository: AIRepository

    private var noteContent: String = ""
    private var noteId: Long = -1L
    private var backendNoteId: String? = null
    private var useProcessEndpoint: Boolean = false
    private var contentType: String? = null
    private var checkedVocabItems: String? = null
    private var currentMCQDifficulty: String = "easy"
    private var summaryResponse: SummaryResponse? = null
    private var initialSection: AISection = AISection.SUMMARY
    private var hasScrolledToInitialSection = false
    private var forceShowAllSections = false
    private var statsOnly = false
    private var pendingContentHash: String? = null

    // Original data for translation (to preserve format)
    private var vocabStoryOriginal: VocabStory? = null
    private var vocabMCQsOriginal: List<VocabQuiz>? = null
    private var flashcardsOriginal: List<Flashcard>? = null

    // Translation states for each vocab card
    private var vocabStoryTranslated: String? = null
    private var vocabStoryIsTranslated = false
    private var vocabMCQTranslated: String? = null
    private var vocabMCQIsTranslated = false
    private var flashcardsTranslated: String? = null
    private var flashcardsIsTranslated = false
    private var mindmapTranslated: String? = null
    private var mindmapIsTranslated = false
    private var summaryTableTranslated: String? = null
    // TTS cho phát âm
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    // State for flashcards
    private var flashcardIndex = 0
    private var flashcardFlipped = false

    // State for match pairs
    private var matchPairsState: MutableList<MatchPair>? = null
    private var matchPairsAll: MutableList<MatchPair>? = null
    private var matchPairsRevealed: MutableSet<Int> = mutableSetOf()
    private var matchFirstSelection: Int? = null

    // State for cloze answers
    private var clozeUserAnswers: MutableMap<Pair<Int, Int>, String> = mutableMapOf()

    // State for vocab MCQ quiz mode (checklist)
    private var vocabMCQAllQuestions: List<VocabQuiz>? = null
    private var vocabMCQShuffledQuestions: MutableList<VocabQuiz> = mutableListOf()
    private var vocabMCQCurrentIndex: Int = 0
    private var vocabMCQUserAnswers: MutableMap<Int, String> =
        mutableMapOf() // question index -> selected answer
    private var vocabMCQScore: Int = 0
    private var vocabMCQIsQuizMode: Boolean = false

    // State for cloze quiz mode (checklist)
    private var clozeAllQuestions: MutableList<Pair<ClozeTest, ClozeBlank>> = mutableListOf()
    private var clozeShuffledQuestions: MutableList<Pair<ClozeTest, ClozeBlank>> = mutableListOf()
    private var clozeCurrentIndex: Int = 0
    private var clozeUserAnswersQuiz: MutableMap<Int, String> =
        mutableMapOf() // question index -> user answer
    private var clozeScore: Int = 0
    private var clozeIsQuizMode: Boolean = false

    // State for match pairs quiz mode (theo t? v?ng)
    private var matchPairsScore: Int = 0 // S? t? ?ã hoàn thành (?ã match ?úng ít nh?t 1 l?n)
    private var matchPairsTotal: Int = 0 // T?ng s? t? unique trong note
    private var matchPairsCompleted: Boolean = false
    private var matchPairsWordsMatched: MutableSet<String> =
        mutableSetOf() // Các vocab ?ã hoàn thành
    private var matchPairsUniqueWords: Set<String> = emptySet() // T?t c? vocab unique

    // Progress chi ti?t cho t?ng vocab c?a Match Pairs (l?u vào SharedPreferences)
    private data class MatchPairVocabProgress(
        val vocab: String,
        var status: String = "pending", // "pending" | "completed"
        var attempts: Int = 0, // s? l?n th? (match sai + ?úng)
        var completedAt: Long? = null, // timestamp khi completed
    )

    private var matchPairsVocabProgress: MutableMap<String, MatchPairVocabProgress> = mutableMapOf()

    // Quiz completion tracking
    private var vocabQuizCompleted: Boolean = false
    private var clozeQuizCompleted: Boolean = false
    private var matchPairsQuizCompleted: Boolean = false

    // State for summary table (to get Vietnamese translations for match pairs)
    private var summaryTableState: List<VocabSummaryRow>? = null
    private var summaryTableIsTranslated = false

    companion object {
        const val EXTRA_NOTE_CONTENT = "note_content"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_BACKEND_NOTE_ID = "backend_note_id"
        const val EXTRA_INITIAL_SECTION = "initial_section"
        const val EXTRA_USE_PROCESS = "use_process_endpoint"
        const val EXTRA_CONTENT_TYPE = "content_type"
        const val EXTRA_CHECKED_VOCAB_ITEMS = "checked_vocab_items"
        private const val EXTRA_PRECOMPUTED_RESULT = "precomputed_result"
        private const val EXTRA_SHOW_ALL_SECTIONS = "show_all_sections"
        private const val EXTRA_STATS_ONLY = "stats_only"

        fun start(
            context: Context,
            noteContent: String,
            noteId: Long = -1L,
            initialSection: AISection = AISection.SUMMARY,
            backendNoteId: String? = null,
            useProcessEndpointForText: Boolean = false,
            contentType: String? = null,
            checkedVocabItems: String? = null,
        ) {
            val intent =
                Intent(context, AISummaryActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_CONTENT, noteContent)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                    backendNoteId?.let { putExtra(EXTRA_BACKEND_NOTE_ID, it) }
                    if (useProcessEndpointForText) {
                        putExtra(EXTRA_USE_PROCESS, true)
                    }
                    contentType?.let { putExtra(EXTRA_CONTENT_TYPE, it) }
                    checkedVocabItems?.let { putExtra(EXTRA_CHECKED_VOCAB_ITEMS, it) }
                }
            context.startActivity(intent)
        }

        fun startWithResult(
            context: Context,
            summaryResponse: SummaryResponse,
            noteId: Long = -1L,
            showAllSections: Boolean = false,
            initialSection: AISection = AISection.SUMMARY,
            isVocabMode: Boolean = false,
            statsOnly: Boolean = false,
        ) {
            val intent =
                Intent(context, AISummaryActivity::class.java).apply {
                    putExtra(EXTRA_PRECOMPUTED_RESULT, Gson().toJson(summaryResponse))
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_SHOW_ALL_SECTIONS, showAllSections)
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                    if (statsOnly) {
                        putExtra(EXTRA_STATS_ONLY, true)
                    }
                    if (isVocabMode) {
                        putExtra(EXTRA_USE_PROCESS, true)
                    }
                }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityAiSummaryBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Show loading by default to avoid black screen
            showLoading()

            aiRepository = AIRepository(this)
            // Init TTS (English) cho phát âm t? v?ng
            tts =
                TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val r = tts?.setLanguage(Locale.US)
                        ttsReady =
                            r != TextToSpeech.LANG_MISSING_DATA &&
                                r != TextToSpeech.LANG_NOT_SUPPORTED
                    } else {
                        ttsReady = false
                    }
                }

            extractIntentData()
            setupToolbar()
            setupClickListeners()
            setupTranslateButtons()

            android.util.Log.d(
                "AISummaryActivity",
                "onCreate: summaryResponse=${summaryResponse != null}, noteContent length=${noteContent.length}",
            )

            if (summaryResponse != null) {
                displayResults(summaryResponse!!)
            } else if (noteContent.isNotBlank()) {
                summarizeNote()
            } else {
                showError(getString(R.string.ai_error_empty_note))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AISummaryActivity", "onCreate: exception - ${e.message}", e)
            // Show error to user
            try {
                if (::binding.isInitialized) {
                    showError("Error initializing: ${e.message ?: "Unknown error"}")
                } else {
                    // If binding failed, show a simple error
                    setContentView(
                        TextView(this).apply {
                            text = "Error: ${e.message ?: "Failed to initialize"}"
                            setPadding(50, 50, 50, 50)
                        }
                    )
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
                android.util.Log.e(
                    "AISummaryActivity",
                    "onCreate: error showing error - ${e2.message}",
                    e2,
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
        // Set title d?a trên initialSection
        val title =
            when (initialSection) {
                // Text-mode sections
                AISection.SUMMARY -> "AI Summary"
                AISection.BULLET_POINTS -> "AI Bullet Points"
                AISection.QUESTIONS -> "AI Questions"
                AISection.MCQ -> "AI MCQ Practice"

                // Vocab/checklist sections
                AISection.VOCAB_MCQ -> "AI Vocabulary Quizzes"
                AISection.VOCAB_STORY -> "AI Story"
                AISection.VOCAB_SUMMARY_TABLE -> "AI Vocabulary Summary"
                AISection.VOCAB_FLASHCARDS -> "AI Flashcards"
                AISection.VOCAB_CLOZE -> "AI Cloze Test"
                AISection.VOCAB_MATCH -> "AI Match Pairs"
            }
        binding.Toolbar.title = title
    }

    private fun extractIntentData() {
        noteContent = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        backendNoteId = intent.getStringExtra(EXTRA_BACKEND_NOTE_ID)
        useProcessEndpoint = intent.getBooleanExtra(EXTRA_USE_PROCESS, false)
        contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE)
        checkedVocabItems = intent.getStringExtra(EXTRA_CHECKED_VOCAB_ITEMS)
        forceShowAllSections = intent.getBooleanExtra(EXTRA_SHOW_ALL_SECTIONS, false)
        initialSection =
            intent.getStringExtra(EXTRA_INITIAL_SECTION)?.let {
                runCatching { AISection.valueOf(it) }.getOrNull()
            } ?: AISection.SUMMARY
        statsOnly = intent.getBooleanExtra(EXTRA_STATS_ONLY, false)
        hasScrolledToInitialSection = initialSection == AISection.SUMMARY

        // If backendNoteId is not provided in intent, try to get it from preferences
        // BUT only use it if the content hash matches (to avoid using old backend_note_id for new
        // content)
        if (backendNoteId == null && noteId != -1L && noteContent.isNotBlank()) {
            val storedBackendNoteId =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                    this,
                    noteId,
                )

            // Check if content hash matches before using stored backend_note_id
            if (storedBackendNoteId != null) {
                val currentHash = computeContentHash()
                val storedHash =
                    com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                        this,
                        noteId,
                        currentHashMode(),
                    )

                // Only use stored backend_note_id if hash matches (content hasn't changed)
                if (currentHash != null && currentHash == storedHash) {
                    backendNoteId = storedBackendNoteId
                    android.util.Log.d(
                        "AISummaryActivity",
                        "extractIntentData: Using stored backend_note_id=$backendNoteId (hash matches)",
                    )
                } else {
                    // Content has changed, clear the old backend_note_id mapping
                    android.util.Log.d(
                        "AISummaryActivity",
                        "extractIntentData: Content changed, clearing old backend_note_id mapping. currentHash=$currentHash, storedHash=$storedHash",
                    )
                    com.philkes.notallyx.data.preferences.AIUserPreferences.removeBackendNoteId(
                        this,
                        noteId,
                    )
                }
            }
        }

        // Debug logging
        android.util.Log.d(
            "AISummaryActivity",
            "extractIntentData: noteContent length=${noteContent.length}, noteId=$noteId, backendNoteId=$backendNoteId, useProcessEndpoint=$useProcessEndpoint, initialSection=$initialSection",
        )

        intent.getStringExtra(EXTRA_PRECOMPUTED_RESULT)?.let { json ->
            runCatching { Gson().fromJson(json, SummaryResponse::class.java) }
                .onSuccess { summaryResponse = it }
        }
    }

    private fun setupClickListeners() {
        binding.RetryButton.setOnClickListener { summarizeNote() }
        binding.RetryButton.isVisible = summaryResponse == null

        binding.CopySummaryButton.setOnClickListener {
            summaryResponse?.summaries?.let { summaries ->
                val text = buildString {
                    summaries.oneSentence?.let { appendLine("? $it\n") }
                    summaries.shortParagraph?.let { appendLine("? $it\n") }
                    summaries.bulletPoints?.let { points ->
                        appendLine("? Key Points:")
                        points.forEach { appendLine("? $it") }
                    }
                }
                copyToClipboard(text)
            }
        }

        binding.CopyQuestionsButton.setOnClickListener {
            summaryResponse?.questions?.let { questions ->
                val text = buildString {
                    questions.forEachIndexed { index, q ->
                        appendLine("Q${index + 1}: ${q.question}")
                        appendLine("A: ${q.answer}")
                        appendLine()
                    }
                }
                copyToClipboard(text)
            }
        }

        binding.CopyRawTextButton.setOnClickListener {
            summaryResponse?.let { resp ->
                val textToCopy = resp.processedText ?: resp.rawText
                if (!textToCopy.isNullOrBlank()) {
                    copyToClipboard(textToCopy)
                }
            }
        }

        // Expand/Collapse raw text
        binding.ExpandRawTextButton.setOnClickListener {
            val textView = binding.RawTextContent
            if (textView.maxLines == 10) {
                textView.maxLines = Int.MAX_VALUE
                binding.ExpandRawTextButton.text = getString(R.string.show_less)
            } else {
                textView.maxLines = 10
                binding.ExpandRawTextButton.text = getString(R.string.show_more)
            }
        }

        setupMCQTabs()
    }

    private fun setupMCQTabs() {
        binding.MCQTabLayout.addTab(binding.MCQTabLayout.newTab().setText(R.string.ai_mcq_easy))
        binding.MCQTabLayout.addTab(binding.MCQTabLayout.newTab().setText(R.string.ai_mcq_medium))
        binding.MCQTabLayout.addTab(binding.MCQTabLayout.newTab().setText(R.string.ai_mcq_hard))

        binding.MCQTabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    currentMCQDifficulty =
                        when (tab.position) {
                            0 -> "easy"
                            1 -> "medium"
                            2 -> "hard"
                            else -> "easy"
                        }
                    displayMCQs()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )
    }

    private fun summarizeNote() {
        showLoading()

        // Validate noteContent before proceeding
        if (noteContent.isBlank()) {
            showError(getString(R.string.ai_error_empty_note))
            return
        }

        lifecycleScope.launch {
            try {
                // ??m b?o note_id g?i lên backend luôn là UUID duy nh?t (tránh trùng sau khi wipe
                // app)
                val noteIdToUse = ensureBackendNoteId()

                val userId = getAiUserId()

                android.util.Log.d(
                    "AISummaryActivity",
                    "summarizeNote: noteContent length=${noteContent.length}, userId=$userId, noteIdToUse=$noteIdToUse, useProcessEndpoint=$useProcessEndpoint",
                )

                if (userId.isNullOrBlank()) {
                    showError("User ID is missing. Please check your settings.")
                    return@launch
                }

                val shouldUseCache = shouldUseLocalCache()
                android.util.Log.d(
                    "AISummaryActivity",
                    "summarizeNote: shouldUseCache=$shouldUseCache",
                )

                // N?u nên dùng cache và ?ã có noteId, th? GET tr??c ?? tránh g?i POST
                if (shouldUseCache && !noteIdToUse.isNullOrBlank()) {
                    val cached = aiRepository.getCachedNote(userId, noteIdToUse)
                    if (cached != null) {
                        android.util.Log.d(
                            "AISummaryActivity",
                            "summarizeNote: Using cached note from GET /notes/$noteIdToUse",
                        )
                        summaryResponse = cached
                        persistBackendNoteIdIfNeeded(noteIdToUse)
                        displayResults(cached)
                        persistContentHashIfNeeded()
                        return@launch
                    }
                }

                // Luôn dùng /process/combined cho note text (text + future files) ?? ??ng b? cache
                val result =
                    aiRepository.processCombinedInputs(
                        noteText = noteContent,
                        attachments = emptyList(),
                        userId = userId,
                        noteId = noteIdToUse,
                        contentType = contentType,
                        checkedVocabItems = checkedVocabItems,
                        useCache = shouldUseCache,
                    )

                when (result) {
                    is AIResult.Success -> {
                        summaryResponse = result.data
                        persistBackendNoteIdIfNeeded(noteIdToUse)
                        displayResults(result.data)
                        persistContentHashIfNeeded()
                    }
                    is AIResult.Error -> {
                        showError(result.message)
                    }
                    is AIResult.Loading -> {
                        showLoading()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error: ${e.message ?: "Unknown error occurred"}")
            }
        }
    }

    /**
     * ??m b?o note_id g?i lên backend luôn là UUID duy nh?t ?? tránh trùng v?i các l?n cài app
     * tr??c ?ó (khi local noteId có th? l?p l?i sau khi wipe).
     */
    private fun ensureBackendNoteId(): String {
        backendNoteId?.let {
            return it
        }

        val generated = UUID.randomUUID().toString()
        backendNoteId = generated

        // N?u có local noteId, l?u mapping ?? l?n sau v?n dùng cùng backendNoteId (khi hash kh?p)
        if (noteId != -1L) {
            AIUserPreferences.setBackendNoteId(this, noteId, generated)
        }
        return generated
    }

    private fun persistBackendNoteIdIfNeeded(noteIdToUse: String?) {
        if (backendNoteId == null && noteId != -1L && noteIdToUse != null) {
            AIUserPreferences.setBackendNoteId(this@AISummaryActivity, noteId, noteIdToUse)
        }
    }

    private fun showLoading() {
        try {
            if (::binding.isInitialized) {
                binding.LoadingLayout.isVisible = true
                binding.ErrorLayout.isVisible = false
                binding.ContentScrollView.isVisible = false
            }
        } catch (e: Exception) {
            android.util.Log.e("AISummaryActivity", "showLoading: error - ${e.message}", e)
        }
    }

    private fun showError(message: String) {
        try {
            if (::binding.isInitialized) {
                binding.LoadingLayout.isVisible = false
                binding.ErrorLayout.isVisible = true
                binding.ContentScrollView.isVisible = false
                binding.ErrorMessage.text = message
                android.util.Log.d("AISummaryActivity", "showError: $message")
            }
        } catch (e: Exception) {
            android.util.Log.e("AISummaryActivity", "showError: error - ${e.message}", e)
        }
    }

    private fun showContent() {
        try {
            if (::binding.isInitialized) {
                binding.LoadingLayout.isVisible = false
                binding.ErrorLayout.isVisible = false
                binding.ContentScrollView.isVisible = true
                android.util.Log.d("AISummaryActivity", "showContent: content shown")
            }
        } catch (e: Exception) {
            android.util.Log.e("AISummaryActivity", "showContent: error - ${e.message}", e)
        }
    }

    private fun displayResults(
        response: SummaryResponse,
        forceShowAllSections: Boolean = this.forceShowAllSections,
    ) {
        showContent()
        summaryResponse = response
        binding.RetryButton.isVisible = summaryResponse == null

        val summaries = response.summaries
        val showAll = forceShowAllSections

        val isVocabMode = useProcessEndpoint

        // Always show header with processed_text at the top of each section
        val displayText = buildDisplayProcessedText(response, isVocabMode)
        if (displayText != null) {
            binding.RawTextCard.isVisible = true
            if (isVocabMode && displayText is SpannableString) {
                // For vocab mode, use SpannableString to show bold words
                binding.RawTextContent.setText(displayText)
            } else {
                binding.RawTextContent.text = displayText.toString()
            }
            binding.RawTextContent.maxLines = 10
            val textLength = displayText.toString().length
            binding.ExpandRawTextButton.isVisible = textLength > 200
            binding.ExpandRawTextButton.text = getString(R.string.show_more)
        } else {
            // Even if no processed_text, try to show raw_text as fallback
            val fallbackText = response.rawText
            if (!fallbackText.isNullOrBlank()) {
                binding.RawTextCard.isVisible = true
                binding.RawTextContent.text = fallbackText
                binding.RawTextContent.maxLines = 10
                val textLength = fallbackText.length
                binding.ExpandRawTextButton.isVisible = textLength > 200
                binding.ExpandRawTextButton.text = getString(R.string.show_more)
            } else {
                binding.RawTextCard.isVisible = false
            }
        }

        // ----- TEXT NOTE UI (non-vocab mode) -----
        if (!isVocabMode) {
            if (showAll) {
                // Display all sections when showAll = true
                updateOneSentenceCard(summaries?.oneSentence)
                updateParagraphCard(summaries?.shortParagraph)
                updateBulletPointsCard(summaries?.bulletPoints)
            } else if (initialSection == AISection.SUMMARY) {
                // Quick Summary: only display one sentence and paragraph, DO NOT display bullet
                // points
                updateOneSentenceCard(summaries?.oneSentence)
                updateParagraphCard(summaries?.shortParagraph)
                updateBulletPointsCard(null) // Hide bullet points to avoid duplication
            } else if (initialSection == AISection.BULLET_POINTS) {
                // Bullet Points section: only display bullet points
                updateOneSentenceCard(null)
                updateParagraphCard(null)
                updateBulletPointsCard(summaries?.bulletPoints)
            } else {
                // Other sections (QUESTIONS, MCQ)
                updateOneSentenceCard(null)
                updateParagraphCard(null)
                updateBulletPointsCard(null)
            }

            if (showAll || initialSection == AISection.QUESTIONS) {
                updateQuestionsCard(response.questions)
            } else {
                binding.QuestionsCard.isVisible = false
            }

            if (showAll || initialSection == AISection.MCQ) {
                updateMCQCard(response.mcqs)
            } else {
                binding.MCQCard.isVisible = false
            }
        } else {
            // Vocab mode (checklist): ?n to?n b? ph?n summary/text m?c ??nh
            updateOneSentenceCard(null)
            updateParagraphCard(null)
            updateBulletPointsCard(null)
            binding.QuestionsCard.isVisible = false
            binding.MCQCard.isVisible = false
        }

        // Stats-only mode: ?n các n?i dung khác, ch? hi?n th? ti?n ?? và b?ng th?ng kê
        if (statsOnly) {
            hideAllCardsExceptStats()
            renderStatsOnly()
            return
        }

        val vocabStory = response.vocabStory ?: response.review?.vocabStory
        val vocabMcqs = response.vocabMcqs ?: response.review?.vocabMcqs
        val flashcards = response.flashcards ?: response.review?.flashcards
        val mindmap = null // Mindmap removed
        val summaryTable = response.summaryTable ?: response.review?.summaryTable
        val clozeTests = response.clozeTests ?: response.review?.clozeTests
        val matchPairs = response.matchPairs ?: response.review?.matchPairs

        // LUÔN set summaryTableState ?? Match Pairs có th? l?y ngh?a ti?ng Vi?t
        // (ngay c? khi không hi?n th? Summary Table card)
        summaryTableState = summaryTable?.ifEmpty { null }

        // Debug logging for vocab data
        android.util.Log.d(
            "AISummaryActivity",
            "displayResults: vocab data - story=${vocabStory != null}, mcqs=${vocabMcqs?.size ?: 0}, flashcards=${flashcards?.size ?: 0}, mindmap=${mindmap != null}, summaryTable=${summaryTable?.size ?: 0}, clozeTests=${clozeTests?.size ?: 0}, matchPairs=${matchPairs?.size ?: 0}",
        )

        if (!isVocabMode) {
            // N?u kh?ng ? ch? ?? vocab, ch? hi?n th? n?u c? (v? d? l?ch s?)
            updateVocabStoryCard(vocabStory)
            updateVocabMCQCard(vocabMcqs)
            updateFlashcardsCard(flashcards)
            updateSummaryTableCard(summaryTable)
            updateClozeCard(clozeTests)
            updateMatchPairsCard(matchPairs)
        } else {
            // Vocab mode: ch? hi?n th? ch?c n?ng ???c ch?n
            if (showAll) {
                updateSummaryTableCard(summaryTable)
                updateVocabStoryCard(vocabStory)
                updateVocabMCQCard(vocabMcqs)
                updateFlashcardsCard(flashcards)
                // mindmap removed
                updateClozeCard(clozeTests)
                updateMatchPairsCard(matchPairs)
            } else {
                when (initialSection) {
                    AISection.VOCAB_SUMMARY_TABLE -> {
                        updateSummaryTableCard(summaryTable)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateClozeCard(null)
                        updateMatchPairsCard(null)
                    }
                    AISection.VOCAB_STORY -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(vocabStory)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateClozeCard(null)
                        updateMatchPairsCard(null)
                    }
                    AISection.VOCAB_MCQ -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(vocabMcqs)
                        updateFlashcardsCard(null)
                        updateClozeCard(null)
                        updateMatchPairsCard(null)
                    }
                    AISection.VOCAB_FLASHCARDS -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(flashcards)
                        updateClozeCard(null)
                        updateMatchPairsCard(null)
                    }
                    AISection.VOCAB_CLOZE -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateClozeCard(clozeTests)
                        updateMatchPairsCard(null)
                    }
                    AISection.VOCAB_MATCH -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateClozeCard(null)
                        updateMatchPairsCard(matchPairs)
                    }
                    else -> {
                        // fallback: hi?n th? t?t c? n?u kh?ng kh?p case
                        updateSummaryTableCard(summaryTable)
                        updateVocabStoryCard(vocabStory)
                        updateVocabMCQCard(vocabMcqs)
                        updateFlashcardsCard(flashcards)
                        updateClozeCard(clozeTests)
                        updateMatchPairsCard(matchPairs)
                    }
                }
            }
        }

        // C?p nh?t Overall Progress Card (n?u có d? li?u quiz)
        updateOverallProgressCardSummary()

        maybeScrollToInitialSection(forceShowAll = showAll)
    }

    private fun shouldUseLocalCache(): Boolean {
        if (noteId == -1L) {
            pendingContentHash = null
            return false
        }

        val currentHash = computeContentHash()
        pendingContentHash = currentHash

        if (currentHash.isNullOrBlank()) {
            return false
        }

        val storedHash = AIUserPreferences.getNoteContentHash(this, noteId, currentHashMode())
        return storedHash == currentHash
    }

    private fun persistContentHashIfNeeded() {
        val hash = pendingContentHash
        if (hash.isNullOrBlank() || noteId == -1L) {
            return
        }
        AIUserPreferences.setNoteContentHash(this, noteId, currentHashMode(), hash)
    }

    private fun computeContentHash(): String? {
        if (noteContent.isBlank()) return null
        val payload = "${currentHashMode()}::$noteContent"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun currentHashMode(): String = if (useProcessEndpoint) "vocab" else "text"

    private fun maybeScrollToInitialSection(forceShowAll: Boolean) {
        if (forceShowAll || hasScrolledToInitialSection) return
        binding.ContentScrollView.post {
            val targetView: View? =
                when (initialSection) {
                    AISection.SUMMARY ->
                        listOf(binding.ParagraphCard, binding.OneSentenceCard).firstOrNull {
                            it.isVisible
                        }
                    AISection.BULLET_POINTS -> binding.BulletPointsCard.takeIf { it.isVisible }
                    AISection.QUESTIONS -> binding.QuestionsCard.takeIf { it.isVisible }
                    AISection.MCQ -> binding.MCQCard.takeIf { it.isVisible }
                    AISection.VOCAB_STORY -> binding.VocabStoryCard.takeIf { it.isVisible }
                    AISection.VOCAB_MCQ -> binding.VocabMCQCard.takeIf { it.isVisible }
                    AISection.VOCAB_FLASHCARDS -> binding.FlashcardsCard.takeIf { it.isVisible }
                    AISection.VOCAB_CLOZE -> binding.ClozeCard.takeIf { it.isVisible }
                    AISection.VOCAB_MATCH -> binding.MatchPairsCard.takeIf { it.isVisible }
                    AISection.VOCAB_SUMMARY_TABLE ->
                        binding.SummaryTableCard.takeIf { it.isVisible }
                }

            targetView?.let { view ->
                binding.ContentScrollView.smoothScrollTo(0, view.top)
                hasScrolledToInitialSection = true
            }
        }
    }

    private fun updateOneSentenceCard(text: String?) {
        binding.OneSentenceCard.isVisible = !text.isNullOrBlank()
        if (!text.isNullOrBlank()) {
            binding.OneSentenceText.text = text
        }
    }

    private fun updateParagraphCard(text: String?) {
        binding.ParagraphCard.isVisible = !text.isNullOrBlank()
        if (!text.isNullOrBlank()) {
            binding.ParagraphText.text = text
        }
    }

    private fun updateBulletPointsCard(points: List<String>?) {
        if (points.isNullOrEmpty()) {
            binding.BulletPointsCard.isVisible = false
            binding.BulletPointsContainer.removeAllViews()
            return
        }
        binding.BulletPointsCard.isVisible = true
        binding.BulletPointsContainer.removeAllViews()
        points.forEach { point ->
            binding.BulletPointsContainer.addView(createBulletPointView(point))
        }
    }

    private fun updateQuestionsCard(questions: List<Question>?) {
        if (questions.isNullOrEmpty()) {
            binding.QuestionsCard.isVisible = false
            binding.QuestionsContainer.removeAllViews()
            return
        }
        binding.QuestionsCard.isVisible = true
        binding.QuestionsContainer.removeAllViews()
        questions.forEachIndexed { index, question ->
            binding.QuestionsContainer.addView(createQuestionView(index + 1, question))
        }
    }

    private fun updateMCQCard(mcqs: com.philkes.notallyx.data.api.models.MCQs?) {
        val hasMCQs =
            mcqs != null &&
                (!mcqs.easy.isNullOrEmpty() ||
                    !mcqs.medium.isNullOrEmpty() ||
                    !mcqs.hard.isNullOrEmpty())
        binding.MCQCard.isVisible = hasMCQs
        if (hasMCQs) {
            summaryResponse = summaryResponse?.copy(mcqs = mcqs) ?: summaryResponse
            displayMCQs()
        } else {
            binding.MCQContainer.removeAllViews()
        }
    }

    private fun createBulletPointView(text: String): TextView {
        return TextView(this).apply {
            this.text = "\u2022 $text"
            textSize = 15f
            setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            setTextIsSelectable(true)
        }
    }

    private fun createQuestionView(number: Int, question: Question): View {
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dpToPx(), 0, 12.dpToPx())
            }

        // Question text
        val questionText =
            TextView(this).apply {
                text = "Q$number: ${question.question}"
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setTextIsSelectable(true)
            }
        container.addView(questionText)

        // Answer (initially hidden)
        val answerText =
            TextView(this).apply {
                text = "A: ${question.answer}"
                textSize = 14f
                setPadding(0, 8.dpToPx(), 0, 0)
                visibility = View.GONE
                setTextIsSelectable(true)
            }
        container.addView(answerText)

        // Show/Hide button
        val toggleButton =
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    text = getString(R.string.ai_show_answer)
                    textSize = 12f
                    setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                    layoutParams =
                        LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            )
                            .apply { topMargin = 8.dpToPx() }

                    setOnClickListener {
                        if (answerText.isVisible) {
                            answerText.visibility = View.GONE
                            text = getString(R.string.ai_show_answer)
                        } else {
                            answerText.visibility = View.VISIBLE
                            text = getString(R.string.ai_hide_answer)
                        }
                    }
                }
        container.addView(toggleButton)

        return container
    }

    private fun buildDisplayProcessedText(
        response: SummaryResponse,
        isVocabMode: Boolean = false,
    ): CharSequence? {
        // For vocab mode, prioritize getting vocab words from summaryTable (most accurate)
        if (isVocabMode) {
            val vocabItemsList = mutableListOf<String>()

            // First priority: Get words from summaryTable
            response.summaryTable?.forEach { row ->
                row.word?.takeIf { it.isNotBlank() }?.let { vocabItemsList.add(it) }
            }

            // Second priority: Get from sources (checked items or file attachments)
            if (vocabItemsList.isEmpty() && !response.sources.isNullOrEmpty()) {
                response.sources.forEach { source ->
                    val body = source.processedText ?: source.rawText
                    if (!body.isNullOrBlank()) {
                        val cleaned = body.replace("\\n", "\n").trim()
                        if (cleaned.isNotEmpty()) {
                            // Split by newlines (each line is a vocab item)
                            val items =
                                if (cleaned.contains("\n")) {
                                    cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                } else {
                                    // If no newlines, try to split by comma or space
                                    cleaned
                                        .split(Regex("[,;\\s]+"))
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                }
                            vocabItemsList.addAll(items)
                        }
                    }
                }
            }

            // Third priority: Get from processed_text
            if (vocabItemsList.isEmpty()) {
                val primary = response.processedText ?: response.rawText
                if (primary != null) {
                    val cleaned = primary.replace("\\n", "\n").trim()
                    if (cleaned.isNotEmpty()) {
                        val items =
                            if (cleaned.contains("\n")) {
                                cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                            } else {
                                cleaned
                                    .split(Regex("[,;\\s]+"))
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                        vocabItemsList.addAll(items)
                    }
                }
            }

            // Remove duplicates and create formatted text
            val uniqueVocabItems = vocabItemsList.distinct()
            if (uniqueVocabItems.isNotEmpty()) {
                android.util.Log.d(
                    "AISummaryActivity",
                    "buildDisplayProcessedText: vocabItems count=${uniqueVocabItems.size}",
                )
                return createBoldVocabText(uniqueVocabItems)
            }
        }

        // For non-vocab mode, return as before
        val sources = response.sources
        if (!sources.isNullOrEmpty()) {
            val builder = StringBuilder()
            sources.forEachIndexed { index, source ->
                val typeLabel =
                    when (source.type?.lowercase()) {
                        "text" -> "Text"
                        "image" -> "Image"
                        "audio" -> "Audio"
                        "pdf" -> "PDF"
                        "docx" -> "Document"
                        else -> "Source"
                    }
                if (builder.isNotEmpty()) {
                    builder.append("\n\n")
                }
                val title =
                    source.source?.takeIf { it.isNotBlank() }?.let { "$typeLabel: $it" }
                        ?: typeLabel
                builder.append(title)
                val body = source.processedText ?: source.rawText
                if (!body.isNullOrBlank()) {
                    val cleaned = body.replace("\\n", "\n").trim()
                    if (cleaned.isNotEmpty()) {
                        builder.append("\n").append(cleaned)
                    }
                }
            }
            return builder.toString().ifBlank { null }
        }

        val primary = response.processedText ?: response.rawText
        if (primary != null) {
            val cleaned = primary.replace("\\n", "\n").trim()
            return cleaned.ifBlank { null }
        }
        return null
    }

    /** Create SpannableString with bold vocab words separated by " - " */
    private fun createBoldVocabText(vocabItems: List<String>): SpannableString {
        val text = vocabItems.joinToString(" - ")
        val spannable = SpannableString(text)

        // Make each vocab word bold
        var currentIndex = 0
        vocabItems.forEach { item ->
            val itemIndex = text.indexOf(item, currentIndex)
            if (itemIndex >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    itemIndex,
                    itemIndex + item.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                currentIndex = itemIndex + item.length
            }
        }

        return spannable
    }

    private fun displayMCQs() {
        val mcqs = summaryResponse?.mcqs ?: return

        val currentMCQs =
            when (currentMCQDifficulty) {
                "easy" -> mcqs.easy
                "medium" -> mcqs.medium
                "hard" -> mcqs.hard
                else -> mcqs.easy
            }

        binding.MCQContainer.removeAllViews()

        if (currentMCQs.isNullOrEmpty()) {
            val emptyText =
                TextView(this).apply {
                    text = "No questions available for this difficulty level"
                    textSize = 14f
                    setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                }
            binding.MCQContainer.addView(emptyText)
            return
        }

        currentMCQs.forEachIndexed { index, mcq ->
            val mcqView = createMCQView(index + 1, mcq)
            binding.MCQContainer.addView(mcqView)
        }
    }

    private fun createMCQView(number: Int, mcq: MCQ): View {
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dpToPx(), 0, 16.dpToPx())
            }

        // Question text
        val questionText =
            TextView(this).apply {
                text = "Q$number: ${mcq.question}"
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
            }
        container.addView(questionText)

        // Options
        val optionsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dpToPx(), 0, 0)
            }

        var answered = false
        var explanationText: TextView? = null

        mcq.options.forEach { (key, value) ->
            val optionButton =
                MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    )
                    .apply {
                        text = "$key: $value"
                        textSize = 13f
                        isAllCaps = false
                        layoutParams =
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                )
                                .apply { topMargin = 4.dpToPx() }

                        setOnClickListener {
                            if (answered) return@setOnClickListener

                            answered = true
                            // Lock all options after first choice
                            for (i in 0 until optionsContainer.childCount) {
                                (optionsContainer.getChildAt(i) as? MaterialButton)?.isEnabled =
                                    false
                            }

                            if (key == mcq.answer) {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_green_light,
                                    )
                                )
                                Toast.makeText(
                                        this@AISummaryActivity,
                                        R.string.ai_correct,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            } else {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_red_light,
                                    )
                                )
                                Toast.makeText(
                                        this@AISummaryActivity,
                                        R.string.ai_incorrect,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()

                                for (i in 0 until optionsContainer.childCount) {
                                    val child = optionsContainer.getChildAt(i) as? MaterialButton
                                    if (child?.text?.startsWith(mcq.answer) == true) {
                                        child.setBackgroundColor(
                                            ContextCompat.getColor(
                                                this@AISummaryActivity,
                                                android.R.color.holo_green_light,
                                            )
                                        )
                                    }
                                }
                            }

                            explanationText?.visibility = View.VISIBLE
                        }
                    }
            optionsContainer.addView(optionButton)
        }
        container.addView(optionsContainer)

        // Explanation (shown after answer)
        mcq.explanation?.let { explanation ->
            explanationText =
                TextView(this).apply {
                    text = "? $explanation"
                    textSize = 13f
                    setPadding(0, 12.dpToPx(), 0, 0)
                    visibility = View.GONE
                    setTextIsSelectable(true)
                }
            container.addView(explanationText)
        }

        return container
    }

    private fun updateVocabStoryCard(story: VocabStory?) {
        binding.VocabStoryCard.isVisible = story != null
        if (story == null) {
            binding.VocabStoryParagraphs.removeAllViews()
            binding.VocabStoryUsedWords.text = ""
            vocabStoryOriginal = null
            return
        }

        // L?u d? li?u g?c ?? dùng cho translate
        vocabStoryOriginal = story

        // Hi?n th? title
        val title = story.title?.takeIf { it.isNotBlank() } ?: getString(R.string.ai_vocab_story)
        binding.VocabStoryTitle.text = title

        // Hi?n th? paragraphs
        binding.VocabStoryParagraphs.removeAllViews()
        val paragraphs = story.paragraphs?.filter { it.isNotBlank() } ?: emptyList()

        if (paragraphs.isEmpty()) {
            // N?u không có paragraphs, hi?n th? thông báo
            binding.VocabStoryParagraphs.addView(
                TextView(this).apply {
                    text = "No story content available"
                    textSize = 14f
                    setTextColor(
                        ContextCompat.getColor(this@AISummaryActivity, android.R.color.darker_gray)
                    )
                    setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                    gravity = android.view.Gravity.CENTER
                }
            )
        } else {
            paragraphs.forEachIndexed { index, paragraph ->
                val paragraphText = paragraph.trim()
                if (paragraphText.isNotBlank()) {
                    binding.VocabStoryParagraphs.addView(
                        TextView(this).apply {
                            text = markdownBoldToSpannable(paragraphText)
                            textSize = 15f
                            setTextIsSelectable(true)
                            setPadding(0, if (index == 0) 0 else 8.dpToPx(), 0, 8.dpToPx())
                            setLineSpacing(4f, 1.2f)
                        }
                    )
                }
            }
        }

        // Hide used words section - already shown in Processed Text above
        binding.VocabStoryUsedWords.isVisible = false

        android.util.Log.d(
            "AISummaryActivity",
            "updateVocabStoryCard: title='$title', paragraphs count=${paragraphs.size}",
        )
    }

    private fun updateVocabMCQCard(quizzes: List<VocabQuiz>?) {
        val items = quizzes.orEmpty()
        binding.VocabMCQCard.isVisible = items.isNotEmpty()
        binding.VocabMCQContainer.removeAllViews()
        if (items.isEmpty()) return

        // L?y set_id t? b? câu h?i (t?t c? cùng set_id)
        val currentSetId = items.firstOrNull()?.setId
        if (noteId != -1L && currentSetId != null) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val savedSetId = prefs.getInt("note_${noteId}_vocab_mcq_set_id", -1)
            if (savedSetId != -1 && savedSetId != currentSetId) {
                // set_id thay ??i -> reset ti?n trình MCQ
                android.util.Log.d(
                    "AISummaryActivity",
                    "updateVocabMCQCard: set_id changed ($savedSetId -> $currentSetId), resetting progress",
                )
                prefs
                    .edit()
                    .apply {
                        remove("note_${noteId}_vocab_mcq_completed")
                        remove("note_${noteId}_vocab_mcq_score")
                        remove("note_${noteId}_vocab_mcq_total")
                        remove("note_${noteId}_vocab_mcq_set_id")
                    }
                    .apply()
                vocabMCQUserAnswers.clear()
                vocabMCQScore = 0
                vocabQuizCompleted = false
            }
            // L?u set_id hi?n t?i ?? l?n sau so sánh
            prefs.edit().putInt("note_${noteId}_vocab_mcq_set_id", currentSetId).apply()
        }

        // Ki?m tra xem quiz ?ã hoàn thành ch?a
        val isCompleted = checkQuizCompleted("vocab_mcq")
        if (isCompleted) {
            // Load k?t qu? ?ã l?u và hi?n th?
            vocabQuizCompleted = true
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            vocabMCQScore = prefs.getInt("note_${noteId}_vocab_mcq_score", 0)

            // Khôi ph?c th? t? câu h?i và ?áp án ?ã ch?n t? prefs
            val gson = Gson()
            val savedOrderJson = prefs.getString("note_${noteId}_vocab_mcq_order", null)
            val savedAnswersJson = prefs.getString("note_${noteId}_vocab_mcq_answers", null)

            // Th? t? câu h?i (danh sách id)
            val idOrder: List<Int>? =
                try {
                    savedOrderJson?.let { gson.fromJson(it, Array<Int>::class.java)?.toList() }
                } catch (_: Exception) {
                    null
                }

            // Map id -> answer
            val answersById: Map<Int, String>? =
                try {
                    savedAnswersJson?.let {
                        gson.fromJson(
                            it,
                            object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}.type,
                        )
                    }
                } catch (_: Exception) {
                    null
                }

            // S?p x?p l?i theo order ?ã l?u n?u có, ?? map ?áp án v? ?úng câu
            val orderedItems =
                if (!idOrder.isNullOrEmpty()) {
                    items.sortedBy { quiz -> idOrder.indexOf(quiz.id ?: -1) }
                } else {
                    items
                }

            vocabMCQAllQuestions = orderedItems
            vocabMCQShuffledQuestions = orderedItems.toMutableList()

            // Khôi ph?c ?áp án ng??i dùng theo id
            vocabMCQUserAnswers.clear()
            if (!answersById.isNullOrEmpty()) {
                vocabMCQShuffledQuestions.forEachIndexed { idx, quiz ->
                    val qid = quiz.id
                    if (qid != null) {
                        answersById[qid]?.let { ans -> vocabMCQUserAnswers[idx] = ans }
                    }
                }
            }
            showVocabMCQResult()
            return
        }

        // L?u d? li?u g?c ?? dùng cho translate
        vocabMCQsOriginal = items

        // Checklist mode: hi?n th? t?ng câu h?i ng?u nhiên, tính ?i?m
        vocabMCQAllQuestions = items
        vocabMCQShuffledQuestions = items.shuffled().toMutableList()
        vocabMCQCurrentIndex = 0
        vocabMCQUserAnswers.clear()
        vocabMCQScore = 0
        vocabMCQIsQuizMode = true
        vocabQuizCompleted = false

        displayVocabMCQQuestion()
    }

    private fun checkQuizCompleted(quizType: String): Boolean {
        if (noteId == -1L) return false
        val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
        return prefs.getBoolean("note_${noteId}_${quizType}_completed", false)
    }

    private fun displayVocabMCQQuestion() {
        binding.VocabMCQContainer.removeAllViews()

        if (vocabMCQCurrentIndex >= vocabMCQShuffledQuestions.size) {
            // H?t câu h?i, hi?n th? k?t qu?
            showVocabMCQResult()
            return
        }

        val quiz = vocabMCQShuffledQuestions[vocabMCQCurrentIndex]
        val questionNumber = vocabMCQCurrentIndex + 1
        val totalQuestions = vocabMCQShuffledQuestions.size

        // Header: Question X of Y
        val header =
            TextView(this).apply {
                text = "Question $questionNumber / $totalQuestions"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setPadding(0, 0, 0, 12.dpToPx())
            }
        binding.VocabMCQContainer.addView(header)

        // Question text
        val questionText =
            TextView(this).apply {
                val questionStr = quiz.question ?: ""
                text = markdownBoldToSpannable(questionStr)
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setTextIsSelectable(true)
                setPadding(0, 0, 0, 16.dpToPx())
            }
        binding.VocabMCQContainer.addView(questionText)

        // Options
        val optionsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

        var selectedAnswer: String? = vocabMCQUserAnswers[vocabMCQCurrentIndex]
        var isAnswered = selectedAnswer != null
        var nextButton: MaterialButton? = null

        quiz.options.orEmpty().forEach { (key, value) ->
            val optionButton =
                MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    )
                    .apply {
                        text = markdownBoldToSpannable("$key: $value")
                        textSize = 13f
                        isAllCaps = false
                        layoutParams =
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                )
                                .apply { topMargin = 4.dpToPx() }

                        // Highlight selected/correct/incorrect answer if already answered
                        if (isAnswered) {
                            if (key == selectedAnswer) {
                                // User's selected answer
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        if (key == quiz.answer) android.R.color.holo_green_light
                                        else android.R.color.holo_red_light,
                                    )
                                )
                            } else if (key == quiz.answer) {
                                // Correct answer (highlight if user was wrong)
                                if (selectedAnswer != quiz.answer) {
                                    setBackgroundColor(
                                        ContextCompat.getColor(
                                            this@AISummaryActivity,
                                            android.R.color.holo_green_light,
                                        )
                                    )
                                }
                            }
                            isEnabled = false
                        }

                        setOnClickListener {
                            if (isAnswered) return@setOnClickListener

                            selectedAnswer = key
                            vocabMCQUserAnswers[vocabMCQCurrentIndex] = key
                            isAnswered = true

                            // Show correct/incorrect
                            if (key == quiz.answer) {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_green_light,
                                    )
                                )
                                vocabMCQScore++
                            } else {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_red_light,
                                    )
                                )
                                // Highlight correct answer
                                for (i in 0 until optionsContainer.childCount) {
                                    val child = optionsContainer.getChildAt(i) as? MaterialButton
                                    if (
                                        child?.text?.toString()?.startsWith(quiz.answer ?: "") ==
                                            true
                                    ) {
                                        child.setBackgroundColor(
                                            ContextCompat.getColor(
                                                this@AISummaryActivity,
                                                android.R.color.holo_green_light,
                                            )
                                        )
                                    }
                                }
                            }

                            // Lock all options
                            for (i in 0 until optionsContainer.childCount) {
                                (optionsContainer.getChildAt(i) as? MaterialButton)?.isEnabled =
                                    false
                            }

                            // Enable Next/View Results once answered
                            nextButton?.isEnabled = true

                            // Show explanation if available
                            quiz.explanation?.let { explanation ->
                                val explanationView =
                                    TextView(this@AISummaryActivity).apply {
                                        text = markdownBoldToSpannable("? $explanation")
                                        textSize = 13f
                                        setPadding(0, 12.dpToPx(), 0, 0)
                                        setTextIsSelectable(true)
                                    }
                                binding.VocabMCQContainer.addView(explanationView)
                            }
                        }
                    }
            optionsContainer.addView(optionButton)
        }
        binding.VocabMCQContainer.addView(optionsContainer)

        // Next / View Results button (always show; disabled until answered)
        nextButton =
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    text =
                        if (vocabMCQCurrentIndex < vocabMCQShuffledQuestions.size - 1)
                            "Next Question"
                        else "View Results"
                    isEnabled = isAnswered
                    setOnClickListener {
                        if (!isAnswered) return@setOnClickListener
                        vocabMCQCurrentIndex++
                        displayVocabMCQQuestion()
                    }
                }
        binding.VocabMCQContainer.addView(nextButton)
    }

    private fun showVocabMCQResult() {
        vocabQuizCompleted = true
        val totalQuestions = vocabMCQShuffledQuestions.size

        // Load k?t qu? ?ã l?u n?u có (khi m? l?i)
        if (noteId != -1L) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val savedScore = prefs.getInt("note_${noteId}_vocab_mcq_score", -1)
            val savedTotal = prefs.getInt("note_${noteId}_vocab_mcq_total", -1)
            if (savedScore >= 0 && savedTotal > 0) {
                vocabMCQScore = savedScore
            }
        }

        val percentage = if (totalQuestions > 0) (vocabMCQScore * 100 / totalQuestions) else 0

        // L?u k?t qu? vào preferences
        saveQuizResults()

        val resultView =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

        // Title
        resultView.addView(
            TextView(this).apply {
                text = "Quiz Complete!"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setPadding(0, 0, 0, 16.dpToPx())
            }
        )

        // Score
        resultView.addView(
            TextView(this).apply {
                text = "Score: $vocabMCQScore / $totalQuestions ($percentage%)"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                val color =
                    if (percentage >= 80) android.R.color.holo_green_dark
                    else if (percentage >= 60) android.R.color.holo_orange_dark
                    else android.R.color.holo_red_dark
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                setPadding(0, 0, 0, 8.dpToPx())
            }
        )

        // Review button
        val reviewButton =
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    text = "Review Answers"
                    setOnClickListener { showVocabMCQReview() }
                }
        resultView.addView(reviewButton)

        // Show overall statistics if all quizzes completed
        if (vocabQuizCompleted && clozeQuizCompleted && matchPairsQuizCompleted) {
            resultView.addView(
                TextView(this).apply {
                    text = "\n--- Overall Statistics ---"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
                }
            )
            showOverallStatistics(resultView)
        }

        binding.VocabMCQContainer.addView(resultView)
    }

    private fun showVocabMCQReview() {
        binding.VocabMCQContainer.removeAllViews()

        val title =
            TextView(this).apply {
                text = "Review Answers"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16.dpToPx())
            }
        binding.VocabMCQContainer.addView(title)

        vocabMCQShuffledQuestions.forEachIndexed { index, quiz ->
            val userAnswer = vocabMCQUserAnswers[index]
            val isCorrect = userAnswer == quiz.answer

            val reviewCard =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    setBackgroundColor(
                        ContextCompat.getColor(
                            this@AISummaryActivity,
                            if (isCorrect) android.R.color.holo_green_light
                            else android.R.color.holo_red_light,
                        )
                    )
                }

            // Question
            reviewCard.addView(
                TextView(this).apply {
                    text = "Q${index + 1}: ${quiz.question}"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
            )

            // User answer
            reviewCard.addView(
                TextView(this).apply {
                    text = "Your answer: ${userAnswer ?: "Not answered"}"
                    textSize = 14f
                    setPadding(0, 0, 0, 4.dpToPx())
                }
            )

            // Correct answer
            reviewCard.addView(
                TextView(this).apply {
                    text = "Correct answer: ${quiz.answer}"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
            )

            // Explanation
            quiz.explanation?.let { explanation ->
                reviewCard.addView(
                    TextView(this).apply {
                        text = "Explanation: $explanation"
                        textSize = 13f
                        setPadding(0, 0, 0, 8.dpToPx())
                    }
                )
            }

            binding.VocabMCQContainer.addView(reviewCard)
        }
    }

    private fun createVocabMCQView(quiz: VocabQuiz): View {
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dpToPx(), 0, 16.dpToPx())
            }

        // Question text (không hi?n th? Target ?? không l? ?áp án)
        val questionText =
            TextView(this).apply {
                val questionStr = quiz.question ?: ""
                text = markdownBoldToSpannable(questionStr)
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setTextIsSelectable(true)
            }
        container.addView(questionText)

        // Question type indicator
        quiz.type?.let { type ->
            container.addView(
                TextView(this).apply {
                    text = "Type: $type"
                    textSize = 11f
                    setTextColor(
                        ContextCompat.getColor(this@AISummaryActivity, android.R.color.darker_gray)
                    )
                    setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
                }
            )
        }

        // Options
        val optionsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dpToPx(), 0, 0)
            }

        var answered = false
        var explanationText: TextView? = null

        quiz.options.orEmpty().forEach { (key, value) ->
            val optionButton =
                MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    )
                    .apply {
                        text = markdownBoldToSpannable("$key: $value")
                        textSize = 13f
                        isAllCaps = false
                        layoutParams =
                            LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                )
                                .apply { topMargin = 4.dpToPx() }

                        setOnClickListener {
                            if (answered) return@setOnClickListener

                            answered = true
                            // Lock all options after first choice
                            for (i in 0 until optionsContainer.childCount) {
                                (optionsContainer.getChildAt(i) as? MaterialButton)?.isEnabled =
                                    false
                            }

                            if (key == quiz.answer) {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_green_light,
                                    )
                                )
                                Toast.makeText(
                                        this@AISummaryActivity,
                                        R.string.ai_correct,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            } else {
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_red_light,
                                    )
                                )

                                // Show when_wrong feedback if available
                                quiz.whenWrong?.let { whenWrong ->
                                    Toast.makeText(
                                            this@AISummaryActivity,
                                            whenWrong,
                                            Toast.LENGTH_LONG,
                                        )
                                        .show()
                                }
                                    ?: Toast.makeText(
                                            this@AISummaryActivity,
                                            R.string.ai_incorrect,
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()

                                for (i in 0 until optionsContainer.childCount) {
                                    val child = optionsContainer.getChildAt(i) as? MaterialButton
                                    if (
                                        child?.text?.toString()?.startsWith(quiz.answer ?: "") ==
                                            true
                                    ) {
                                        child.setBackgroundColor(
                                            ContextCompat.getColor(
                                                this@AISummaryActivity,
                                                android.R.color.holo_green_light,
                                            )
                                        )
                                    }
                                }
                            }

                            explanationText?.visibility = View.VISIBLE
                        }
                    }
            optionsContainer.addView(optionButton)
        }
        container.addView(optionsContainer)

        // Explanation (shown after answer)
        quiz.explanation?.let { explanation ->
            explanationText =
                TextView(this).apply {
                    text = markdownBoldToSpannable("? $explanation")
                    textSize = 13f
                    setPadding(0, 12.dpToPx(), 0, 0)
                    visibility = View.GONE
                    setTextIsSelectable(true)
                }
            container.addView(explanationText)
        }

        return container
    }

    private fun updateFlashcardsCard(cards: List<Flashcard>?) {
        val items = cards.orEmpty()
        binding.FlashcardsCard.isVisible = items.isNotEmpty()
        binding.FlashcardsContainer.removeAllViews()
        if (items.isEmpty()) {
            flashcardsOriginal = null
            return
        }

        // L?u d? li?u g?c ?? dùng cho translate
        flashcardsOriginal = items

        flashcardIndex = flashcardIndex.coerceIn(0, items.size - 1)
        val card = items[flashcardIndex]
        val word = card.word ?: card.front ?: ""
        val back = card.back

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            }

        container.addView(
            TextView(this).apply {
                text = word
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextIsSelectable(true)
            }
        )

        if (flashcardFlipped && back != null) {
            fun addLine(label: String, value: String?) {
                if (value.isNullOrBlank()) return
                val text = "$label: $value"
                val spannable = SpannableString(text)
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    label.length + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                container.addView(
                    TextView(this).apply {
                        setText(spannable)
                        textSize = 14f
                        setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                        setTextIsSelectable(true)
                    }
                )
            }
            addLine(getString(R.string.meaning), back.meaning)
            addLine(getString(R.string.example), back.example)
            addLine(getString(R.string.note), back.usageNote)
            if (!back.synonyms.isNullOrEmpty())
                addLine("Synonyms", back.synonyms.joinToString(", "))
            if (!back.antonyms.isNullOrEmpty())
                addLine("Antonyms", back.antonyms.joinToString(", "))
            back.quickTip?.let { addLine("Quick Tip", it) }
        } else {
            container.addView(
                TextView(this).apply {
                    text = getString(R.string.ai_flashcard_tap_to_flip)
                    textSize = 14f
                    setPadding(0, 8.dpToPx(), 0, 0)
                    setTextColor(
                        ContextCompat.getColor(this@AISummaryActivity, android.R.color.darker_gray)
                    )
                }
            )
        }

        binding.FlashcardsContainer.addView(container)

        binding.FlashPrevButton.setOnClickListener {
            flashcardIndex = (flashcardIndex - 1 + items.size) % items.size
            flashcardFlipped = false
            updateFlashcardsCard(items)
        }
        binding.FlashNextButton.setOnClickListener {
            flashcardIndex = (flashcardIndex + 1) % items.size
            flashcardFlipped = false
            updateFlashcardsCard(items)
        }
        binding.FlashFlipButton.setOnClickListener {
            flashcardFlipped = !flashcardFlipped
            updateFlashcardsCard(items)
        }
        binding.FlashGotItButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.ai_got_it), Toast.LENGTH_SHORT).show()
        }
        binding.FlashAgainButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.ai_again), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFlashcardView(card: Flashcard): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dpToPx(), 0, 12.dpToPx())

            // Don't show word - already shown in Processed Text

            card.back?.let { back ->
                fun addLine(label: String, value: String?) {
                    if (value.isNullOrBlank()) return
                    val text = "$label: $value"
                    val spannable = markdownBoldToSpannable(text)
                    // Make label bold
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        label.length + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    addView(
                        TextView(context).apply {
                            setText(spannable)
                            textSize = 13f
                            setPadding(0, 4.dpToPx(), 0, 0)
                            setTextIsSelectable(true)
                        }
                    )
                }

                // Format meaning: if it contains multiple words, format each on a new line
                val meaning = back.meaning
                if (!meaning.isNullOrBlank()) {
                    // Check if meaning contains multiple word definitions (format: "word:
                    // definition")
                    val wordPattern = Regex("([A-Za-z]+(?:\\s+[A-Za-z]+)*):\\s*([^?]+)")
                    val matches = wordPattern.findAll(meaning)
                    if (matches.count() > 1) {
                        // Multiple words - format each on a new line
                        val meaningBuilder = StringBuilder()
                        matches.forEachIndexed { index, match ->
                            if (index > 0) {
                                meaningBuilder.append("\n")
                            }
                            meaningBuilder
                                .append("? ")
                                .append(match.groupValues[1])
                                .append(": ")
                                .append(match.groupValues[2].trim())
                        }
                        val meaningText = meaningBuilder.toString()
                        val fullText = "${getString(R.string.meaning)}:\n$meaningText"
                        val spannable = SpannableString(fullText)

                        // Make "Meaning:" label bold
                        val labelLength = getString(R.string.meaning).length + 1
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            labelLength,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )

                        // Make each word bold
                        val pattern = Regex("•\\s+([^:]+):")
                        pattern.findAll(meaningText).forEach { match ->
                            val wordStart = labelLength + 1 + match.range.first
                            val wordEnd = labelLength + 1 + match.range.last - 1
                            if (
                                wordStart >= 0 && wordEnd < spannable.length && wordStart < wordEnd
                            ) {
                                spannable.setSpan(
                                    StyleSpan(Typeface.BOLD),
                                    wordStart,
                                    wordEnd,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                )
                            }
                        }

                        addView(
                            TextView(context).apply {
                                setText(spannable)
                                textSize = 13f
                                setPadding(0, 4.dpToPx(), 0, 0)
                                setTextIsSelectable(true)
                            }
                        )
                    } else {
                        // Single word - use normal format
                        addLine(getString(R.string.meaning), meaning)
                    }
                }

                addLine(getString(R.string.example), back.example)
                addLine(getString(R.string.note), back.usageNote)
                if (!back.synonyms.isNullOrEmpty()) {
                    addLine("Synonyms", back.synonyms.joinToString(", "))
                }
                if (!back.antonyms.isNullOrEmpty()) {
                    addLine("Antonyms", back.antonyms.joinToString(", "))
                }
            }
        }
    }

    private fun updateMindmapCard(mindmap: MindmapBundle?) {
        binding.MindmapCard.isVisible = mindmap != null
        binding.MindmapContainer.removeAllViews()
        mindmap ?: return

        fun addSection(title: String, description: String?, words: List<String>?) {
            if (words.isNullOrEmpty() && description.isNullOrBlank()) return

            val sectionContainer =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8.dpToPx(), 0, 12.dpToPx())
                }

            // Section title with bullet
            sectionContainer.addView(
                TextView(this).apply {
                    text = "? $title"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(
                        ContextCompat.getColor(this@AISummaryActivity, android.R.color.black)
                    )
                    setPadding(0, 0, 0, 4.dpToPx())
                }
            )

            // Description
            description?.let {
                sectionContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(it)
                        textSize = 13f
                        setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
                        setTextIsSelectable(true)
                    }
                )
            }

            // Words list
            if (!words.isNullOrEmpty()) {
                sectionContainer.addView(
                    TextView(this).apply {
                        text = words.joinToString(", ")
                        textSize = 14f
                        setPadding(0, 4.dpToPx(), 0, 0)
                        setTextIsSelectable(true)
                    }
                )
            }

            binding.MindmapContainer.addView(sectionContainer)
        }

        mindmap.byTopic.orEmpty().forEach { group ->
            addSection("Ch? ??: ${group.topic ?: ""}", group.description, group.words)
        }
        mindmap.byDifficulty.orEmpty().forEach { group ->
            addSection("M?c ??: ${group.level ?: ""}", group.description, group.words)
        }
        mindmap.byPos.orEmpty().forEach { group ->
            addSection("Lo?i t?: ${group.pos ?: ""}", null, group.words)
        }
        mindmap.byRelation.orEmpty().forEach { group ->
            val groupName = group.groupName ?: "Relation"

            // Handle synonyms with clusters
            if (groupName.lowercase() == "synonyms" && !group.clusters.isNullOrEmpty()) {
                val sectionContainer =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 8.dpToPx(), 0, 12.dpToPx())
                    }

                sectionContainer.addView(
                    TextView(this).apply {
                        text = "? $groupName"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(
                            ContextCompat.getColor(this@AISummaryActivity, android.R.color.black)
                        )
                        setPadding(0, 0, 0, 4.dpToPx())
                    }
                )

                group.description?.let {
                    sectionContainer.addView(
                        TextView(this).apply {
                            text = markdownBoldToSpannable(it)
                            textSize = 13f
                            setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
                            setTextIsSelectable(true)
                        }
                    )
                }

                group.clusters.forEach { cluster ->
                    sectionContainer.addView(
                        TextView(this).apply {
                            text = "  Cluster: ${cluster.joinToString(", ")}"
                            textSize = 14f
                            setPadding(0, 2.dpToPx(), 0, 2.dpToPx())
                            setTextIsSelectable(true)
                        }
                    )
                }

                binding.MindmapContainer.addView(sectionContainer)
            }
            // Handle antonyms with pairs
            else if (groupName.lowercase() == "antonyms" && !group.pairs.isNullOrEmpty()) {
                val sectionContainer =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 8.dpToPx(), 0, 12.dpToPx())
                    }

                sectionContainer.addView(
                    TextView(this).apply {
                        text = "? $groupName"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(
                            ContextCompat.getColor(this@AISummaryActivity, android.R.color.black)
                        )
                        setPadding(0, 0, 0, 4.dpToPx())
                    }
                )

                group.description?.let {
                    sectionContainer.addView(
                        TextView(this).apply {
                            text = markdownBoldToSpannable(it)
                            textSize = 13f
                            setPadding(0, 2.dpToPx(), 0, 4.dpToPx())
                            setTextIsSelectable(true)
                        }
                    )
                }

                group.pairs.forEach { pair ->
                    if (pair.size >= 2) {
                        sectionContainer.addView(
                            TextView(this).apply {
                                text = "  ${pair[0]} ? ${pair[1]}"
                                textSize = 14f
                                setPadding(0, 2.dpToPx(), 0, 2.dpToPx())
                                setTextIsSelectable(true)
                            }
                        )
                    }
                }

                binding.MindmapContainer.addView(sectionContainer)
            }
            // Default: use words list
            else {
                addSection(groupName, group.description, group.words)
            }
        }
    }

    private fun updateClozeCard(clozeTests: List<ClozeTest>?) {
        binding.ClozeCard.isVisible = !clozeTests.isNullOrEmpty()
        binding.ClozeContainer.removeAllViews()
        clozeTests ?: return

        // L?y set_id t? cloze_tests ??u tiên (t?t c? ??u có cùng set_id)
        val currentSetId = clozeTests.firstOrNull()?.setId

        // Check xem set_id có thay ??i không (n?u có thì reset progress)
        if (noteId != -1L) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val savedSetId = prefs.getInt("note_${noteId}_cloze_set_id", -1)
            if (currentSetId != null && savedSetId != -1 && currentSetId != savedSetId) {
                // set_id ?ã thay ??i ? reset progress
                android.util.Log.d(
                    "AISummaryActivity",
                    "updateClozeCard: set_id changed ($savedSetId -> $currentSetId), resetting progress",
                )
                prefs.edit().apply {
                    remove("note_${noteId}_cloze_completed")
                    remove("note_${noteId}_cloze_score")
                    remove("note_${noteId}_cloze_total")
                    remove("note_${noteId}_cloze_set_id")
                    remove("note_${noteId}_cloze_order")
                    remove("note_${noteId}_cloze_answers")
                    apply()
                }
                // Clear in-memory state to avoid showing stale data
                clozeAllQuestions.clear()
                clozeShuffledQuestions.clear()
                clozeUserAnswersQuiz.clear()
                clozeScore = 0
                clozeCurrentIndex = 0
                clozeQuizCompleted = false
            }
        }

        // Ki?m tra xem quiz ?ã hoàn thành ch?a (sau khi ?ã reset n?u c?n)
        val isCompleted = checkQuizCompleted("cloze")
        if (isCompleted) {
            // Load k?t qu? ?ã l?u và hi?n th?
            clozeQuizCompleted = true
            if (noteId != -1L) {
                val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
                clozeScore = prefs.getInt("note_${noteId}_cloze_score", 0)
                // Khôi ph?c th? t? câu h?i và ?áp án ?ã ch?n
                val gson = Gson()
                val savedOrderJson = prefs.getString("note_${noteId}_cloze_order", null)
                val savedAnswersJson = prefs.getString("note_${noteId}_cloze_answers", null)

                val idOrder: List<Int>? =
                    try {
                        savedOrderJson?.let { gson.fromJson(it, Array<Int>::class.java)?.toList() }
                    } catch (_: Exception) {
                        null
                    }

                val answersById: Map<Int, String>? =
                    try {
                        savedAnswersJson?.let {
                            gson.fromJson(
                                it,
                                object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}
                                    .type,
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }

                // Flatten cloze -> blanks to list<Pair<ClozeTest, ClozeBlank>>
                val allPairs = mutableListOf<Pair<ClozeTest, ClozeBlank>>()
                clozeTests.forEach { c ->
                    c.blanks?.forEach { b ->
                        if (b.id != null && b.answer != null) {
                            allPairs.add(Pair(c, b))
                        }
                    }
                }

                // Reorder by saved id order if available
                val ordered =
                    if (!idOrder.isNullOrEmpty()) {
                        allPairs.sortedBy { pair -> idOrder.indexOf(pair.second.id ?: -1) }
                    } else {
                        allPairs
                    }

                clozeAllQuestions = ordered.toMutableList()
                clozeShuffledQuestions = ordered.toMutableList()

                // Khôi ph?c câu tr? l?i ng??i dùng theo id
                clozeUserAnswersQuiz.clear()
                if (!answersById.isNullOrEmpty()) {
                    clozeShuffledQuestions.forEachIndexed { idx, (_, blank) ->
                        val bid = blank.id
                        if (bid != null) {
                            answersById[bid]?.let { ans -> clozeUserAnswersQuiz[idx] = ans }
                        }
                    }
                }
            }
            showClozeResult()
            return
        }

        // L?u set_id ngay khi load data (?? check khi save)
        if (noteId != -1L && currentSetId != null) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            prefs.edit().putInt("note_${noteId}_cloze_set_id", currentSetId).apply()
        }

        // Checklist mode: hi?n th? t?ng câu h?i ng?u nhiên, tính ?i?m
        // Tách m?i blank thành m?t câu h?i riêng
        clozeAllQuestions.clear()
        clozeTests.forEachIndexed { clozeIdx, cloze ->
            cloze.blanks?.forEach { blank ->
                if (blank.answer != null) {
                    clozeAllQuestions.add(Pair(cloze, blank))
                }
            }
        }

        clozeShuffledQuestions = clozeAllQuestions.shuffled().toMutableList()
        clozeCurrentIndex = 0
        clozeUserAnswersQuiz.clear()
        clozeScore = 0
        clozeIsQuizMode = true
        clozeQuizCompleted = false

        displayClozeQuestion()
    }

    private fun displayClozeQuestion() {
        binding.ClozeContainer.removeAllViews()

        if (clozeCurrentIndex >= clozeShuffledQuestions.size) {
            // H?t câu h?i, hi?n th? k?t qu?
            showClozeResult()
            return
        }

        val (cloze, blank) = clozeShuffledQuestions[clozeCurrentIndex]
        val questionNumber = clozeCurrentIndex + 1
        val totalQuestions = clozeShuffledQuestions.size
        val answer = blank.answer ?: ""

        // Header: Question X of Y
        val header =
            TextView(this).apply {
                text = "Question $questionNumber / $totalQuestions"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setPadding(0, 0, 0, 12.dpToPx())
            }
        binding.ClozeContainer.addView(header)

        // Paragraph (câu h?i v?i blank)
        cloze.paragraph
            ?.takeIf { it.isNotBlank() }
            ?.let { paragraph ->
                val paragraphView =
                    TextView(this).apply {
                        text = paragraph
                        textSize = 14f
                        setPadding(0, 0, 0, 16.dpToPx())
                        setTextIsSelectable(true)
                    }
                binding.ClozeContainer.addView(paragraphView)
            }

        // Input field
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

        val edit =
            android.widget.EditText(this).apply {
                hint = getString(R.string.ai_fill_answer)
                setText(clozeUserAnswersQuiz[clozeCurrentIndex] ?: "")
                setTextIsSelectable(true)
            }

        val feedback =
            TextView(this).apply {
                textSize = 13f
                setPadding(0, 8.dpToPx(), 0, 0)
                visibility = View.GONE
                movementMethod = LinkMovementMethod.getInstance()
            }

        val userAnswer = clozeUserAnswersQuiz[clozeCurrentIndex]
        val isAnswered = userAnswer != null

        if (isAnswered) {
            val correct = userAnswer.equals(answer, ignoreCase = true)
            edit.isEnabled = false
            edit.setBackgroundColor(
                ContextCompat.getColor(
                    this@AISummaryActivity,
                    if (correct) android.R.color.holo_green_light
                    else android.R.color.holo_red_light,
                )
            )
            feedback.visibility = View.VISIBLE
            if (correct) {
                val example = blank.onCorrectExample ?: ""
                feedback.text = buildString {
                    append(getString(R.string.ai_correct))
                    if (example.isNotBlank()) append("\n").append(example)
                }
            } else {
                val explanation = blank.explanation ?: ""
                feedback.text = buildString {
                    append(getString(R.string.ai_incorrect))
                    append("\n").append("Correct answer: $answer")
                    if (explanation.isNotBlank()) append("\n").append(explanation)
                }
            }
        }

        row.addView(edit)
        row.addView(feedback)
        binding.ClozeContainer.addView(row)

        // Check/Next button
        val actionButton =
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    if (isAnswered) {
                        text =
                            if (clozeCurrentIndex < clozeShuffledQuestions.size - 1) "Next Question"
                            else "View Results"
                        setOnClickListener {
                            clozeCurrentIndex++
                            displayClozeQuestion()
                        }
                    } else {
                        text = getString(R.string.ai_check_answer)
                        setOnClickListener {
                            val user = edit.text.toString().trim()
                            if (user.isBlank()) {
                                Toast.makeText(
                                        this@AISummaryActivity,
                                        "Please enter an answer",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                                return@setOnClickListener
                            }

                            clozeUserAnswersQuiz[clozeCurrentIndex] = user
                            val correct = user.equals(answer, ignoreCase = true)

                            if (correct) {
                                clozeScore++
                            }
                            edit.isEnabled = false
                            edit.setBackgroundColor(
                                ContextCompat.getColor(
                                    this@AISummaryActivity,
                                    if (correct) {
                                        android.R.color.holo_green_light
                                    } else {
                                        android.R.color.holo_red_light
                                    },
                                )
                            )
                            feedback.visibility = View.VISIBLE

                            if (correct) {
                                val example = blank.onCorrectExample ?: ""
                                feedback.text = buildString {
                                    append(getString(R.string.ai_correct))
                                    if (example.isNotBlank()) append("\n").append(example)
                                }
                            } else {
                                val explanation = blank.explanation ?: ""
                                feedback.text = buildString {
                                    append(getString(R.string.ai_incorrect))
                                    append("\n").append("Correct answer: $answer")
                                    if (explanation.isNotBlank()) append("\n").append(explanation)
                                }
                            }

                            // Update button to Next
                            text =
                                if (clozeCurrentIndex < clozeShuffledQuestions.size - 1)
                                    "Next Question"
                                else "View Results"
                            setOnClickListener {
                                clozeCurrentIndex++
                                displayClozeQuestion()
                            }
                        }
                    }
                }
        binding.ClozeContainer.addView(actionButton)
    }

    private fun showClozeResult() {
        clozeQuizCompleted = true
        val totalQuestions = clozeShuffledQuestions.size
        val percentage = if (totalQuestions > 0) (clozeScore * 100 / totalQuestions) else 0

        // Load k?t qu? ?ã l?u n?u có (khi m? l?i)
        if (noteId != -1L) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val savedScore = prefs.getInt("note_${noteId}_cloze_score", -1)
            val savedTotal = prefs.getInt("note_${noteId}_cloze_total", -1)
            if (savedScore >= 0 && savedTotal > 0) {
                clozeScore = savedScore
            }
        }

        // L?u k?t qu? vào preferences
        saveQuizResults()

        val resultView =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

        // Title
        resultView.addView(
            TextView(this).apply {
                text = "Quiz Complete!"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setPadding(0, 0, 0, 16.dpToPx())
            }
        )

        // Score
        resultView.addView(
            TextView(this).apply {
                text = "Score: $clozeScore / $totalQuestions ($percentage%)"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                val color =
                    if (percentage >= 80) android.R.color.holo_green_dark
                    else if (percentage >= 60) android.R.color.holo_orange_dark
                    else android.R.color.holo_red_dark
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                setPadding(0, 0, 0, 8.dpToPx())
            }
        )

        // Review button
        val reviewButton =
            MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                )
                .apply {
                    text = "Review Answers"
                    setOnClickListener { showClozeReview() }
                }
        resultView.addView(reviewButton)

        // Show overall statistics if all quizzes completed
        if (vocabQuizCompleted && clozeQuizCompleted && matchPairsQuizCompleted) {
            resultView.addView(
                TextView(this).apply {
                    text = "\n--- Overall Statistics ---"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
                }
            )
            showOverallStatistics(resultView)
        }

        binding.ClozeContainer.addView(resultView)
    }

    private fun showClozeReview() {
        binding.ClozeContainer.removeAllViews()

        val title =
            TextView(this).apply {
                text = "Review Answers"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16.dpToPx())
            }
        binding.ClozeContainer.addView(title)

        clozeShuffledQuestions.forEachIndexed { index, (cloze, blank) ->
            val userAnswer = clozeUserAnswersQuiz[index]
            val correctAnswer = blank.answer ?: ""
            val isCorrect = userAnswer?.equals(correctAnswer, ignoreCase = true) == true

            val reviewCard =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    setBackgroundColor(
                        ContextCompat.getColor(
                            this@AISummaryActivity,
                            if (isCorrect) android.R.color.holo_green_light
                            else android.R.color.holo_red_light,
                        )
                    )
                }

            // Question
            reviewCard.addView(
                TextView(this).apply {
                    text = "Q${index + 1}: ${cloze.paragraph}"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
            )

            // User answer
            reviewCard.addView(
                TextView(this).apply {
                    text = "Your answer: ${userAnswer ?: "Not answered"}"
                    textSize = 14f
                    setPadding(0, 0, 0, 4.dpToPx())
                }
            )

            // Correct answer
            reviewCard.addView(
                TextView(this).apply {
                    text = "Correct answer: $correctAnswer"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
            )

            // Explanation
            blank.explanation?.let { explanation ->
                reviewCard.addView(
                    TextView(this).apply {
                        text = "Explanation: $explanation"
                        textSize = 13f
                        setPadding(0, 0, 0, 8.dpToPx())
                    }
                )
            }

            binding.ClozeContainer.addView(reviewCard)
        }
    }

    private fun saveQuizResults() {
        // L?u k?t qu? vào preferences ?? tracking
        if (noteId != -1L) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            if (vocabQuizCompleted) {
                editor.putBoolean("note_${noteId}_vocab_mcq_completed", true)
                editor.putInt("note_${noteId}_vocab_mcq_score", vocabMCQScore)
                editor.putInt("note_${noteId}_vocab_mcq_total", vocabMCQShuffledQuestions.size)
                // L?u th? t? câu h?i và ?áp án (?? xem l?i v?n ?úng màu)
                val idOrder = vocabMCQShuffledQuestions.map { it.id ?: -1 }
                val answersById = mutableMapOf<Int, String>()
                vocabMCQUserAnswers.forEach { (idx, ans) ->
                    idOrder.getOrNull(idx)?.let { qid -> if (qid != -1) answersById[qid] = ans }
                }
                val gson = Gson()
                editor.putString("note_${noteId}_vocab_mcq_order", gson.toJson(idOrder))
                editor.putString("note_${noteId}_vocab_mcq_answers", gson.toJson(answersById))
            }

            if (clozeQuizCompleted) {
                editor.putBoolean("note_${noteId}_cloze_completed", true)
                editor.putInt("note_${noteId}_cloze_score", clozeScore)
                editor.putInt("note_${noteId}_cloze_total", clozeShuffledQuestions.size)
                // set_id ?ã ???c l?u khi updateClozeCard
                // L?u th? t? blank id và ?áp án ng??i dùng
                val idOrder = clozeShuffledQuestions.map { it.second.id ?: -1 }
                val answersById = mutableMapOf<Int, String>()
                clozeShuffledQuestions.forEachIndexed { idx, (_, blank) ->
                    val bid = blank.id ?: return@forEachIndexed
                    clozeUserAnswersQuiz[idx]?.let { ans -> answersById[bid] = ans }
                }
                val gson = Gson()
                editor.putString("note_${noteId}_cloze_order", gson.toJson(idOrder))
                editor.putString("note_${noteId}_cloze_answers", gson.toJson(answersById))
            }

            if (matchPairsQuizCompleted) {
                editor.putBoolean("note_${noteId}_match_pairs_completed", true)
                editor.putInt("note_${noteId}_match_pairs_score", matchPairsScore)
                editor.putInt("note_${noteId}_match_pairs_total", matchPairsTotal)
                // set_id ?ã ???c l?u khi updateMatchPairsCard
            }

            // L?u progress chi ti?t per-vocab cho Match Pairs
            if (matchPairsUniqueWords.isNotEmpty()) {
                try {
                    val progressList = matchPairsVocabProgress.values.toList()
                    val json = Gson().toJson(progressList)
                    editor.putString("note_${noteId}_match_pairs_vocab_progress", json)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "AISummaryActivity",
                        "saveQuizResults: error saving match pairs vocab progress",
                        e,
                    )
                }
            }

            editor.apply()

            // Sau khi l?u, c?p nh?t l?i Overall Progress Card ?? ph?n ánh ?i?m m?i
            updateOverallProgressCardSummary()
        }
    }

    /** Load progress chi ti?t Match Pairs theo vocab t? SharedPreferences */
    private fun loadMatchPairsVocabProgress() {
        matchPairsVocabProgress.clear()
        if (noteId == -1L) return

        try {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val json = prefs.getString("note_${noteId}_match_pairs_vocab_progress", null)
            if (!json.isNullOrBlank()) {
                val gson = Gson()
                val arr = gson.fromJson(json, Array<MatchPairVocabProgress>::class.java)
                arr?.forEach { prog ->
                    val key = prog.vocab.lowercase().trim()
                    if (key.isNotBlank()) {
                        matchPairsVocabProgress[key] = prog
                        if (prog.status == "completed") {
                            matchPairsWordsMatched.add(key)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AISummaryActivity", "loadMatchPairsVocabProgress: error", e)
        }
    }

    private fun showOverallStatistics(container: LinearLayout) {
        // N?u ?ang ? ch? ?? statsOnly: tính toán tr?c ti?p t? SummaryResponse + SharedPreferences
        if (statsOnly && noteId != -1L && summaryResponse != null) {
            val vocabStats = mutableMapOf<String, VocabStat>()
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val gson = Gson()

            val resp = summaryResponse!!

            // ---- MCQ t? prefs ----
            val mcqs = (resp.vocabMcqs ?: resp.review?.vocabMcqs).orEmpty()
            if (mcqs.isNotEmpty()) {
                val answersJson = prefs.getString("note_${noteId}_vocab_mcq_answers", null)
                val answersById: Map<Int, String> =
                    try {
                        answersJson?.let {
                            gson.fromJson(
                                it,
                                object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}
                                    .type,
                            )
                        } ?: emptyMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }

                mcqs.forEach { quiz ->
                    val vocab = quiz.vocabTarget?.lowercase()?.trim() ?: return@forEach
                    val questionType = quiz.questionType?.lowercase()?.trim()
                    val weight =
                        when (questionType) {
                            "meaning" -> 1
                            "context" -> 1
                            else -> 1
                        }
                    val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                    stat.maxPoints += weight
                    val qid = quiz.id
                    if (qid != null && answersById[qid] == quiz.answer) {
                        stat.earnedPoints += weight
                    }
                }
            }

            // ---- Cloze t? prefs ----
            val clozeTests = (resp.clozeTests ?: resp.review?.clozeTests).orEmpty()
            if (clozeTests.isNotEmpty()) {
                val answersJson = prefs.getString("note_${noteId}_cloze_answers", null)
                val answersById: Map<Int, String> =
                    try {
                        answersJson?.let {
                            gson.fromJson(
                                it,
                                object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}
                                    .type,
                            )
                        } ?: emptyMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }

                clozeTests.forEach { cloze ->
                    cloze.blanks?.forEach { blank ->
                        val bid = blank.id ?: return@forEach
                        val ans = blank.answer ?: return@forEach
                        val vocab = (cloze.vocab?.lowercase()?.trim() ?: ans.lowercase().trim())
                        val clozeType = cloze.type?.lowercase()?.trim()
                        val weight =
                            when (clozeType) {
                                "basic_usage" -> 2
                                "context_usage" -> 2
                                else -> 2
                            }
                        val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                        stat.maxPoints += weight
                        if (answersById[bid]?.equals(ans, ignoreCase = true) == true) {
                            stat.earnedPoints += weight
                        }
                    }
                }
            }

            // ---- Match Pairs t? prefs ----
            val progressJson = prefs.getString("note_${noteId}_match_pairs_vocab_progress", null)
            if (!progressJson.isNullOrBlank()) {
                try {
                    val arr = gson.fromJson(progressJson, Array<MatchPairVocabProgress>::class.java)
                    arr?.forEach { prog ->
                        val vocab = prog.vocab.lowercase().trim()
                        if (vocab.isNotBlank()) {
                            val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                            stat.maxPoints += 1
                            if (prog.status == "completed") {
                                stat.earnedPoints += 1
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore parse errors
                }
            }

            // T?ng ?i?m cho c? ghi chú
            var totalEarned = 0
            var totalMax = 0
            vocabStats.values.forEach { s ->
                totalEarned += s.earnedPoints
                totalMax += s.maxPoints
            }
            val overallPercentage = if (totalMax > 0) (totalEarned * 100 / totalMax) else 0

            container.addView(
                TextView(this).apply {
                    text = "\nTotal Mastery Score: $totalEarned / $totalMax ($overallPercentage%)"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    val color =
                        if (overallPercentage >= 80) {
                            android.R.color.holo_green_dark
                        } else if (overallPercentage >= 60) {
                            android.R.color.holo_orange_dark
                        } else {
                            android.R.color.holo_red_dark
                        }
                    setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                    setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
                }
            )

            // Th?ng kê theo t? v?ng
            container.addView(
                TextView(this).apply {
                    text = "\nVocabulary Statistics:"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                }
            )

            vocabStats.values
                .sortedByDescending { it.percentage }
                .forEach { stat ->
                    container.addView(
                        TextView(this).apply {
                            text =
                                "${stat.vocab}: ${stat.earnedPoints}/${stat.maxPoints} (${stat.percentage}%)"
                            textSize = 14f
                            val color =
                                if (stat.percentage >= 80) {
                                    android.R.color.holo_green_dark
                                } else if (stat.percentage >= 60) {
                                    android.R.color.holo_orange_dark
                                } else {
                                    android.R.color.holo_red_dark
                                }
                            setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                            setPadding(0, 2.dpToPx(), 0, 2.dpToPx())
                        }
                    )
                }
            return
        }

        // M?c ??nh: dùng state hi?n t?i (khi ?ang ? màn quiz)
        val vocabStats = mutableMapOf<String, VocabStat>()

        // Vocab MCQ stats (weighted)
        vocabMCQShuffledQuestions.forEachIndexed { index, quiz ->
            val vocab = quiz.vocabTarget?.lowercase()?.trim() ?: return@forEachIndexed
            val questionType = quiz.questionType?.lowercase()?.trim()
            val weight =
                when (questionType) {
                    "meaning" -> 1
                    "context" -> 1
                    else -> 1
                }
            val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
            stat.maxPoints += weight
            if (vocabMCQUserAnswers[index] == quiz.answer) {
                stat.earnedPoints += weight
            }
        }

        // Cloze stats (weighted)
        clozeShuffledQuestions.forEachIndexed { index, (cloze, blank) ->
            val vocab =
                (cloze.vocab?.lowercase()?.trim() ?: blank.answer?.lowercase()?.trim())
                    ?: return@forEachIndexed
            val clozeType = cloze.type?.lowercase()?.trim()
            val weight =
                when (clozeType) {
                    "basic_usage" -> 2
                    "context_usage" -> 2
                    else -> 2
                }
            val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
            stat.maxPoints += weight
            if (clozeUserAnswersQuiz[index]?.equals(blank.answer, ignoreCase = true) == true) {
                stat.earnedPoints += weight
            }
        }

        // Match Pairs stats
        if (matchPairsUniqueWords.isNotEmpty()) {
            val matchPairsPercentage =
                if (matchPairsTotal > 0) (matchPairsScore * 100 / matchPairsTotal) else 0
            if (matchPairsTotal > 0) {
                container.addView(
                    TextView(this).apply {
                        text =
                            "Match Pairs: $matchPairsScore / $matchPairsTotal ($matchPairsPercentage%)"
                        textSize = 14f
                        setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                    }
                )
            }

            matchPairsUniqueWords.forEach { vocab ->
                val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                stat.maxPoints += 1
                if (matchPairsWordsMatched.contains(vocab)) {
                    stat.earnedPoints += 1
                }
            }
        }

        var totalEarned = 0
        var totalMax = 0
        vocabStats.values.forEach { s ->
            totalEarned += s.earnedPoints
            totalMax += s.maxPoints
        }
        val overallPercentage = if (totalMax > 0) (totalEarned * 100 / totalMax) else 0

        container.addView(
            TextView(this).apply {
                text = "\nTotal Mastery Score: $totalEarned / $totalMax ($overallPercentage%)"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                val color =
                    if (overallPercentage >= 80) {
                        android.R.color.holo_green_dark
                    } else if (overallPercentage >= 60) {
                        android.R.color.holo_orange_dark
                    } else {
                        android.R.color.holo_red_dark
                    }
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            }
        )

        container.addView(
            TextView(this).apply {
                text = "\nVocabulary Statistics:"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
        )

        vocabStats.values
            .sortedByDescending { it.percentage }
            .forEach { stat ->
                container.addView(
                    TextView(this).apply {
                        text =
                            "${stat.vocab}: ${stat.earnedPoints}/${stat.maxPoints} (${stat.percentage}%)"
                        textSize = 14f
                        val color =
                            if (stat.percentage >= 80) {
                                android.R.color.holo_green_dark
                            } else if (stat.percentage >= 60) {
                                android.R.color.holo_orange_dark
                            } else {
                                android.R.color.holo_red_dark
                            }
                        setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                        setPadding(0, 2.dpToPx(), 0, 2.dpToPx())
                    }
                )
            }
    }

    /**
     * C?p nh?t Overall Progress Card ? ??u ghi chú (summary ng?n g?n) S? d?ng cùng tr?ng s? nh?
     * showOverallStatistics nh?ng ch? hi?n th?:
     * - Note Progress (%)
     * - S? vocab ?ã master / t?ng vocab
     */
    private fun updateOverallProgressCardSummary() {
        // N?u ch?a có d? li?u vocab/quizzes thì ?n card
        if (
            vocabMCQShuffledQuestions.isEmpty() &&
                clozeShuffledQuestions.isEmpty() &&
                matchPairsUniqueWords.isEmpty()
        ) {
            binding.OverallProgressCard.isVisible = false
            return
        }

        val vocabStats = mutableMapOf<String, VocabStat>()

        // MCQ (meaning/context) - weight 1
        vocabMCQShuffledQuestions.forEachIndexed { index, quiz ->
            val vocab = quiz.vocabTarget?.lowercase()?.trim() ?: return@forEachIndexed
            val questionType = quiz.questionType?.lowercase()?.trim()
            val weight =
                when (questionType) {
                    "meaning" -> 1
                    "context" -> 1
                    else -> 1
                }
            val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
            stat.maxPoints += weight
            if (vocabMCQUserAnswers[index] == quiz.answer) {
                stat.earnedPoints += weight
            }
        }

        // Cloze (basic/context) - weight 2
        clozeShuffledQuestions.forEachIndexed { index, (cloze, blank) ->
            val vocab =
                (cloze.vocab?.lowercase()?.trim() ?: blank.answer?.lowercase()?.trim())
                    ?: return@forEachIndexed
            val clozeType = cloze.type?.lowercase()?.trim()
            val weight =
                when (clozeType) {
                    "basic_usage" -> 2
                    "context_usage" -> 2
                    else -> 2
                }
            val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
            stat.maxPoints += weight
            if (clozeUserAnswersQuiz[index]?.equals(blank.answer, ignoreCase = true) == true) {
                stat.earnedPoints += weight
            }
        }

        // Match Pairs - weight 1 theo t?
        if (matchPairsUniqueWords.isNotEmpty()) {
            matchPairsUniqueWords.forEach { vocab ->
                val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                stat.maxPoints += 1
                if (matchPairsWordsMatched.contains(vocab)) {
                    stat.earnedPoints += 1
                }
            }
        }

        if (vocabStats.isEmpty()) {
            binding.OverallProgressCard.isVisible = false
            return
        }

        var totalEarned = 0
        var totalMax = 0
        vocabStats.values.forEach { s ->
            totalEarned += s.earnedPoints
            totalMax += s.maxPoints
        }

        if (totalMax == 0) {
            binding.OverallProgressCard.isVisible = false
            return
        }

        val overallPercentage = (totalEarned * 100 / totalMax)
        val masteredCount =
            vocabStats.values.count { it.earnedPoints >= it.maxPoints && it.maxPoints > 0 }
        val totalVocab = vocabStats.size

        binding.OverallProgressCard.isVisible = true
        binding.OverallProgressTitle.text = "Note Progress: $overallPercentage%"
        binding.OverallProgressSubtitle.text = "Mastered $masteredCount / $totalVocab vocab"
    }

    private data class VocabStat(
        val vocab: String,
        var earnedPoints: Int = 0,
        var maxPoints: Int = 0,
    ) {
        val percentage: Int
            get() = if (maxPoints > 0) (earnedPoints * 100 / maxPoints) else 0
    }

    private fun updateMatchPairsCard(pairs: List<MatchPair>?) {
        binding.MatchPairsCard.isVisible = !pairs.isNullOrEmpty()
        binding.MatchPairsContainer.removeAllViews()

        if (pairs.isNullOrEmpty()) {
            android.util.Log.d("AISummaryActivity", "updateMatchPairsCard: pairs is null or empty")
            return
        }

        android.util.Log.d("AISummaryActivity", "updateMatchPairsCard: pairs count=${pairs.size}")

        // L?y set_id t? match_pairs ??u tiên (t?t c? ??u có cùng set_id)
        val currentSetId = pairs.firstOrNull()?.setId

        // Check xem set_id có thay ??i không (n?u có thì reset progress)
        if (noteId != -1L) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            val savedSetId = prefs.getInt("note_${noteId}_match_pairs_set_id", -1)
            if (currentSetId != null && savedSetId != -1 && currentSetId != savedSetId) {
                // set_id ?ã thay ??i ? reset progress
                android.util.Log.d(
                    "AISummaryActivity",
                    "updateMatchPairsCard: set_id changed ($savedSetId -> $currentSetId), resetting progress",
                )
                prefs.edit().apply {
                    remove("note_${noteId}_match_pairs_completed")
                    remove("note_${noteId}_match_pairs_score")
                    remove("note_${noteId}_match_pairs_total")
                    remove("note_${noteId}_match_pairs_set_id")
                    remove("note_${noteId}_match_pairs_vocab_progress")
                    apply()
                }
                // Clear in-memory state to avoid showing stale data
                matchPairsAll = null
                matchPairsState = null
                matchPairsWordsMatched.clear()
                matchPairsVocabProgress.clear()
                matchPairsUniqueWords = emptySet()
                matchPairsScore = 0
                matchPairsTotal = 0
                matchPairsQuizCompleted = false
            }
        }

        // L?u toàn b? pairs
        matchPairsAll = pairs.toMutableList()

        // L?y t?t c? các t? unique ?? tính ?i?m theo t?
        matchPairsUniqueWords =
            pairs.mapNotNull { it.word?.lowercase()?.trim() }.filter { it.isNotBlank() }.toSet()

        // Kh?i t?o total theo s? vocab unique
        matchPairsTotal = matchPairsUniqueWords.size

        // L?u set_id ngay khi load data (?? check khi save)
        if (noteId != -1L && currentSetId != null) {
            val prefs = getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
            prefs.edit().putInt("note_${noteId}_match_pairs_set_id", currentSetId).apply()
        }

        // Load progress ?ã l?u (n?u có)
        matchPairsWordsMatched.clear()
        matchPairsVocabProgress.clear()
        loadMatchPairsVocabProgress()

        // ??m b?o t?t c? vocab ??u có entry trong progress
        matchPairsUniqueWords.forEach { vocab ->
            val key = vocab.lowercase().trim()
            if (key.isNotBlank() && !matchPairsVocabProgress.containsKey(key)) {
                matchPairsVocabProgress[key] = MatchPairVocabProgress(vocab = key)
            }
        }

        // C?p nh?t score t? progress (s? vocab ?ã completed)
        matchPairsWordsMatched =
            matchPairsVocabProgress.values
                .filter { it.status == "completed" }
                .map { it.vocab.lowercase().trim() }
                .toMutableSet()
        matchPairsScore = matchPairsWordsMatched.size

        // N?u t?t c? vocab ??u completed -> quiz hoàn thành
        if (matchPairsWordsMatched.size >= matchPairsUniqueWords.size && matchPairsTotal > 0) {
            matchPairsQuizCompleted = true
            saveQuizResults()
            showMatchPairsResult()
            return
        } else {
            matchPairsQuizCompleted = false
        }

        // L?y ngh?a ti?ng Vi?t t? summaryTable d?a trên word
        fun getVietnameseTranslation(word: String?): String? {
            if (word.isNullOrBlank()) return null
            // Tìm translation t? summaryTable
            return summaryTableState
                ?.firstOrNull { it.word?.equals(word, ignoreCase = true) == true }
                ?.translation
        }

        // Rút g?n ngh?a ?? hi?n th? ng?n g?n (t?i ?a 25 ký t? ?? ?? hi?n th? trong ô)
        fun shortenMeaning(meaning: String?): String {
            if (meaning.isNullOrBlank()) return ""
            val text = meaning.trim()
            // Lo?i b? các prefix nh? "Ngh?a c?a", "Ý ngh?a", v.v.
            val cleaned =
                text.replace(
                    Regex(
                        "^(Ngh?a c?a|Ý ngh?a|Ngh?a|Meaning of|Meaning|con|Con)\\s*:?\\s*",
                        RegexOption.IGNORE_CASE,
                    ),
                    "",
                )
            // L?y ph?n ??u tiên, lo?i b? d?u câu
            val firstPart = cleaned.split(Regex("[,?.?()??\\-–—]")).firstOrNull()?.trim() ?: cleaned
            // Gi?i h?n 25 ký t? ?? v?a v?i ô
            return if (firstPart.length > 25) firstPart.take(25).trim() + "..." else firstPart
        }

        /**
         * Render m?t l??t (round) 4x4 v?i t?i ?a 8 t? v?ng, ?u tiên các t? CH?A completed, ch?m
         * ?i?m theo t?.
         *
         * Sau khi hoàn thành h?t các t? trong round hi?n t?i:
         * - N?u v?n còn t? ch?a hoàn thành trong note ? t? ??ng render round m?i.
         * - N?u ?ã hoàn thành h?t t?t c? t? ? hi?n th? k?t qu? cu?i cùng.
         */
        var currentRoundWords: MutableSet<String> = mutableSetOf()

        fun renderRound() {
            val allPairs = matchPairsAll ?: return

            // N?u ?ã hoàn thành h?t thì hi?n th? k?t qu?
            if (
                matchPairsQuizCompleted || matchPairsWordsMatched.size >= matchPairsUniqueWords.size
            ) {
                matchPairsQuizCompleted = true
                saveQuizResults()
                showMatchPairsResult()
                return
            }

            // Danh sách vocab ch?a hoàn thành
            val unfinishedVocab =
                matchPairsUniqueWords.filter { vocab ->
                    val prog = matchPairsVocabProgress[vocab]
                    prog?.status != "completed"
                }

            if (unfinishedVocab.isEmpty()) {
                matchPairsQuizCompleted = true
                saveQuizResults()
                showMatchPairsResult()
                return
            }

            // ?u tiên vocab có attempts cao h?n (b? sai nhi?u) r?i shuffle và l?y t?i ?a 8
            val sortedByAttempts =
                unfinishedVocab.sortedByDescending { matchPairsVocabProgress[it]?.attempts ?: 0 }
            val vocabForRound =
                if (sortedByAttempts.size <= 8) sortedByAttempts.shuffled()
                else sortedByAttempts.shuffled().take(8)

            // L?u l?i danh sách vocab c?a round hi?n t?i (?? bi?t khi nào hoàn thành round này)
            currentRoundWords.clear()
            currentRoundWords.addAll(vocabForRound.map { it.lowercase().trim() })

            // Ch?n 1 pair cho m?i vocab (n?u có nhi?u pair cho cùng 1 t? thì l?y c?p ??u tiên)
            val selected = mutableListOf<MatchPair>()
            vocabForRound.forEach { vocab ->
                val p = allPairs.firstOrNull { it.word?.equals(vocab, ignoreCase = true) == true }
                if (p != null) selected.add(p)
            }

            if (selected.isEmpty()) {
                android.util.Log.w(
                    "AISummaryActivity",
                    "renderRound: no selected pairs for this round",
                )
                matchPairsQuizCompleted = true
                saveQuizResults()
                showMatchPairsResult()
                return
            }

            matchPairsState = selected.toMutableList()
            matchPairsRevealed.clear()
            matchFirstSelection = null

            // Data class ?? l?u tile info
            data class TileInfo(
                val key: Int,
                val text: String,
                val type: String,
                val wordKey: String,
            )

            val tiles = mutableListOf<TileInfo>()
            selected.forEachIndexed { idx, pair ->
                val wordRaw = pair.word?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                val key = idx + 1 // unique per pair
                val meaning = getVietnameseTranslation(wordRaw) ?: pair.meaning ?: pair.hint
                val shortenedMeaning = shortenMeaning(meaning)
                val finalMeaning = if (shortenedMeaning.isNotBlank()) shortenedMeaning else wordRaw
                if (wordRaw.isNotBlank() && finalMeaning.isNotBlank()) {
                    val wordKey = wordRaw.lowercase().trim()
                    tiles.add(TileInfo(key, wordRaw, "word", wordKey))
                    tiles.add(TileInfo(key, finalMeaning, "meaning", wordKey))
                }
            }

            if (tiles.isEmpty()) {
                android.util.Log.w(
                    "AISummaryActivity",
                    "updateMatchPairsCard: No valid tiles created from pairs",
                )
                binding.MatchPairsCard.isVisible = false
                return
            }

            tiles.shuffle()

            // Tính s? c?t và hàng d?a trên s? l??ng tiles (4x4 ho?c ít h?n nh?ng v?n 4 c?t)
            val totalTiles = tiles.size
            val columnCount = 4
            val rowCount = (totalTiles + columnCount - 1) / columnCount // Làm tròn lên

            val grid =
                GridLayout(this).apply {
                    this.columnCount = columnCount
                    this.rowCount = rowCount
                    setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                }

            // Map ?? l?u word t??ng ?ng v?i key
            val keyToWordMap = mutableMapOf<Int, String>()
            tiles.forEach { tile -> keyToWordMap[tile.key] = tile.wordKey }

            tiles.forEachIndexed { idx, tile ->
                val btn =
                    MaterialButton(this).apply {
                        this.text = tile.text
                        isAllCaps = false
                        tag = tile // L?u toàn b? TileInfo vào tag
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        textSize = 11f
                        setTextColor(Color.BLACK)
                        gravity = android.view.Gravity.CENTER
                        setTypeface(null, Typeface.NORMAL)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E6E8F0"))
                        minHeight = 0
                        minWidth = 0
                        setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                        layoutParams =
                            GridLayout.LayoutParams().apply {
                                width = 0
                                height = GridLayout.LayoutParams.WRAP_CONTENT
                                columnSpec = GridLayout.spec(idx % columnCount, 1f)
                                rowSpec = GridLayout.spec(idx / columnCount)
                                setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                            }
                        minimumHeight = 48.dpToPx()
                        setOnClickListener {
                            val tileInfo = tag as? TileInfo ?: return@setOnClickListener
                            val key = tileInfo.key

                            if (matchPairsRevealed.contains(key)) return@setOnClickListener
                            val first = matchFirstSelection
                            if (first == null) {
                                matchFirstSelection = key
                                setBackgroundColor(
                                    ContextCompat.getColor(
                                        this@AISummaryActivity,
                                        android.R.color.holo_blue_light,
                                    )
                                )
                            } else {
                                val secondKey = key

                                if (first == secondKey) {
                                    // Match ?úng
                                    matchPairsRevealed.add(key)

                                    // L?y word t? tileInfo
                                    val vocabKey = tileInfo.wordKey.lowercase().trim()
                                    val prog = matchPairsVocabProgress[vocabKey]

                                    // N?u t? này ch?a ???c match ?úng tr??c ?ó, ?ánh d?u completed
                                    // + t?ng ?i?m
                                    if (!matchPairsWordsMatched.contains(vocabKey)) {
                                        matchPairsWordsMatched.add(vocabKey)
                                        matchPairsScore =
                                            matchPairsWordsMatched
                                                .size // ?i?m tính theo s? vocab ?ã hoàn thành

                                        if (prog != null) {
                                            prog.status = "completed"
                                            prog.completedAt = System.currentTimeMillis()
                                            // C?ng thêm 1 attempt cho l?n match ?úng này
                                            prog.attempts = (prog.attempts + 1).coerceAtMost(999)
                                            matchPairsVocabProgress[vocabKey] = prog
                                        }
                                    }

                                    for (i in 0 until grid.childCount) {
                                        val child =
                                            grid.getChildAt(i) as? MaterialButton ?: continue
                                        val childTile = child.tag as? TileInfo
                                        if (childTile?.key == key) {
                                            child.isEnabled = false
                                            child.setBackgroundColor(
                                                ContextCompat.getColor(
                                                    this@AISummaryActivity,
                                                    android.R.color.holo_green_light,
                                                )
                                            )
                                        }
                                    }

                                    // N?u ?ã hoàn thành T?T C? vocab trong note ? k?t thúc quiz
                                    if (matchPairsWordsMatched.size >= matchPairsUniqueWords.size) {
                                        matchPairsQuizCompleted = true
                                        saveQuizResults()
                                        showMatchPairsResult()
                                    } else {
                                        // N?u ch? hoàn thành xong ROUND hi?n t?i (t?t c? t? trong
                                        // currentRoundWords),
                                        // nh?ng v?n còn t? ch?a hoàn thành trong note ? render
                                        // round m?i.
                                        val roundCompleted =
                                            currentRoundWords.all { wordKey ->
                                                matchPairsWordsMatched.contains(wordKey)
                                            }
                                        if (roundCompleted) {
                                            grid.postDelayed({ renderRound() }, 300)
                                        }
                                    }
                                } else {
                                    // Match sai -> t?ng attempts cho vocab ?ó
                                    val vocabKey = tileInfo.wordKey.lowercase().trim()
                                    val prog = matchPairsVocabProgress[vocabKey]
                                    if (prog != null) {
                                        prog.attempts = (prog.attempts + 1).coerceAtMost(999)
                                        matchPairsVocabProgress[vocabKey] = prog
                                    }

                                    setBackgroundColor(
                                        ContextCompat.getColor(
                                            this@AISummaryActivity,
                                            android.R.color.holo_red_light,
                                        )
                                    )
                                    for (i in 0 until grid.childCount) {
                                        val child =
                                            grid.getChildAt(i) as? MaterialButton ?: continue
                                        val childTile = child.tag as? TileInfo
                                        if (childTile?.key == first) {
                                            child.setBackgroundColor(
                                                ContextCompat.getColor(
                                                    this@AISummaryActivity,
                                                    android.R.color.holo_red_light,
                                                )
                                            )
                                        }
                                    }
                                    grid.postDelayed(
                                        {
                                            for (i in 0 until grid.childCount) {
                                                val child =
                                                    grid.getChildAt(i) as? MaterialButton
                                                        ?: continue
                                                val childTile = child.tag as? TileInfo
                                                val childKey = childTile?.key
                                                if (childKey == first || childKey == secondKey) {
                                                    if (
                                                        !matchPairsRevealed.contains(childKey ?: -1)
                                                    ) {
                                                        child.backgroundTintList =
                                                            ColorStateList.valueOf(
                                                                Color.parseColor("#E6E8F0")
                                                            )
                                                    }
                                                }
                                            }
                                        },
                                        800,
                                    )
                                }
                                matchFirstSelection = null
                            }
                        }
                    }
                grid.addView(btn)
            }

            binding.MatchPairsContainer.removeAllViews()
            binding.MatchPairsContainer.addView(grid)

            // B? nút reset vì ?ã hi?n th? t?t c? các c?p, không c?n reset n?a
            // (User ph?i match h?t t?t c? các t? m?i hoàn thành)
        }

        // Render l?n ??u
        renderRound()
    }

    /**
     * ?n toàn b? card n?i dung, ch? gi? Overall Progress và b?ng th?ng kê per vocab. Dùng cho ch?
     * ?? statsOnly.
     */
    private fun hideAllCardsExceptStats() {
        // Raw text / summary cards
        binding.RawTextCard.isVisible = false
        binding.OneSentenceCard.isVisible = false
        binding.ParagraphCard.isVisible = false
        binding.BulletPointsCard.isVisible = false
        binding.QuestionsCard.isVisible = false
        binding.MCQCard.isVisible = false

        // Vocab cards
        binding.VocabStoryCard.isVisible = false
        binding.VocabMCQCard.isVisible = false
        binding.FlashcardsCard.isVisible = false
        binding.SummaryTableCard.isVisible = false
        binding.ClozeCard.isVisible = false
        binding.MatchPairsCard.isVisible = false
    }

    /**
     * Render b?ng th?ng kê per-vocab và % t?ng, ch? dùng cho statsOnly. T?n d?ng hàm
     * showOverallStatistics ?? tính toán l?i.
     */
    private fun renderStatsOnly() {
        // ??m b?o Overall Progress card hi?n
        binding.OverallProgressCard.isVisible = true
        updateOverallProgressCardSummary()

        // T?o container m?i cho b?ng th?ng kê
        val statsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 32.dpToPx())
            }

        statsContainer.addView(
            TextView(this).apply {
                text = "Vocabulary Statistics"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 12.dpToPx())
            }
        )

        showOverallStatistics(statsContainer)

        // Thêm vào cu?i n?i dung ScrollView
        val root = (binding.ContentScrollView.getChildAt(0) as? LinearLayout)
        root?.addView(statsContainer)
    }

    private fun showMatchPairsResult() {
        val percentage = if (matchPairsTotal > 0) (matchPairsScore * 100 / matchPairsTotal) else 0

        binding.MatchPairsContainer.removeAllViews()

        val resultView =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

        // Title
        resultView.addView(
            TextView(this).apply {
                text = "Match Pairs Complete!"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, android.R.color.black))
                setPadding(0, 0, 0, 16.dpToPx())
            }
        )

        // Score
        resultView.addView(
            TextView(this).apply {
                text = "Score: $matchPairsScore / $matchPairsTotal ($percentage%)"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                val color =
                    if (percentage >= 80) android.R.color.holo_green_dark
                    else if (percentage >= 60) android.R.color.holo_orange_dark
                    else android.R.color.holo_red_dark
                setTextColor(ContextCompat.getColor(this@AISummaryActivity, color))
                setPadding(0, 0, 0, 8.dpToPx())
            }
        )

        // Show overall statistics if all quizzes completed
        if (vocabQuizCompleted && clozeQuizCompleted && matchPairsQuizCompleted) {
            resultView.addView(
                TextView(this).apply {
                    text = "\n--- Overall Statistics ---"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
                }
            )
            showOverallStatistics(resultView)
        }

        binding.MatchPairsContainer.addView(resultView)
    }

    private fun updateSummaryTableCard(rows: List<VocabSummaryRow>?) {
        val items = rows.orEmpty()
        // L?u summaryTable vào state ?? dùng cho match pairs
        summaryTableState = items.ifEmpty { null }
        binding.SummaryTableCard.isVisible = items.isNotEmpty()
        binding.SummaryTableContainer.removeAllViews()
        if (items.isEmpty()) return

        // Display each vocab word as a separate card/item for better readability
        items.forEachIndexed { index, row ->
            val wordCard =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    if (index < items.size - 1) {
                        setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    }
                }

            // Word title (bold) + speaker
            val word = row.word ?: ""
            if (word.isNotBlank()) {
                val header =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, 8.dpToPx())
                    }
                val wordView =
                    TextView(this).apply {
                        text = "• $word"
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(
                            ContextCompat.getColor(this@AISummaryActivity, android.R.color.black)
                        )
                        setTextIsSelectable(true)
                        layoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                header.addView(wordView)

                val speakBtn =
                    ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                        background = null
                        contentDescription = "Speak $word"
                        setOnClickListener {
                            // Luôn dùng t? g?c (word) ?? TTS ??c, không dùng phonetic vì TTS không
                            // hi?u IPA notation
                            // TTS s? t? ??ng phát âm ?úng t? ti?ng Anh n?u setLanguage(Locale.US)
                            val toSpeak = word.trim()
                            if (ttsReady && toSpeak.isNotBlank()) {
                                // Stop any ongoing speech tr??c khi ??c t? m?i
                                tts?.stop()
                                tts?.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "tts-$word")
                            } else {
                                Toast.makeText(
                                        this@AISummaryActivity,
                                        getString(R.string.ai_tts_not_ready),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        }
                    }
                header.addView(speakBtn)
                wordCard.addView(header)
            }

            fun addField(label: String, value: String?) {
                if (value.isNullOrBlank()) return
                val text = "$label: $value"
                val spannable = SpannableString(text)
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    label.length + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                wordCard.addView(
                    TextView(this).apply {
                        setText(spannable)
                        textSize = 14f
                        setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                        setTextIsSelectable(true)
                    }
                )
            }

            // Translation
            row.translation?.let { addField("D?ch", it) }

            // Part of Speech
            row.partOfSpeech?.let { addField("Lo?i t?", it) }

            // Definition
            row.definition?.let { addField("??nh ngh?a", it) }

            // Usage Note
            row.usageNote?.let { addField("Cách dùng", it) }

            // Common Structures
            row.commonStructures
                ?.takeIf { it.isNotEmpty() }
                ?.let { addField("C?u trúc", it.joinToString(", ")) }

            // Collocations
            row.collocations
                ?.takeIf { it.isNotEmpty() }
                ?.let { addField("C?m t?", it.joinToString(", ")) }

            // Add divider between words (except last)
            if (index < items.size - 1) {
                wordCard.addView(
                    View(this).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                .apply {
                                    topMargin = 8.dpToPx()
                                    bottomMargin = 8.dpToPx()
                                }
                        setBackgroundColor(
                            ContextCompat.getColor(
                                this@AISummaryActivity,
                                android.R.color.darker_gray,
                            )
                        )
                    }
                )
            }

            binding.SummaryTableContainer.addView(wordCard)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Summary", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.ai_copied, Toast.LENGTH_SHORT).show()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    /** Convert markdown bold (**text**) to SpannableString with bold formatting */
    private fun markdownBoldToSpannable(text: String?): SpannableString {
        if (text.isNullOrBlank()) {
            return SpannableString("")
        }

        val pattern = Regex("\\*\\*(.+?)\\*\\*")
        val result = StringBuilder()
        val spans = mutableListOf<Pair<Int, Int>>()
        var lastIndex = 0

        pattern.findAll(text).forEach { matchResult ->
            // Add text before the match
            result.append(text.substring(lastIndex, matchResult.range.first))

            // Record the position for bold span
            val boldStart = result.length
            val boldText = matchResult.groupValues[1]
            result.append(boldText)
            val boldEnd = result.length

            spans.add(Pair(boldStart, boldEnd))

            lastIndex = matchResult.range.last + 1
        }

        // Add remaining text
        if (lastIndex < text.length) {
            result.append(text.substring(lastIndex))
        }

        val spannable = SpannableString(result.toString())
        spans.forEach { (start, end) ->
            if (start >= 0 && end <= spannable.length && start < end) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        return spannable
    }

    /** Create SpannableString with bold label and normal content */
    private fun createLabeledText(label: String, content: String?): SpannableString? {
        if (content.isNullOrBlank()) return null
        val text = "$label: $content"
        val spannable = SpannableString(text)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            label.length + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return spannable
    }

    // ==================== TRANSLATION FUNCTIONS ====================

    // Supported translation languages (Vietnamese is first as default)
    private val supportedLanguages =
        listOf(
            "Vietnamese" to "Ti?ng Vi?t",
            "English" to "English",
            "Chinese" to "??",
            "Japanese" to "???",
            "Korean" to "???",
            "French" to "Fran?ais",
            "German" to "Deutsch",
            "Spanish" to "Espa?ol",
            "Italian" to "Italiano",
            "Portuguese" to "Portugu?s",
            "Russian" to "???????",
            "Arabic" to "???????",
            "Thai" to "???",
            "Indonesian" to "Bahasa Indonesia",
            "Hindi" to "??????",
        )

    private fun getCurrentTranslateLanguage(): String {
        return AIUserPreferences.getTranslateLanguage(this)
    }

    private fun showLanguageSelectionDialog(onLanguageSelected: (String) -> Unit = {}) {
        val currentLanguage = getCurrentTranslateLanguage()
        val languages = supportedLanguages.map { it.first }.toTypedArray()
        val currentIndex = languages.indexOf(currentLanguage).takeIf { it >= 0 } ?: 0

        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Ch?n ng?n ng? d?ch / Select Translation Language")
                .setSingleChoiceItems(
                    supportedLanguages.map { "${it.second} (${it.first})" }.toTypedArray(),
                    currentIndex,
                ) { dialog, which ->
                    val selectedLanguage = languages[which]
                    AIUserPreferences.setTranslateLanguage(this, selectedLanguage)
                    onLanguageSelected(selectedLanguage)
                    dialog.dismiss()
                    Toast.makeText(
                            this,
                            "?? ch?n: ${supportedLanguages[which].second}",
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                .setNegativeButton("H?y / Cancel", null)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "L?i hi?n th? dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTranslateButtons() {
        // Update button text to show current language
        val currentLang = getCurrentTranslateLanguage()
        val langDisplay = supportedLanguages.find { it.first == currentLang }?.second ?: currentLang

        // Long press to change language, single click to translate
        val translateButtons =
            listOf(
                binding.VocabStoryTranslateButton,
                binding.VocabMCQTranslateButton,
                binding.FlashcardsTranslateButton,
                binding.MindmapTranslateButton,
                binding.SummaryTableTranslateButton,
            )

        translateButtons.forEach { button ->
            // Show current language in button hint/tooltip
            button.setOnLongClickListener {
                showLanguageSelectionDialog { selectedLanguage ->
                    // Reset translation states when language changes
                    vocabStoryIsTranslated = false
                    vocabMCQIsTranslated = false
                    flashcardsIsTranslated = false
                    mindmapIsTranslated = false
                    summaryTableIsTranslated = false

                    // Update button text to show new language
                    val newLangDisplay =
                        supportedLanguages.find { it.first == selectedLanguage }?.second
                            ?: selectedLanguage
                    Toast.makeText(this, "Ng?n ng? d?ch: $newLangDisplay", Toast.LENGTH_SHORT)
                        .show()
                }
                true
            }
        }

        binding.VocabStoryTranslateButton.setOnClickListener { translateVocabStory() }
        binding.VocabMCQTranslateButton.setOnClickListener { translateVocabMCQ() }
        binding.FlashcardsTranslateButton.setOnClickListener { translateFlashcards() }
        binding.MindmapTranslateButton.setOnClickListener { translateMindmap() }
        binding.SummaryTableTranslateButton.setOnClickListener { translateSummaryTable() }
    }

    private fun translateVocabStory() {
        // ?u tiên l?y t? bi?n instance (?ã hi?n th?), n?u không có thì l?y t? summaryResponse
        val story =
            vocabStoryOriginal
                ?: summaryResponse?.vocabStory
                ?: summaryResponse?.review?.vocabStory
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateVocabStory: story is null")
                    Toast.makeText(this, "Story data is not available", Toast.LENGTH_SHORT).show()
                    return
                }

        if (vocabStoryIsTranslated && vocabStoryTranslated != null) {
            // Toggle back to original - l?y t? bi?n instance ho?c summaryResponse
            vocabStoryIsTranslated = false
            val originalStory = vocabStoryOriginal ?: story
            updateVocabStoryCard(originalStory)
            binding.VocabStoryTranslateButton.text = getString(R.string.translate)
            return
        }

        val textToTranslate = buildString {
            story.title?.let { appendLine("Title: $it") }
            story.paragraphs?.forEach { appendLine(it) }
        }
        if (textToTranslate.isBlank()) {
            android.util.Log.d("AISummaryActivity", "translateVocabStory: textToTranslate is blank")
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetLang = getCurrentTranslateLanguage()
        android.util.Log.d(
            "AISummaryActivity",
            "translateVocabStory: targetLanguage=$targetLang, textLength=${textToTranslate.length}",
        )

        // Translate
        translateText(
            text = textToTranslate,
            targetLanguage = targetLang,
            onSuccess = { translated ->
                android.util.Log.d(
                    "AISummaryActivity",
                    "translateVocabStory: success, translatedLength=${translated.length}",
                )
                vocabStoryTranslated = translated
                vocabStoryIsTranslated = true
                // Update UI with translated text
                binding.VocabStoryTitle.text = story.title ?: getString(R.string.ai_vocab_story)
                binding.VocabStoryParagraphs.removeAllViews()
                // Split translated text back into paragraphs (simple approach)
                translated
                    .split("\n")
                    .filter { it.isNotBlank() && !it.startsWith("Title:") }
                    .forEach { paragraph ->
                        binding.VocabStoryParagraphs.addView(
                            TextView(this).apply {
                                text = markdownBoldToSpannable(paragraph)
                                textSize = 15f
                                setTextIsSelectable(true)
                                setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
                            }
                        )
                    }
                binding.VocabStoryTranslateButton.text = getString(R.string.show_original)
            },
            onError = { error ->
                android.util.Log.e("AISummaryActivity", "translateVocabStory: error - $error")
            },
        )
    }

    private fun translateVocabMCQ() {
        // ?u tiên l?y t? bi?n instance (?ã hi?n th?), n?u không có thì l?y t? summaryResponse
        val quizzes =
            vocabMCQsOriginal
                ?: summaryResponse?.vocabMcqs
                ?: summaryResponse?.review?.vocabMcqs
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateVocabMCQ: quizzes is null")
                    Toast.makeText(this, "MCQ data is not available", Toast.LENGTH_SHORT).show()
                    return
                }

        if (quizzes.isEmpty()) {
            android.util.Log.d("AISummaryActivity", "translateVocabMCQ: quizzes is empty")
            Toast.makeText(this, "MCQ data is not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (vocabMCQIsTranslated && vocabMCQTranslated != null) {
            // Toggle back to original - l?y t? bi?n instance ho?c summaryResponse
            vocabMCQIsTranslated = false
            val originalQuizzes = vocabMCQsOriginal ?: quizzes
            updateVocabMCQCard(originalQuizzes)
            binding.VocabMCQTranslateButton.text = getString(R.string.translate)
            return
        }

        val textToTranslate = buildString {
            quizzes.forEachIndexed { index, quiz ->
                if (index > 0) appendLine()
                appendLine("Question ${index + 1}:")
                quiz.type?.let { appendLine("Type: $it") }
                // Không hi?n th? Target ?? không l? ?áp án
                quiz.question?.let { appendLine("Q: $it") }
                quiz.options?.forEach { (key, value) -> appendLine("$key: $value") }
                quiz.explanation?.let { appendLine("Explanation: $it") }
                quiz.whenWrong?.let { appendLine("When Wrong: $it") }
            }
        }
        if (textToTranslate.isBlank()) {
            android.util.Log.d("AISummaryActivity", "translateVocabMCQ: textToTranslate is blank")
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetLang = getCurrentTranslateLanguage()
        android.util.Log.d(
            "AISummaryActivity",
            "translateVocabMCQ: targetLanguage=$targetLang, textLength=${textToTranslate.length}",
        )

        // D?ch t?ng quiz item ?? gi? format g?c
        translateVocabMCQsWithFormat(quizzes, targetLang)
    }

    private fun translateSummaryTableWithFormat(rows: List<VocabSummaryRow>, targetLang: String) {
        binding.SummaryTableContainer.removeAllViews()
        binding.SummaryTableContainer.addView(
            TextView(this).apply {
                text = "?ang d?ch..."
                textSize = 14f
                setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                gravity = android.view.Gravity.CENTER
            }
        )

        // D?ch t?ng row
        val translatedRows = mutableListOf<VocabSummaryRow>()
        var completedCount = 0

        rows.forEachIndexed { index, row ->
            val textToTranslate = buildString {
                row.word?.let { appendLine("Word: $it") }
                row.translation?.let { appendLine("Translation: $it") }
                row.partOfSpeech?.let { appendLine("Part of Speech: $it") }
                row.definition?.let { appendLine("Definition: $it") }
                row.usageNote?.let { appendLine("Usage Note: $it") }
                row.commonStructures?.joinToString(", ")?.let { appendLine("Structures: $it") }
                row.collocations?.joinToString(", ")?.let { appendLine("Collocations: $it") }
            }

            translateText(
                text = textToTranslate,
                targetLanguage = targetLang,
                onSuccess = { translated ->
                    // Parse k?t qu? d?ch
                    val lines = translated.split("\n")
                    var translatedWord = row.word
                    var translatedTranslation = row.translation
                    var translatedPartOfSpeech = row.partOfSpeech
                    var translatedDefinition = row.definition
                    var translatedUsageNote = row.usageNote
                    var translatedStructures = row.commonStructures
                    var translatedCollocations = row.collocations

                    lines.forEach { line ->
                        when {
                            line.startsWith("Word:", ignoreCase = true) -> {
                                translatedWord = line.removePrefix("Word:").trim()
                            }
                            line.startsWith("Translation:", ignoreCase = true) -> {
                                translatedTranslation = line.removePrefix("Translation:").trim()
                            }
                            line.startsWith("Part of Speech:", ignoreCase = true) -> {
                                translatedPartOfSpeech = line.removePrefix("Part of Speech:").trim()
                            }
                            line.startsWith("Definition:", ignoreCase = true) -> {
                                translatedDefinition = line.removePrefix("Definition:").trim()
                            }
                            line.startsWith("Usage Note:", ignoreCase = true) -> {
                                translatedUsageNote = line.removePrefix("Usage Note:").trim()
                            }
                            line.startsWith("Structures:", ignoreCase = true) -> {
                                translatedStructures =
                                    line.removePrefix("Structures:").trim().split(", ")
                            }
                            line.startsWith("Collocations:", ignoreCase = true) -> {
                                translatedCollocations =
                                    line.removePrefix("Collocations:").trim().split(", ")
                            }
                        }
                    }

                    // T?o l?i row v?i các field ?ã d?ch
                    val translatedRow =
                        row.copy(
                            word = translatedWord,
                            translation = translatedTranslation,
                            partOfSpeech = translatedPartOfSpeech,
                            definition = translatedDefinition,
                            usageNote = translatedUsageNote,
                            commonStructures = translatedStructures,
                            collocations = translatedCollocations,
                        )
                    translatedRows.add(translatedRow)
                    completedCount++

                    // Khi d?ch xong t?t c?, hi?n th? l?i v?i format g?c
                    if (completedCount == rows.size) {
                        summaryTableIsTranslated = true
                        updateSummaryTableCard(translatedRows)
                        binding.SummaryTableTranslateButton.text = getString(R.string.show_original)
                    }
                },
                onError = { error ->
                    android.util.Log.e(
                        "AISummaryActivity",
                        "translateSummaryTable row $index: error - $error",
                    )
                    // N?u l?i, dùng row g?c
                    translatedRows.add(row)
                    completedCount++
                    if (completedCount == rows.size) {
                        summaryTableIsTranslated = true
                        updateSummaryTableCard(translatedRows)
                        binding.SummaryTableTranslateButton.text = getString(R.string.show_original)
                    }
                },
            )
        }
    }

    private fun translateVocabMCQsWithFormat(quizzes: List<VocabQuiz>, targetLang: String) {
        binding.VocabMCQContainer.removeAllViews()
        binding.VocabMCQContainer.addView(
            TextView(this).apply {
                text = "?ang d?ch..."
                textSize = 14f
                setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                gravity = android.view.Gravity.CENTER
            }
        )

        // D?ch t?ng quiz item
        val translatedQuizzes = mutableListOf<VocabQuiz>()
        var completedCount = 0

        quizzes.forEachIndexed { index, quiz ->
            // D?ch question
            val questionText = quiz.question ?: ""
            val optionsText =
                quiz.options?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: ""
            val explanationText = quiz.explanation ?: ""
            val textToTranslate = buildString {
                if (questionText.isNotBlank()) appendLine("Question: $questionText")
                if (optionsText.isNotBlank()) appendLine("Options:\n$optionsText")
                if (explanationText.isNotBlank()) appendLine("Explanation: $explanationText")
            }

            translateText(
                text = textToTranslate,
                targetLanguage = targetLang,
                onSuccess = { translated ->
                    // Parse k?t qu? d?ch
                    val lines = translated.split("\n")
                    var translatedQuestion = questionText
                    val translatedOptions = mutableMapOf<String, String>()
                    var translatedExplanation = explanationText

                    var currentSection = ""
                    lines.forEach { line ->
                        when {
                            line.startsWith("Question:", ignoreCase = true) -> {
                                currentSection = "question"
                                translatedQuestion = line.removePrefix("Question:").trim()
                            }
                            line.startsWith("Options:", ignoreCase = true) -> {
                                currentSection = "options"
                            }
                            line.startsWith("Explanation:", ignoreCase = true) -> {
                                currentSection = "explanation"
                                translatedExplanation = line.removePrefix("Explanation:").trim()
                            }
                            currentSection == "options" && line.contains(":") -> {
                                val parts = line.split(":", limit = 2)
                                if (parts.size == 2) {
                                    val key = parts[0].trim()
                                    val value = parts[1].trim()
                                    translatedOptions[key] = value
                                }
                            }
                        }
                    }

                    // T?o l?i quiz v?i các field ?ã d?ch
                    val translatedQuiz =
                        quiz.copy(
                            question =
                                translatedQuestion.takeIf { it.isNotBlank() } ?: quiz.question,
                            options =
                                if (translatedOptions.isNotEmpty()) translatedOptions
                                else quiz.options,
                            explanation =
                                translatedExplanation.takeIf { it.isNotBlank() } ?: quiz.explanation,
                        )
                    translatedQuizzes.add(translatedQuiz)
                    completedCount++

                    // Khi d?ch xong t?t c?, hi?n th? l?i v?i format g?c
                    if (completedCount == quizzes.size) {
                        vocabMCQIsTranslated = true
                        binding.VocabMCQContainer.removeAllViews()
                        translatedQuizzes.forEach { tq ->
                            binding.VocabMCQContainer.addView(createVocabMCQView(tq))
                        }
                        binding.VocabMCQTranslateButton.text = getString(R.string.show_original)
                    }
                },
                onError = { error ->
                    android.util.Log.e(
                        "AISummaryActivity",
                        "translateVocabMCQ item $index: error - $error",
                    )
                    // N?u l?i, dùng quiz g?c
                    translatedQuizzes.add(quiz)
                    completedCount++
                    if (completedCount == quizzes.size) {
                        vocabMCQIsTranslated = true
                        binding.VocabMCQContainer.removeAllViews()
                        translatedQuizzes.forEach { tq ->
                            binding.VocabMCQContainer.addView(createVocabMCQView(tq))
                        }
                        binding.VocabMCQTranslateButton.text = getString(R.string.show_original)
                    }
                },
            )
        }
    }

    private fun translateFlashcards() {
        val cards =
            summaryResponse?.flashcards
                ?: summaryResponse?.review?.flashcards
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateFlashcards: cards is null")
                    Toast.makeText(this, "Flashcards data is not available", Toast.LENGTH_SHORT)
                        .show()
                    return
                }

        if (cards.isEmpty()) {
            android.util.Log.d("AISummaryActivity", "translateFlashcards: cards is empty")
            Toast.makeText(this, "Flashcards data is not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (flashcardsIsTranslated && flashcardsTranslated != null) {
            // Toggle back to original - l?y t? bi?n instance ho?c summaryResponse
            flashcardsIsTranslated = false
            val originalCards = flashcardsOriginal ?: cards
            updateFlashcardsCard(originalCards)
            binding.FlashcardsTranslateButton.text = getString(R.string.translate)
            return
        }

        // Build text to translate - format similar to how we display
        val textToTranslate = buildString {
            // Meaning section
            val meaningBuilder = StringBuilder()
            cards.forEachIndexed { index, card ->
                val word = card.word ?: card.front ?: ""
                if (word.isNotBlank()) {
                    if (index > 0) {
                        meaningBuilder.append("\n")
                    }
                    meaningBuilder.append("? ").append(word).append(": ")
                    if (!card.back?.meaning.isNullOrBlank()) {
                        meaningBuilder.append(card.back?.meaning)
                    }
                }
            }
            if (meaningBuilder.isNotEmpty()) {
                appendLine("${getString(R.string.meaning)}:")
                appendLine(meaningBuilder.toString())
            }

            // Example section
            val examples = cards.mapNotNull { it.back?.example }.filter { it.isNotBlank() }
            if (examples.isNotEmpty()) {
                appendLine("${getString(R.string.example)}:")
                appendLine(examples.joinToString("\n"))
            }

            // Note section
            val notes = cards.mapNotNull { it.back?.usageNote }.filter { it.isNotBlank() }
            if (notes.isNotEmpty()) {
                appendLine("${getString(R.string.note)}:")
                appendLine(notes.joinToString("\n"))
            }

            // Synonyms
            val synonyms = cards.flatMap { it.back?.synonyms.orEmpty() }.distinct()
            if (synonyms.isNotEmpty()) {
                appendLine("Synonyms: ${synonyms.joinToString(", ")}")
            }

            // Antonyms
            val antonyms = cards.flatMap { it.back?.antonyms.orEmpty() }.distinct()
            if (antonyms.isNotEmpty()) {
                appendLine("Antonyms: ${antonyms.joinToString(", ")}")
            }

            // Quick Tips
            val quickTips = cards.mapNotNull { it.back?.quickTip }.filter { it.isNotBlank() }
            if (quickTips.isNotEmpty()) {
                appendLine("Quick Tips: ${quickTips.joinToString("\n")}")
            }

            // SRS Schedule
            val srsSchedules = cards.mapNotNull { it.srsSchedule }
            if (srsSchedules.isNotEmpty()) {
                appendLine("SRS Schedules:")
                srsSchedules.forEach { schedule ->
                    schedule.initialPrompt?.let { appendLine("  Prompt: $it") }
                    schedule.intervals?.let {
                        appendLine("  Intervals: ${it.joinToString(", ")} days")
                    }
                    schedule.recallTask?.let { appendLine("  Task: $it") }
                }
            }
        }

        if (textToTranslate.isBlank()) {
            android.util.Log.d("AISummaryActivity", "translateFlashcards: textToTranslate is blank")
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetLang = getCurrentTranslateLanguage()
        android.util.Log.d(
            "AISummaryActivity",
            "translateFlashcards: targetLanguage=$targetLang, textLength=${textToTranslate.length}",
        )

        // D?ch t?ng flashcard ?? gi? format g?c
        translateFlashcardsWithFormat(cards, targetLang)
    }

    private fun translateFlashcardsWithFormat(cards: List<Flashcard>, targetLang: String) {
        binding.FlashcardsContainer.removeAllViews()
        binding.FlashcardsContainer.addView(
            TextView(this).apply {
                text = "?ang d?ch..."
                textSize = 14f
                setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                gravity = android.view.Gravity.CENTER
            }
        )

        // D?ch t?ng flashcard
        val translatedCards = mutableListOf<Flashcard>()
        var completedCount = 0

        cards.forEachIndexed { index, card ->
            val word = card.word ?: card.front ?: ""
            val meaning = card.back?.meaning ?: ""
            val example = card.back?.example ?: ""
            val usageNote = card.back?.usageNote ?: ""
            val synonyms = card.back?.synonyms?.joinToString(", ") ?: ""
            val antonyms = card.back?.antonyms?.joinToString(", ") ?: ""
            val quickTip = card.back?.quickTip ?: ""

            val textToTranslate = buildString {
                if (word.isNotBlank()) appendLine("Word: $word")
                if (meaning.isNotBlank()) appendLine("Meaning: $meaning")
                if (example.isNotBlank()) appendLine("Example: $example")
                if (usageNote.isNotBlank()) appendLine("Usage Note: $usageNote")
                if (synonyms.isNotBlank()) appendLine("Synonyms: $synonyms")
                if (antonyms.isNotBlank()) appendLine("Antonyms: $antonyms")
                if (quickTip.isNotBlank()) appendLine("Quick Tip: $quickTip")
            }

            translateText(
                text = textToTranslate,
                targetLanguage = targetLang,
                onSuccess = { translated ->
                    // Parse k?t qu? d?ch
                    val lines = translated.split("\n")
                    var translatedWord = word
                    var translatedMeaning = meaning
                    var translatedExample = example
                    var translatedUsageNote = usageNote
                    var translatedSynonyms = synonyms
                    var translatedAntonyms = antonyms
                    var translatedQuickTip = quickTip

                    lines.forEach { line ->
                        when {
                            line.startsWith("Word:", ignoreCase = true) -> {
                                translatedWord = line.removePrefix("Word:").trim()
                            }
                            line.startsWith("Meaning:", ignoreCase = true) -> {
                                translatedMeaning = line.removePrefix("Meaning:").trim()
                            }
                            line.startsWith("Example:", ignoreCase = true) -> {
                                translatedExample = line.removePrefix("Example:").trim()
                            }
                            line.startsWith("Usage Note:", ignoreCase = true) -> {
                                translatedUsageNote = line.removePrefix("Usage Note:").trim()
                            }
                            line.startsWith("Synonyms:", ignoreCase = true) -> {
                                translatedSynonyms = line.removePrefix("Synonyms:").trim()
                            }
                            line.startsWith("Antonyms:", ignoreCase = true) -> {
                                translatedAntonyms = line.removePrefix("Antonyms:").trim()
                            }
                            line.startsWith("Quick Tip:", ignoreCase = true) -> {
                                translatedQuickTip = line.removePrefix("Quick Tip:").trim()
                            }
                        }
                    }

                    // T?o l?i flashcard v?i các field ?ã d?ch
                    val translatedBack =
                        card.back?.copy(
                            meaning =
                                translatedMeaning.takeIf { it.isNotBlank() } ?: card.back?.meaning,
                            example =
                                translatedExample.takeIf { it.isNotBlank() } ?: card.back?.example,
                            usageNote =
                                translatedUsageNote.takeIf { it.isNotBlank() }
                                    ?: card.back?.usageNote,
                            synonyms =
                                translatedSynonyms.takeIf { it.isNotBlank() }?.split(", ")
                                    ?: card.back?.synonyms,
                            antonyms =
                                translatedAntonyms.takeIf { it.isNotBlank() }?.split(", ")
                                    ?: card.back?.antonyms,
                            quickTip =
                                translatedQuickTip.takeIf { it.isNotBlank() } ?: card.back?.quickTip,
                        )
                    val translatedCard =
                        card.copy(
                            word = translatedWord.takeIf { it.isNotBlank() } ?: card.word,
                            front = translatedWord.takeIf { it.isNotBlank() } ?: card.front,
                            back = translatedBack,
                        )
                    translatedCards.add(translatedCard)
                    completedCount++

                    // Khi d?ch xong t?t c?, hi?n th? l?i v?i format g?c
                    if (completedCount == cards.size) {
                        flashcardsIsTranslated = true
                        updateFlashcardsCard(translatedCards)
                        binding.FlashcardsTranslateButton.text = getString(R.string.show_original)
                    }
                },
                onError = { error ->
                    android.util.Log.e(
                        "AISummaryActivity",
                        "translateFlashcards card $index: error - $error",
                    )
                    // N?u l?i, dùng card g?c
                    translatedCards.add(card)
                    completedCount++
                    if (completedCount == cards.size) {
                        flashcardsIsTranslated = true
                        updateFlashcardsCard(translatedCards)
                        binding.FlashcardsTranslateButton.text = getString(R.string.show_original)
                    }
                },
            )
        }
    }

    private fun translateMindmap() {
        val mindmap =
            summaryResponse?.mindmap
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateMindmap: mindmap is null")
                    Toast.makeText(this, "Mindmap data is not available", Toast.LENGTH_SHORT).show()
                    return
                }

        if (mindmapIsTranslated && mindmapTranslated != null) {
            mindmapIsTranslated = false
            updateMindmapCard(mindmap)
            binding.MindmapTranslateButton.text = getString(R.string.translate)
            return
        }

        val textToTranslate = buildString {
            mindmap.byTopic?.forEach { group ->
                appendLine("Topic: ${group.topic ?: ""}")
                group.description?.let { appendLine("Description: $it") }
                group.words?.joinToString(", ")?.let { appendLine("Words: $it") }
                appendLine()
            }
            mindmap.byDifficulty?.forEach { group ->
                appendLine("Difficulty: ${group.level ?: ""}")
                group.description?.let { appendLine("Description: $it") }
                group.words?.joinToString(", ")?.let { appendLine("Words: $it") }
                appendLine()
            }
            mindmap.byPos?.forEach { group ->
                appendLine("Part of speech: ${group.pos ?: ""}")
                group.words?.joinToString(", ")?.let { appendLine("Words: $it") }
                appendLine()
            }
            mindmap.byRelation?.forEach { group ->
                appendLine("Relation: ${group.groupName ?: ""}")
                group.description?.let { appendLine("Description: $it") }
                // Handle synonyms with clusters
                if (group.groupName?.lowercase() == "synonyms" && !group.clusters.isNullOrEmpty()) {
                    group.clusters.forEachIndexed { index, cluster ->
                        appendLine("Cluster ${index + 1}: ${cluster.joinToString(", ")}")
                    }
                }
                // Handle antonyms with pairs
                else if (
                    group.groupName?.lowercase() == "antonyms" && !group.pairs.isNullOrEmpty()
                ) {
                    group.pairs.forEach { pair ->
                        if (pair.size >= 2) {
                            appendLine("Pair: ${pair[0]} ? ${pair[1]}")
                        }
                    }
                }
                // Default: use words list
                else {
                    group.words?.joinToString(", ")?.let { appendLine("Words: $it") }
                }
                appendLine()
            }
        }
        if (textToTranslate.isBlank()) {
            android.util.Log.d("AISummaryActivity", "translateMindmap: textToTranslate is blank")
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetLang = getCurrentTranslateLanguage()
        android.util.Log.d(
            "AISummaryActivity",
            "translateMindmap: targetLanguage=$targetLang, textLength=${textToTranslate.length}",
        )

        translateText(
            text = textToTranslate,
            targetLanguage = targetLang,
            onSuccess = { translated ->
                android.util.Log.d(
                    "AISummaryActivity",
                    "translateMindmap: success, translatedLength=${translated.length}",
                )
                mindmapTranslated = translated
                mindmapIsTranslated = true
                binding.MindmapContainer.removeAllViews()
                binding.MindmapContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(translated)
                        textSize = 13f
                        setTextIsSelectable(true)
                        setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    }
                )
                binding.MindmapTranslateButton.text = getString(R.string.show_original)
            },
            onError = { error ->
                android.util.Log.e("AISummaryActivity", "translateMindmap: error - $error")
            },
        )
    }

    private fun translateSummaryTable() {
        // ?u tiên l?y t? bi?n instance (?ã hi?n th?), n?u không có thì l?y t? summaryResponse
        val rows =
            summaryTableState
                ?: summaryResponse?.summaryTable
                ?: summaryResponse?.review?.summaryTable
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateSummaryTable: rows is null")
                    Toast.makeText(this, "Summary Table data is not available", Toast.LENGTH_SHORT)
                        .show()
                    return
                }

        if (rows.isEmpty()) {
            android.util.Log.d("AISummaryActivity", "translateSummaryTable: rows is empty")
            Toast.makeText(this, "Summary Table data is not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (summaryTableIsTranslated && summaryTableTranslated != null) {
            // Toggle back to original - l?y t? bi?n instance ho?c summaryResponse
            summaryTableIsTranslated = false
            val originalRows = summaryTableState ?: rows
            updateSummaryTableCard(originalRows)
            binding.SummaryTableTranslateButton.text = getString(R.string.translate)
            return
        }

        val textToTranslate = buildString {
            rows.forEach { row ->
                row.word?.let { appendLine("Word: $it") }
                row.translation?.let { appendLine("Translation: $it") }
                row.partOfSpeech?.let { appendLine("Part of Speech: $it") }
                row.definition?.let { appendLine("Definition: $it") }
                row.usageNote?.let { appendLine("Usage Note: $it") }
                row.commonStructures?.joinToString(", ")?.let { appendLine("Structures: $it") }
                row.collocations?.joinToString(", ")?.let { appendLine("Collocations: $it") }
                appendLine()
            }
        }
        if (textToTranslate.isBlank()) {
            android.util.Log.d(
                "AISummaryActivity",
                "translateSummaryTable: textToTranslate is blank",
            )
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        val targetLang = getCurrentTranslateLanguage()
        android.util.Log.d(
            "AISummaryActivity",
            "translateSummaryTable: targetLanguage=$targetLang, textLength=${textToTranslate.length}",
        )

        // D?ch t?ng row ?? gi? format g?c
        translateSummaryTableWithFormat(rows, targetLang)
    }

    private fun translateText(
        text: String,
        targetLanguage: String,
        onSuccess: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
    ) {
        android.util.Log.d(
            "AISummaryActivity",
            "translateText: textLength=${text.length}, targetLanguage=$targetLanguage",
        )
        binding.ProgressText.text = getString(R.string.translating)
        binding.LoadingLayout.isVisible = true

        lifecycleScope.launch {
            try {
                val result = aiRepository.translateText(text, targetLanguage)
                binding.LoadingLayout.isVisible = false

                when (result) {
                    is AIResult.Success -> {
                        android.util.Log.d(
                            "AISummaryActivity",
                            "translateText: success, translatedLength=${result.data.length}",
                        )
                        onSuccess(result.data)
                    }
                    is AIResult.Error -> {
                        android.util.Log.e(
                            "AISummaryActivity",
                            "translateText: error - ${result.message}",
                        )
                        val errorMessage = result.message ?: getString(R.string.translation_error)
                        Toast.makeText(this@AISummaryActivity, errorMessage, Toast.LENGTH_SHORT)
                            .show()
                        onError?.invoke(errorMessage)
                    }
                    is AIResult.Loading -> {
                        android.util.Log.d("AISummaryActivity", "translateText: loading...")
                        // Already showing loading
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "AISummaryActivity",
                    "translateText: exception - ${e.message}",
                    e,
                )
                binding.LoadingLayout.isVisible = false
                val errorMessage = "Translation error: ${e.message ?: "Unknown error"}"
                Toast.makeText(this@AISummaryActivity, errorMessage, Toast.LENGTH_SHORT).show()
                onError?.invoke(errorMessage)
            }
        }
    }

    enum class AISection {
        SUMMARY,
        BULLET_POINTS,
        QUESTIONS,
        MCQ,
        VOCAB_STORY,
        VOCAB_MCQ,
        VOCAB_FLASHCARDS,
        VOCAB_SUMMARY_TABLE,
        VOCAB_CLOZE,
        VOCAB_MATCH,
    }
}
