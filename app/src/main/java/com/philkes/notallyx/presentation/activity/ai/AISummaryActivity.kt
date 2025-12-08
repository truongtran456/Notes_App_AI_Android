package com.philkes.notallyx.presentation.activity.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
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
import com.philkes.notallyx.data.api.models.Flashcard
import com.philkes.notallyx.data.api.models.MCQ
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
import kotlinx.coroutines.launch

class AISummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSummaryBinding
    private lateinit var aiRepository: AIRepository

    private var noteContent: String = ""
    private var noteId: Long = -1L
    private var backendNoteId: String? = null
    private var useProcessEndpoint: Boolean = false
    private var currentMCQDifficulty: String = "easy"
    private var summaryResponse: SummaryResponse? = null
    private var initialSection: AISection = AISection.SUMMARY
    private var hasScrolledToInitialSection = false
    private var forceShowAllSections = false
    private var pendingContentHash: String? = null

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
    private var summaryTableIsTranslated = false

    companion object {
        const val EXTRA_NOTE_CONTENT = "note_content"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_BACKEND_NOTE_ID = "backend_note_id"
        const val EXTRA_INITIAL_SECTION = "initial_section"
        const val EXTRA_USE_PROCESS = "use_process_endpoint"
        private const val EXTRA_PRECOMPUTED_RESULT = "precomputed_result"
        private const val EXTRA_SHOW_ALL_SECTIONS = "show_all_sections"

        fun start(
            context: Context,
            noteContent: String,
            noteId: Long = -1L,
            initialSection: AISection = AISection.SUMMARY,
            backendNoteId: String? = null,
            useProcessEndpointForText: Boolean = false,
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
        ) {
            val intent =
                Intent(context, AISummaryActivity::class.java).apply {
                    putExtra(EXTRA_PRECOMPUTED_RESULT, Gson().toJson(summaryResponse))
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_SHOW_ALL_SECTIONS, showAllSections)
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
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

            setupToolbar()
            extractIntentData()
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

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
    }

    private fun extractIntentData() {
        noteContent = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        backendNoteId = intent.getStringExtra(EXTRA_BACKEND_NOTE_ID)
        useProcessEndpoint = intent.getBooleanExtra(EXTRA_USE_PROCESS, false)
        forceShowAllSections = intent.getBooleanExtra(EXTRA_SHOW_ALL_SECTIONS, false)
        initialSection =
            intent.getStringExtra(EXTRA_INITIAL_SECTION)?.let {
                runCatching { AISection.valueOf(it) }.getOrNull()
            } ?: AISection.SUMMARY
        hasScrolledToInitialSection = initialSection == AISection.SUMMARY

        // Debug logging
        android.util.Log.d(
            "AISummaryActivity",
            "extractIntentData: noteContent length=${noteContent.length}, noteId=$noteId, useProcessEndpoint=$useProcessEndpoint, initialSection=$initialSection",
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
                // Use backend_note_id if available, otherwise use local note_id
                val noteIdToUse = backendNoteId ?: (if (noteId != -1L) noteId.toString() else null)

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

                val result =
                    if (useProcessEndpoint) {
                        // For flows like vocab checklist, use /process (text) endpoint
                        aiRepository.processNoteText(
                            noteText = noteContent,
                            userId = userId,
                            noteId = noteIdToUse,
                            contentType = "vocab",
                            useCache = shouldUseCache,
                        )
                    } else {
                        // Default flow: use /summarize endpoint
                        aiRepository.summarizeNote(
                            noteText = noteContent,
                            userId = userId,
                            noteId = noteIdToUse,
                            useCache = shouldUseCache,
                        )
                    }

                when (result) {
                    is AIResult.Success -> {
                        summaryResponse = result.data
                        // Save backend_note_id if not exists and noteId is valid
                        if (backendNoteId == null && noteId != -1L && noteIdToUse != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setBackendNoteId(this@AISummaryActivity, noteId, noteIdToUse)
                        }
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
            binding.RawTextCard.isVisible = false
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
            // Vocab mode (checklist): ?n toàn b? ph?n summary/text m?c ??nh
            updateOneSentenceCard(null)
            updateParagraphCard(null)
            updateBulletPointsCard(null)
            binding.QuestionsCard.isVisible = false
            binding.MCQCard.isVisible = false
        }

        val vocabStory = response.vocabStory ?: response.review?.vocabStory
        val vocabMcqs = response.vocabMcqs ?: response.review?.vocabMcqs
        val flashcards = response.flashcards ?: response.review?.flashcards
        val mindmap = response.mindmap ?: response.review?.mindmap
        val summaryTable = response.summaryTable ?: response.review?.summaryTable

        if (!isVocabMode) {
            // N?u không ? ch? ?? vocab, ch? hi?n th? n?u có (ví d? l?ch s?)
            updateVocabStoryCard(vocabStory)
            updateVocabMCQCard(vocabMcqs)
            updateFlashcardsCard(flashcards)
            updateMindmapCard(mindmap)
            updateSummaryTableCard(summaryTable)
        } else {
            // Vocab mode: ch? hi?n th? ch?c n?ng ???c ch?n
            if (showAll) {
                updateSummaryTableCard(summaryTable)
                updateVocabStoryCard(vocabStory)
                updateVocabMCQCard(vocabMcqs)
                updateFlashcardsCard(flashcards)
                updateMindmapCard(mindmap)
            } else {
                when (initialSection) {
                    AISection.VOCAB_SUMMARY_TABLE -> {
                        updateSummaryTableCard(summaryTable)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateMindmapCard(null)
                    }
                    AISection.VOCAB_STORY -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(vocabStory)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateMindmapCard(null)
                    }
                    AISection.VOCAB_MCQ -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(vocabMcqs)
                        updateFlashcardsCard(null)
                        updateMindmapCard(null)
                    }
                    AISection.VOCAB_FLASHCARDS -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(flashcards)
                        updateMindmapCard(null)
                    }
                    AISection.VOCAB_MINDMAP -> {
                        updateSummaryTableCard(null)
                        updateVocabStoryCard(null)
                        updateVocabMCQCard(null)
                        updateFlashcardsCard(null)
                        updateMindmapCard(mindmap)
                    }
                    else -> {
                        // fallback: hi?n th? t?t c? n?u không kh?p case
                        updateSummaryTableCard(summaryTable)
                        updateVocabStoryCard(vocabStory)
                        updateVocabMCQCard(vocabMcqs)
                        updateFlashcardsCard(flashcards)
                        updateMindmapCard(mindmap)
                    }
                }
            }
        }

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
                    AISection.VOCAB_MINDMAP -> binding.MindmapCard.takeIf { it.isVisible }
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
            this.text = "? $text"
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
        val sources = response.sources
        if (!sources.isNullOrEmpty()) {
            val vocabItemsList = mutableListOf<String>()
            sources.forEachIndexed { index, source ->
                val body = source.processedText ?: source.rawText
                if (!body.isNullOrBlank()) {
                    val cleaned = body.replace("\\n", "\n").trim()
                    if (cleaned.isNotEmpty()) {
                        if (isVocabMode) {
                            // Format vocab: split by lines and get vocab items
                            // Also handle space-separated words if no newlines
                            val items =
                                if (cleaned.contains("\n")) {
                                    cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                } else {
                                    // If no newlines, try to split by common separators or spaces
                                    cleaned
                                        .split(Regex("[,\\s]+"))
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                }
                            vocabItemsList.addAll(items)
                        }
                    }
                }
            }
            if (isVocabMode && vocabItemsList.isNotEmpty()) {
                // Create SpannableString with bold vocab words separated by " - "
                android.util.Log.d(
                    "AISummaryActivity",
                    "buildDisplayProcessedText: vocabItems count=${vocabItemsList.size}",
                )
                return createBoldVocabText(vocabItemsList)
            }
            // For non-vocab mode, return as before
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
            if (isVocabMode) {
                // Format vocab: split by lines and get vocab items
                // Also handle space-separated words if no newlines
                val vocabItems =
                    if (cleaned.contains("\n")) {
                        cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        // If no newlines, split by spaces (but keep multi-word phrases together if
                        // possible)
                        cleaned.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
                    }
                if (vocabItems.isNotEmpty()) {
                    return createBoldVocabText(vocabItems)
                }
            }
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
            return
        }
        binding.VocabStoryTitle.text = story.title ?: getString(R.string.ai_vocab_story)
        binding.VocabStoryParagraphs.removeAllViews()
        story.paragraphs.orEmpty().forEach { paragraph ->
            binding.VocabStoryParagraphs.addView(
                TextView(this).apply {
                    text = markdownBoldToSpannable(paragraph)
                    textSize = 15f
                    setTextIsSelectable(true)
                    setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
                }
            )
        }
        // Hide used words section - already shown in Processed Text above
        binding.VocabStoryUsedWords.isVisible = false
    }

    private fun updateVocabMCQCard(quizzes: List<VocabQuiz>?) {
        val items = quizzes.orEmpty()
        binding.VocabMCQCard.isVisible = items.isNotEmpty()
        binding.VocabMCQContainer.removeAllViews()
        if (items.isEmpty()) return
        items.forEach { quiz -> binding.VocabMCQContainer.addView(createVocabMCQView(quiz)) }
    }

    private fun createVocabMCQView(quiz: VocabQuiz): View {
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dpToPx(), 0, 16.dpToPx())
            }

        // Question text with vocab target if available
        val questionText =
            TextView(this).apply {
                val questionStr = quiz.question ?: ""
                val vocabTarget = quiz.vocabTarget
                val fullQuestion =
                    if (!vocabTarget.isNullOrBlank()) {
                        "$questionStr\n(Target: **$vocabTarget**)"
                    } else {
                        questionStr
                    }
                text = markdownBoldToSpannable(fullQuestion)
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
        if (items.isEmpty()) return

        // Group all flashcards together, format meaning with each word on a new line
        val meaningBuilder = StringBuilder()
        items.forEachIndexed { index, card ->
            val word = card.word ?: card.front ?: ""
            if (word.isNotBlank()) {
                if (index > 0) {
                    meaningBuilder.append("\n")
                }
                meaningBuilder.append("• ").append(word).append(": ")
                if (!card.back?.meaning.isNullOrBlank()) {
                    meaningBuilder.append(card.back?.meaning)
                }
            }
        }

        // Create a single container for all flashcard content
        binding.FlashcardsContainer.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dpToPx(), 0, 12.dpToPx())

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

                // Meaning: each word on a new line
                if (meaningBuilder.isNotEmpty()) {
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

                    // Make each word bold (format: "• word: definition")
                    val pattern = Regex("•\\s+([^:]+):")
                    pattern.findAll(meaningText).forEach { match ->
                        val wordStart = labelLength + 1 + match.range.first
                        val wordEnd = labelLength + 1 + match.range.last - 1
                        if (wordStart >= 0 && wordEnd < spannable.length && wordStart < wordEnd) {
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
                }

                // Example: combine all examples
                val allExamples = items.mapNotNull { it.back?.example }.filter { it.isNotBlank() }
                if (allExamples.isNotEmpty()) {
                    addLine(getString(R.string.example), allExamples.joinToString("\n"))
                }

                // Note: combine all usage notes
                val allNotes = items.mapNotNull { it.back?.usageNote }.filter { it.isNotBlank() }
                if (allNotes.isNotEmpty()) {
                    addLine(getString(R.string.note), allNotes.joinToString("\n"))
                }

                // Synonyms: combine all synonyms
                val allSynonyms = items.flatMap { it.back?.synonyms.orEmpty() }.distinct()
                if (allSynonyms.isNotEmpty()) {
                    addLine("Synonyms", allSynonyms.joinToString(", "))
                }

                // Antonyms: combine all antonyms
                val allAntonyms = items.flatMap { it.back?.antonyms.orEmpty() }.distinct()
                if (allAntonyms.isNotEmpty()) {
                    addLine("Antonyms", allAntonyms.joinToString(", "))
                }

                // Quick Tips: combine all quick tips
                val allQuickTips = items.mapNotNull { it.back?.quickTip }.filter { it.isNotBlank() }
                if (allQuickTips.isNotEmpty()) {
                    addLine("Quick Tips", allQuickTips.joinToString("\n\n"))
                }

                // SRS Schedule info (if available)
                val srsSchedules =
                    items
                        .mapNotNull { it.srsSchedule }
                        .filter {
                            !it.initialPrompt.isNullOrBlank() ||
                                !it.intervals.isNullOrEmpty() ||
                                !it.recallTask.isNullOrBlank()
                        }
                if (srsSchedules.isNotEmpty()) {
                    val srsInfo = buildString {
                        srsSchedules.forEachIndexed { index, schedule ->
                            if (index > 0) append("\n")
                            schedule.initialPrompt?.let { append("Prompt: $it\n") }
                            schedule.intervals?.let { intervals ->
                                append("Review intervals: ${intervals.joinToString(", ")} days\n")
                            }
                            schedule.recallTask?.let { append("Task: $it") }
                        }
                    }
                    if (srsInfo.isNotBlank()) {
                        addLine("SRS Schedule", srsInfo)
                    }
                }
            }
        )
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
                    val wordPattern = Regex("([A-Za-z]+(?:\\s+[A-Za-z]+)*):\\s*([^•]+)")
                    val matches = wordPattern.findAll(meaning)
                    if (matches.count() > 1) {
                        // Multiple words - format each on a new line
                        val meaningBuilder = StringBuilder()
                        matches.forEachIndexed { index, match ->
                            if (index > 0) {
                                meaningBuilder.append("\n")
                            }
                            meaningBuilder
                                .append("• ")
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
            binding.MindmapContainer.addView(
                TextView(this).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 8.dpToPx(), 0, 0)
                }
            )
            description?.let {
                binding.MindmapContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(it)
                        textSize = 13f
                        setPadding(0, 2.dpToPx(), 0, 0)
                        setTextIsSelectable(true)
                    }
                )
            }
            if (!words.isNullOrEmpty()) {
                binding.MindmapContainer.addView(
                    TextView(this).apply {
                        text = words.joinToString(", ")
                        textSize = 13f
                        setPadding(0, 2.dpToPx(), 0, 0)
                        setTextIsSelectable(true)
                    }
                )
            }
        }

        mindmap.byTopic.orEmpty().forEach { group ->
            addSection("Topic: ${group.topic ?: ""}", group.description, group.words)
        }
        mindmap.byDifficulty.orEmpty().forEach { group ->
            addSection("Difficulty: ${group.level ?: ""}", group.description, group.words)
        }
        mindmap.byPos.orEmpty().forEach { group ->
            addSection("Part of speech: ${group.pos ?: ""}", null, group.words)
        }
        mindmap.byRelation.orEmpty().forEach { group ->
            val groupName = group.groupName ?: "Relation"

            // Handle synonyms with clusters
            if (groupName.lowercase() == "synonyms" && !group.clusters.isNullOrEmpty()) {
                binding.MindmapContainer.addView(
                    TextView(this).apply {
                        text = groupName
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 8.dpToPx(), 0, 0)
                    }
                )
                group.description?.let {
                    binding.MindmapContainer.addView(
                        TextView(this).apply {
                            text = markdownBoldToSpannable(it)
                            textSize = 13f
                            setPadding(0, 2.dpToPx(), 0, 0)
                            setTextIsSelectable(true)
                        }
                    )
                }
                group.clusters.forEach { cluster ->
                    binding.MindmapContainer.addView(
                        TextView(this).apply {
                            text = "Cluster: ${cluster.joinToString(", ")}"
                            textSize = 13f
                            setPadding(0, 2.dpToPx(), 0, 0)
                            setTextIsSelectable(true)
                        }
                    )
                }
            }
            // Handle antonyms with pairs
            else if (groupName.lowercase() == "antonyms" && !group.pairs.isNullOrEmpty()) {
                binding.MindmapContainer.addView(
                    TextView(this).apply {
                        text = groupName
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 8.dpToPx(), 0, 0)
                    }
                )
                group.description?.let {
                    binding.MindmapContainer.addView(
                        TextView(this).apply {
                            text = markdownBoldToSpannable(it)
                            textSize = 13f
                            setPadding(0, 2.dpToPx(), 0, 0)
                            setTextIsSelectable(true)
                        }
                    )
                }
                group.pairs.forEach { pair ->
                    if (pair.size >= 2) {
                        binding.MindmapContainer.addView(
                            TextView(this).apply {
                                text = "${pair[0]} ? ${pair[1]}"
                                textSize = 13f
                                setPadding(0, 2.dpToPx(), 0, 0)
                                setTextIsSelectable(true)
                            }
                        )
                    }
                }
            }
            // Default: use words list
            else {
                addSection(groupName, group.description, group.words)
            }
        }
    }

    private fun updateSummaryTableCard(rows: List<VocabSummaryRow>?) {
        val items = rows.orEmpty()
        binding.SummaryTableCard.isVisible = items.isNotEmpty()
        binding.SummaryTableContainer.removeAllViews()
        if (items.isEmpty()) return

        // Group all items together, format meaning with each word on a new line
        val meaningBuilder = StringBuilder()
        items.forEachIndexed { index, row ->
            val word = row.word ?: ""
            if (word.isNotBlank()) {
                if (index > 0) {
                    meaningBuilder.append("\n")
                }
                meaningBuilder.append("• ").append(word).append(": ")
                if (!row.definition.isNullOrBlank()) {
                    meaningBuilder.append(row.definition)
                }
            }
        }

        // Create a single container for all summary table content
        binding.SummaryTableContainer.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10.dpToPx(), 0, 10.dpToPx())

                fun addText(label: String, value: String?) {
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

                // Translation: combine all translations
                val translations = items.mapNotNull { it.translation }.filter { it.isNotBlank() }
                if (translations.isNotEmpty()) {
                    addText(getString(R.string.translation), translations.joinToString(", "))
                }

                // Part of Speech: combine all parts of speech
                val partsOfSpeech =
                    items.mapNotNull { it.partOfSpeech }.filter { it.isNotBlank() }.distinct()
                if (partsOfSpeech.isNotEmpty()) {
                    addText("Part of Speech", partsOfSpeech.joinToString(", "))
                }

                // Meaning: each word on a new line
                if (meaningBuilder.isNotEmpty()) {
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

                    // Make each word bold (format: "• word: definition")
                    val pattern = Regex("•\\s+([^:]+):")
                    pattern.findAll(meaningText).forEach { match ->
                        val wordStart = labelLength + 1 + match.range.first // +1 for newline
                        val wordEnd = labelLength + 1 + match.range.last - 1 // Exclude the colon
                        if (wordStart >= 0 && wordEnd < spannable.length && wordStart < wordEnd) {
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
                }

                // Usage Note: combine all usage notes
                val usageNotes = items.mapNotNull { it.usageNote }.filter { it.isNotBlank() }
                if (usageNotes.isNotEmpty()) {
                    addText("Usage Notes", usageNotes.joinToString("\n\n"))
                }

                // Structures: combine all structures
                val allStructures = items.flatMap { it.commonStructures.orEmpty() }.distinct()
                if (allStructures.isNotEmpty()) {
                    addText("Structures", allStructures.joinToString(", "))
                }

                // Collocations: combine all collocations
                val allCollocations = items.flatMap { it.collocations.orEmpty() }.distinct()
                if (allCollocations.isNotEmpty()) {
                    addText("Collocations", allCollocations.joinToString(", "))
                }
            }
        )
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
            "French" to "Français",
            "German" to "Deutsch",
            "Spanish" to "Español",
            "Italian" to "Italiano",
            "Portuguese" to "Português",
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
                .setTitle("Ch?n ngôn ng? d?ch / Select Translation Language")
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
                            "?ã ch?n: ${supportedLanguages[which].second}",
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
                    Toast.makeText(this, "Ngôn ng? d?ch: $newLangDisplay", Toast.LENGTH_SHORT)
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
        val story =
            summaryResponse?.vocabStory
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateVocabStory: story is null")
                    Toast.makeText(this, "Story data is not available", Toast.LENGTH_SHORT).show()
                    return
                }

        if (vocabStoryIsTranslated && vocabStoryTranslated != null) {
            // Toggle back to original
            vocabStoryIsTranslated = false
            updateVocabStoryCard(story)
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
        val quizzes =
            summaryResponse?.vocabMcqs
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateVocabMCQ: quizzes is null")
                    Toast.makeText(this, "MCQ data is not available", Toast.LENGTH_SHORT).show()
                    return
                }

        if (vocabMCQIsTranslated && vocabMCQTranslated != null) {
            vocabMCQIsTranslated = false
            updateVocabMCQCard(quizzes)
            binding.VocabMCQTranslateButton.text = getString(R.string.translate)
            return
        }

        val textToTranslate = buildString {
            quizzes.forEachIndexed { index, quiz ->
                if (index > 0) appendLine()
                appendLine("Question ${index + 1}:")
                quiz.type?.let { appendLine("Type: $it") }
                quiz.vocabTarget?.let { appendLine("Target: $it") }
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

        translateText(
            text = textToTranslate,
            targetLanguage = targetLang,
            onSuccess = { translated ->
                android.util.Log.d(
                    "AISummaryActivity",
                    "translateVocabMCQ: success, translatedLength=${translated.length}",
                )
                vocabMCQTranslated = translated
                vocabMCQIsTranslated = true
                // Display translated text with formatting
                binding.VocabMCQContainer.removeAllViews()
                binding.VocabMCQContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(translated)
                        textSize = 13f
                        setTextIsSelectable(true)
                        setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    }
                )
                binding.VocabMCQTranslateButton.text = getString(R.string.show_original)
            },
            onError = { error ->
                android.util.Log.e("AISummaryActivity", "translateVocabMCQ: error - $error")
            },
        )
    }

    private fun translateFlashcards() {
        val cards =
            summaryResponse?.flashcards
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateFlashcards: cards is null")
                    Toast.makeText(this, "Flashcards data is not available", Toast.LENGTH_SHORT)
                        .show()
                    return
                }

        if (flashcardsIsTranslated && flashcardsTranslated != null) {
            flashcardsIsTranslated = false
            updateFlashcardsCard(cards)
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
                    meaningBuilder.append("• ").append(word).append(": ")
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

        translateText(
            text = textToTranslate,
            targetLanguage = targetLang,
            onSuccess = { translated ->
                android.util.Log.d(
                    "AISummaryActivity",
                    "translateFlashcards: success, translatedLength=${translated.length}",
                )
                flashcardsTranslated = translated
                flashcardsIsTranslated = true
                binding.FlashcardsContainer.removeAllViews()
                binding.FlashcardsContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(translated)
                        textSize = 13f
                        setTextIsSelectable(true)
                        setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    }
                )
                binding.FlashcardsTranslateButton.text = getString(R.string.show_original)
            },
            onError = { error ->
                android.util.Log.e("AISummaryActivity", "translateFlashcards: error - $error")
            },
        )
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
        val rows =
            summaryResponse?.summaryTable
                ?: run {
                    android.util.Log.d("AISummaryActivity", "translateSummaryTable: rows is null")
                    Toast.makeText(this, "Summary Table data is not available", Toast.LENGTH_SHORT)
                        .show()
                    return
                }

        if (summaryTableIsTranslated && summaryTableTranslated != null) {
            summaryTableIsTranslated = false
            updateSummaryTableCard(rows)
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

        translateText(
            text = textToTranslate,
            targetLanguage = targetLang,
            onSuccess = { translated ->
                android.util.Log.d(
                    "AISummaryActivity",
                    "translateSummaryTable: success, translatedLength=${translated.length}",
                )
                summaryTableTranslated = translated
                summaryTableIsTranslated = true
                binding.SummaryTableContainer.removeAllViews()
                binding.SummaryTableContainer.addView(
                    TextView(this).apply {
                        text = markdownBoldToSpannable(translated)
                        textSize = 14f
                        setTextIsSelectable(true)
                        setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    }
                )
                binding.SummaryTableTranslateButton.text = getString(R.string.show_original)
            },
            onError = { error ->
                android.util.Log.e("AISummaryActivity", "translateSummaryTable: error - $error")
            },
        )
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
        VOCAB_MINDMAP,
        VOCAB_SUMMARY_TABLE,
    }
}
