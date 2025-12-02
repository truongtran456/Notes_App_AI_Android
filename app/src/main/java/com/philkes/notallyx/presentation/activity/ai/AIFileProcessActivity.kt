package com.philkes.notallyx.presentation.activity.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.ActivityAiSummaryBinding
import kotlinx.coroutines.launch

class AIFileProcessActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_NOTE_TEXT = "extra_note_text"
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val EXTRA_ATTACH_URIS = "extra_attach_uris"
        private const val EXTRA_INITIAL_SECTION = "extra_initial_section"
        private const val EXTRA_SHOW_ALL = "extra_show_all"

        fun start(context: Context, noteText: String, noteId: Long) {
            val intent =
                Intent(context, AIFileProcessActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_TEXT, noteText)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_INITIAL_SECTION, AISummaryActivity.AISection.SUMMARY.name)
                    putExtra(EXTRA_SHOW_ALL, true)
                }
            context.startActivity(intent)
        }

        fun startWithAttachments(
            context: Context,
            noteText: String?,
            noteId: Long,
            attachments: List<Uri>,
            initialSection: AISummaryActivity.AISection,
            showAllSections: Boolean = false,
        ) {
            val intent =
                Intent(context, AIFileProcessActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_TEXT, noteText)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putParcelableArrayListExtra(EXTRA_ATTACH_URIS, ArrayList(attachments))
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                    putExtra(EXTRA_SHOW_ALL, showAllSections)
                }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityAiSummaryBinding
    private lateinit var repository: AIRepository
    private lateinit var userId: String
    private var initialAttachments: ArrayList<Uri>? = null
    private var initialSection: AISummaryActivity.AISection = AISummaryActivity.AISection.SUMMARY
    private var showAllSections: Boolean = false

    private val pickFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) {
                finish()
            } else {
                handleSelectedUris(uris)
            }
        }

    private var noteText: String? = null
    private var noteId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AIRepository(this)
        userId = getAiUserId()
        noteText = intent.getStringExtra(EXTRA_NOTE_TEXT)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        initialAttachments = intent.getParcelableArrayListExtra(EXTRA_ATTACH_URIS)
        showAllSections = intent.getBooleanExtra(EXTRA_SHOW_ALL, false)
        initialSection =
            intent.getStringExtra(EXTRA_INITIAL_SECTION)?.let {
                runCatching { AISummaryActivity.AISection.valueOf(it) }.getOrNull()
            } ?: AISummaryActivity.AISection.SUMMARY

        setupToolbar()
        setupInitialUi()
        val attachments = initialAttachments
        if (!attachments.isNullOrEmpty()) {
            startProcessingCombined(attachments)
        } else {
            openFilePicker()
        }
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
        binding.Toolbar.title = getString(R.string.ai_from_file_title)
    }

    private fun setupInitialUi() {
        binding.CopySummaryButton.isVisible = false
        binding.CopyQuestionsButton.isVisible = false
        binding.CopyRawTextButton.isVisible = false
        binding.ExpandRawTextButton.isVisible = false

        binding.LoadingLayout.isVisible = false
        binding.ContentScrollView.isVisible = false
        binding.ErrorLayout.isVisible = false

        binding.RetryButton.setOnClickListener { openFilePicker() }

        Snackbar.make(binding.root, getString(R.string.ai_choose_file_hint), Snackbar.LENGTH_SHORT)
            .show()
    }

    private fun openFilePicker() {
        pickFilesLauncher.launch(
            arrayOf(
                "image/*",
                "audio/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "*/*",
            )
        )
    }

    private fun handleSelectedUris(uris: List<Uri>) {
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {}
        }
        startProcessingCombined(uris)
    }

    private fun startProcessingCombined(uris: List<Uri>) {
        binding.LoadingLayout.isVisible = true
        binding.ContentScrollView.isVisible = false
        binding.ErrorLayout.isVisible = false
        binding.ProgressStage.text = ""
        binding.ProgressText.text = getString(R.string.ai_processing_file)

        val noteIdString = if (noteId != -1L) noteId.toString() else null

        lifecycleScope.launch {
            when (
                val result =
                    repository.processCombinedInputs(
                        noteText = noteText,
                        attachments = uris,
                        userId = userId,
                        noteId = noteIdString,
                    )
            ) {
                is AIResult.Success -> {
                    AISummaryActivity.startWithResult(
                        context = this@AIFileProcessActivity,
                        summaryResponse = result.data,
                        noteId = noteId,
                        showAllSections = showAllSections,
                        initialSection = initialSection,
                    )
                    finish()
                }
                is AIResult.Error -> {
                    binding.LoadingLayout.isVisible = false
                    binding.ErrorLayout.isVisible = true
                    binding.ErrorMessage.text =
                        result.message.ifBlank { getString(R.string.ai_error_generic) }
                }
                is AIResult.Loading -> {
                    binding.LoadingLayout.isVisible = true
                }
            }
        }
    }
}
