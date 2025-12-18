package com.philkes.notallyx.presentation.activity.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.ApiClient
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.preferences.AIUserPreferences
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.ActivityAiSummaryBinding
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIFileProcessActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_NOTE_TEXT = "extra_note_text"
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val EXTRA_BACKEND_NOTE_ID = "extra_backend_note_id"
        private const val EXTRA_ATTACH_URIS = "extra_attach_uris"
        private const val EXTRA_INITIAL_SECTION = "extra_initial_section"
        private const val EXTRA_SHOW_ALL = "extra_show_all"
        private const val EXTRA_CONTENT_TYPE = "extra_content_type"
        private const val EXTRA_CHECKED_VOCAB_ITEMS = "extra_checked_vocab_items"

        fun start(context: Context, noteText: String, noteId: Long, backendNoteId: String? = null) {
            val intent =
                Intent(context, AIFileProcessActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_TEXT, noteText)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(EXTRA_INITIAL_SECTION, AISummaryActivity.AISection.SUMMARY.name)
                    putExtra(EXTRA_SHOW_ALL, true)
                    backendNoteId?.let { putExtra(EXTRA_BACKEND_NOTE_ID, it) }
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
            backendNoteId: String? = null,
            contentType: String? = null,
            checkedVocabItems: String? = null,
        ) {
            val intent =
                Intent(context, AIFileProcessActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_TEXT, noteText)
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putParcelableArrayListExtra(EXTRA_ATTACH_URIS, ArrayList(attachments))
                    putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
                    putExtra(EXTRA_SHOW_ALL, showAllSections)
                    backendNoteId?.let { putExtra(EXTRA_BACKEND_NOTE_ID, it) }
                    contentType?.let { putExtra(EXTRA_CONTENT_TYPE, it) }
                    checkedVocabItems?.let { putExtra(EXTRA_CHECKED_VOCAB_ITEMS, it) }
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
    private var isVocabMode: Boolean = false

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
    private var backendNoteId: String? = null
    private var pendingContentHash: String? = null
    private var contentType: String? = null
    private var checkedVocabItems: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AIRepository(this)
        userId = getAiUserId()
        noteText = intent.getStringExtra(EXTRA_NOTE_TEXT)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        backendNoteId = intent.getStringExtra(EXTRA_BACKEND_NOTE_ID)
        initialAttachments = intent.getParcelableArrayListExtra(EXTRA_ATTACH_URIS)
        showAllSections = intent.getBooleanExtra(EXTRA_SHOW_ALL, false)
        contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE)
        checkedVocabItems = intent.getStringExtra(EXTRA_CHECKED_VOCAB_ITEMS)
        initialSection =
            intent.getStringExtra(EXTRA_INITIAL_SECTION)?.let {
                runCatching { AISummaryActivity.AISection.valueOf(it) }.getOrNull()
            } ?: AISummaryActivity.AISection.SUMMARY

        // Detect vocab/checklist mode
        isVocabMode =
            contentType == "vocab" || contentType == "checklist" || checkedVocabItems != null
        isVocabMode = initialSection.isVocabSection()

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

        // ??m b?o note_id g?i lên backend luôn là UUID duy nh?t (tránh trùng sau khi wipe app)
        val noteIdString = ensureBackendNoteId()

        lifecycleScope.launch {
            val shouldUseCache = shouldUseLocalCache(uris)
            // S? d?ng contentType t? intent (c? th? l? "checklist" ho?c "vocab")
            val contentTypeToUse = contentType ?: (if (isVocabMode) "vocab" else null)

            when (
                val result =
                    repository.processCombinedInputs(
                        noteText = noteText,
                        attachments = uris,
                        userId = userId,
                        noteId = noteIdString,
                        contentType = contentTypeToUse,
                        checkedVocabItems = checkedVocabItems,
                        useCache = shouldUseCache,
                    )
            ) {
                is AIResult.Success -> {
                    // Save backend_note_id n?u v?a sinh m?i
                    persistBackendNoteIdIfNeeded(noteIdString)
                    AISummaryActivity.startWithResult(
                        context = this@AIFileProcessActivity,
                        summaryResponse = result.data,
                        noteId = noteId,
                        showAllSections = showAllSections,
                        initialSection = initialSection,
                        isVocabMode = isVocabMode,
                    )
                    persistContentHashIfNeeded()
                    finish()
                }
                is AIResult.Error -> {
                    // N?u l?i là connection abort và có note_id, th? GET l?i t? backend
                    // (có th? backend ?ã x? lý xong nh?ng response b? m?t)
                    val isConnectionError =
                        result.message.contains("connection abort", ignoreCase = true) ||
                            result.message.contains("connection", ignoreCase = true) ||
                            result.message.contains("network", ignoreCase = true)

                    if (isConnectionError && noteIdString.isNotBlank()) {
                        android.util.Log.d(
                            "AIFileProcessActivity",
                            "Connection error detected, attempting to fetch result from backend. noteId=$noteIdString",
                        )
                        // Th? GET l?i k?t qu? t? backend (có th? backend ?ã x? lý xong nh?ng
                        // response b? m?t)
                        try {
                            val cachedResult =
                                withContext(Dispatchers.IO) {
                                    val response =
                                        ApiClient.getService().getNoteById(noteIdString, userId)
                                    if (response.isSuccessful && response.body() != null) {
                                        response.body()
                                    } else {
                                        null
                                    }
                                }
                            if (cachedResult != null) {
                                android.util.Log.d(
                                    "AIFileProcessActivity",
                                    "Successfully retrieved cached result from backend after connection error",
                                )
                                // Save backend_note_id n?u v?a sinh m?i
                                persistBackendNoteIdIfNeeded(noteIdString)
                                AISummaryActivity.startWithResult(
                                    context = this@AIFileProcessActivity,
                                    summaryResponse = cachedResult,
                                    noteId = noteId,
                                    showAllSections = showAllSections,
                                    initialSection = initialSection,
                                    isVocabMode = isVocabMode,
                                )
                                persistContentHashIfNeeded()
                                finish()
                                return@launch
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "AIFileProcessActivity",
                                "Failed to fetch cached result: ${e.message}",
                                e,
                            )
                        }
                    }

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

    /** Sinh backend_note_id d?ng UUID ?? tránh trùng l?p khi local noteId b? reset (wipe app). */
    private fun ensureBackendNoteId(): String {
        backendNoteId?.let {
            return it
        }
        val generated = UUID.randomUUID().toString()
        backendNoteId = generated

        if (noteId != -1L) {
            AIUserPreferences.setBackendNoteId(this, noteId, generated)
        }
        return generated
    }

    private fun persistBackendNoteIdIfNeeded(noteIdString: String?) {
        if (backendNoteId == null && noteId != -1L && noteIdString != null) {
            AIUserPreferences.setBackendNoteId(this, noteId, noteIdString)
        }
    }

    private fun AISummaryActivity.AISection.isVocabSection(): Boolean {
        return this in
            listOf(
                AISummaryActivity.AISection.VOCAB_STORY,
                AISummaryActivity.AISection.VOCAB_MCQ,
                AISummaryActivity.AISection.VOCAB_FLASHCARDS,
                AISummaryActivity.AISection.VOCAB_SUMMARY_TABLE,
            )
    }

    private fun shouldUseLocalCache(uris: List<Uri>): Boolean {
        if (noteId == -1L) {
            pendingContentHash = null
            return false
        }
        val hash = computeContentHash(noteText, uris)
        pendingContentHash = hash
        if (hash.isNullOrBlank()) return false
        val mode = currentHashMode()
        val stored = AIUserPreferences.getNoteContentHash(this, noteId, mode)
        return stored == hash
    }

    private fun persistContentHashIfNeeded() {
        val hash = pendingContentHash
        if (hash.isNullOrBlank() || noteId == -1L) {
            return
        }
        AIUserPreferences.setNoteContentHash(this, noteId, currentHashMode(), hash)
    }

    private fun computeContentHash(noteText: String?, uris: List<Uri>): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        var hasData = false

        noteText
            ?.takeIf { it.isNotBlank() }
            ?.let {
                digest.update("TEXT".toByteArray())
                digest.update(it.trim().toByteArray())
                hasData = true
            }

        uris
            .sortedBy { it.toString() }
            .forEach { uri ->
                digest.update("URI".toByteArray())
                digest.update(uri.toString().toByteArray())
                val doc = DocumentFile.fromSingleUri(this, uri)
                val size = doc?.length() ?: queryFileSize(uri)
                val lastModified = doc?.lastModified() ?: 0L
                digest.update(size.toString().toByteArray())
                digest.update(lastModified.toString().toByteArray())
                hasData = true
            }

        return if (hasData) {
            digest.digest().joinToString("") { "%02x".format(it) }
        } else {
            null
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun currentHashMode(): String = if (isVocabMode) "combined_vocab" else "combined"
}
