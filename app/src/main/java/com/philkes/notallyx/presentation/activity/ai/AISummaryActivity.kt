package com.philkes.notallyx.presentation.activity.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.philkes.notallyx.data.api.models.MCQ
import com.philkes.notallyx.data.api.models.Question
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.ActivityAiSummaryBinding
import kotlinx.coroutines.launch

class AISummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiSummaryBinding
    private lateinit var aiRepository: AIRepository

    private var noteContent: String = ""
    private var noteId: Long = -1L
    private var currentMCQDifficulty: String = "easy"
    private var summaryResponse: SummaryResponse? = null
    private var initialSection: AISection = AISection.SUMMARY
    private var hasScrolledToInitialSection = false
    private var forceShowAllSections = false

    companion object {
        const val EXTRA_NOTE_CONTENT = "note_content"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_INITIAL_SECTION = "initial_section"
        private const val EXTRA_PRECOMPUTED_RESULT = "precomputed_result"
        private const val EXTRA_SHOW_ALL_SECTIONS = "show_all_sections"

        fun start(
            context: Context,
            noteContent: String,
            noteId: Long = -1L,
            initialSection: AISection = AISection.SUMMARY,
        ) {
            val intent =
                Intent(context, AISummaryActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_CONTENT, noteContent)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                }
            context.startActivity(intent)
        }

        fun startWithResult(
            context: Context,
            summaryResponse: SummaryResponse,
            noteId: Long = -1L,
            showAllSections: Boolean = false,
            initialSection: AISection = AISection.SUMMARY,
        ) {
            val intent =
                Intent(context, AISummaryActivity::class.java).apply {
                    putExtra(EXTRA_PRECOMPUTED_RESULT, Gson().toJson(summaryResponse))
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_SHOW_ALL_SECTIONS, showAllSections)
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aiRepository = AIRepository(this)

        setupToolbar()
        extractIntentData()
        setupClickListeners()

        if (summaryResponse != null) {
            displayResults(summaryResponse!!)
        } else if (noteContent.isNotBlank()) {
            summarizeNote()
        } else {
            showError(getString(R.string.ai_error_empty_note))
        }
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
    }

    private fun extractIntentData() {
        noteContent = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        forceShowAllSections = intent.getBooleanExtra(EXTRA_SHOW_ALL_SECTIONS, false)
        initialSection =
            intent.getStringExtra(EXTRA_INITIAL_SECTION)?.let {
                runCatching { AISection.valueOf(it) }.getOrNull()
            } ?: AISection.SUMMARY
        hasScrolledToInitialSection = initialSection == AISection.SUMMARY

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

        lifecycleScope.launch {
            val result =
                aiRepository.summarizeNote(
                    noteText = noteContent,
                    userId = getAiUserId(),
                    noteId = if (noteId != -1L) noteId.toString() else null,
                )

            when (result) {
                is AIResult.Success -> {
                    summaryResponse = result.data
                    displayResults(result.data)
                }
                is AIResult.Error -> {
                    showError(result.message)
                }
                is AIResult.Loading -> {
                    showLoading()
                }
            }
        }
    }

    private fun showLoading() {
        binding.LoadingLayout.isVisible = true
        binding.ErrorLayout.isVisible = false
        binding.ContentScrollView.isVisible = false
    }

    private fun showError(message: String) {
        binding.LoadingLayout.isVisible = false
        binding.ErrorLayout.isVisible = true
        binding.ContentScrollView.isVisible = false
        binding.ErrorMessage.text = message
    }

    private fun showContent() {
        binding.LoadingLayout.isVisible = false
        binding.ErrorLayout.isVisible = false
        binding.ContentScrollView.isVisible = true
    }

    private fun displayResults(
        response: SummaryResponse,
        forceShowAllSections: Boolean = this.forceShowAllSections,
    ) {
        showContent()
        summaryResponse = response
        binding.RetryButton.isVisible = summaryResponse == null

        val displayText = buildDisplayProcessedText(response)
        if (!displayText.isNullOrBlank()) {
            binding.RawTextCard.isVisible = true
            binding.RawTextContent.text = displayText
            binding.RawTextContent.maxLines = 10
            binding.ExpandRawTextButton.isVisible = displayText.length > 200
            binding.ExpandRawTextButton.text = getString(R.string.show_more)
        } else {
            binding.RawTextCard.isVisible = false
        }

        val sources = response.sources
        val summaries = response.summaries
        val showAll = forceShowAllSections

        if (showAll || initialSection == AISection.SUMMARY) {
            updateOneSentenceCard(summaries?.oneSentence)
            updateParagraphCard(summaries?.shortParagraph)
            updateBulletPointsCard(summaries?.bulletPoints)
        } else if (initialSection == AISection.BULLET_POINTS) {
            updateOneSentenceCard(null)
            updateParagraphCard(null)
            updateBulletPointsCard(summaries?.bulletPoints)
        } else {
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

        maybeScrollToInitialSection(forceShowAll = showAll)
    }

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

    private fun buildDisplayProcessedText(response: SummaryResponse): String? {
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
                        builder.append("\n")
                        builder.append(cleaned)
                    }
                }
            }
            return builder.toString().ifBlank { null }
        }

        val primary = response.processedText ?: response.rawText
        return primary?.replace("\\n", "\n")?.trim()
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

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Summary", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.ai_copied, Toast.LENGTH_SHORT).show()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    enum class AISection {
        SUMMARY,
        BULLET_POINTS,
        QUESTIONS,
        MCQ,
    }
}
