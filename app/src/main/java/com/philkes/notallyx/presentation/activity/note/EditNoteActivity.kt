package com.philkes.notallyx.presentation.activity.note

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.data.model.getNoteIdFromUrl
import com.philkes.notallyx.data.model.getNoteTypeFromUrl
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.BottomTextFormattingMenuBinding
import com.philkes.notallyx.databinding.RecyclerToggleBinding
import com.philkes.notallyx.presentation.activity.ai.AISummaryActivity
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_EXCLUDE_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TITLE
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TYPE
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.TextFormattingAdapter
import com.philkes.notallyx.presentation.view.note.action.AddNoteActions
import com.philkes.notallyx.presentation.view.note.action.AddNoteBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreNoteBottomSheet
import com.philkes.notallyx.utils.LinkMovementMethod
import com.philkes.notallyx.utils.copyToClipBoard
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch

class EditNoteActivity : EditActivity(Type.NOTE), AddNoteActions {

    private lateinit var selectedSpan: URLSpan
    private lateinit var pickNoteNewActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickNoteUpdateActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var textFormatMenu: View

    private var textFormattingAdapter: TextFormattingAdapter? = null

    // Cache AI result cho note text (t??ng t? checklist)
    private var cachedTextResult: SummaryResponse? = null
    private var aiRepository: AIRepository? = null

    private var searchResultIndices: List<Pair<Int, Int>>? = null

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        setupEditor()

        if (notallyModel.isNewNote) {
            binding.EnterBody.requestFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupActivityResultLaunchers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putInt(EXTRA_SELECTION_START, binding.EnterBody.selectionStart)
            putInt(EXTRA_SELECTION_END, binding.EnterBody.selectionEnd)
        }
    }

    private fun setupActivityResultLaunchers() {
        pickNoteNewActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        if (emptyTitle) {
                            binding.EnterBody.showAddLinkDialog(
                                this,
                                presetDisplayText = title,
                                presetUrl = url,
                                isNewUnnamedLink = true,
                            )
                        } else {
                            binding.EnterBody.addSpans(title, listOf(UnderlineSpan(), URLSpan(url)))
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
        pickNoteUpdateActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        val newSpan = URLSpan(url)
                        binding.EnterBody.updateSpan(selectedSpan, newSpan, title)
                        if (emptyTitle) {
                            binding.EnterBody.showEditDialog(newSpan, isNewUnnamedLink = true)
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
    }

    override fun highlightSearchResults(search: String): Int {
        binding.EnterBody.clearHighlights()
        if (search.isEmpty()) {
            return 0
        }
        searchResultIndices =
            notallyModel.body.toString().findAllOccurrences(search).onEach { (startIdx, endIdx) ->
                binding.EnterBody.highlight(startIdx, endIdx, false)
            }
        return searchResultIndices!!.size
    }

    override fun selectSearchResult(resultPos: Int) {
        if (resultPos < 0) {
            binding.EnterBody.unselectHighlight()
            return
        }
        searchResultIndices?.get(resultPos)?.let { (startIdx, endIdx) ->
            val selectedLineTop = binding.EnterBody.highlight(startIdx, endIdx, true)
            selectedLineTop?.let { binding.ScrollView.scrollTo(0, it) }
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.EnterBody.initHistory(changeHistory) { text ->
            val textChanged = !notallyModel.body.toString().contentEquals(text)
            notallyModel.body = text
            if (textChanged) {
                updateSearchResults(search.query)
            }
        }
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        updateEditText()
        savedInstanceState?.let {
            val selectionStart = it.getInt(EXTRA_SELECTION_START, -1)
            val selectionEnd = it.getInt(EXTRA_SELECTION_END, -1)
            if (selectionStart > -1) {
                binding.EnterBody.focusAndSelect(selectionStart, selectionEnd)
            }
        }
    }

    private fun updateEditText() {
        binding.EnterBody.text = notallyModel.body
    }

    private fun setupEditor() {
        setupMovementMethod()

        binding.EnterBody.customSelectionActionModeCallback =
            object : ActionMode.Callback {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    binding.EnterBody.isActionModeOn = true
                    // Try block is there because this will crash on MiUI as Xiaomi has a broken
                    // ActionMode implementation
                    try {
                        menu?.apply {
                            add(R.string.link, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.showAddLinkDialog(
                                    this@EditNoteActivity,
                                    mode = mode,
                                )
                            }
                            add(R.string.bold, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.BOLD))
                                mode?.finish()
                            }
                            add(R.string.italic, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.ITALIC))
                                mode?.finish()
                            }
                            add(
                                R.string.monospace,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.applySpan(TypefaceSpan("monospace"))
                                mode?.finish()
                            }
                            add(
                                R.string.strikethrough,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.applySpan(StrikethroughSpan())
                                mode?.finish()
                            }
                            add(
                                R.string.clear_formatting,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.clearFormatting()
                                mode?.finish()
                            }
                        }
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    binding.EnterBody.isActionModeOn = false
                }
            }

        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.EnterBody.customInsertionActionModeCallback =
                object : ActionMode.Callback {
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        binding.EnterBody.isActionModeOn = true
                        // Try block is there because this will crash on MiUI as Xiaomi has a broken
                        // ActionMode implementation
                        try {
                            menu?.apply {
                                add(R.string.link_note, 0, order = Menu.CATEGORY_CONTAINER + 1) {
                                    linkNote(pickNoteNewActivityResultLauncher)
                                    mode?.finish()
                                }
                            }
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        binding.EnterBody.isActionModeOn = false
                    }
                }
        }
        binding.EnterBody.setOnSelectionChange { selStart, selEnd ->
            if (selEnd - selStart > 0) {
                if (!::textFormatMenu.isInitialized || !textFormatMenu.isEnabled) {
                    initBottomTextFormattingMenu()
                }
                if (::textFormatMenu.isInitialized) {
                    textFormatMenu.isEnabled = true
                }
                textFormattingAdapter?.updateTextFormattingToggles(selStart, selEnd)
            } else {
                if (::textFormatMenu.isInitialized && textFormatMenu.isEnabled) {
                    initBottomMenu()
                }
                if (::textFormatMenu.isInitialized) {
                    textFormatMenu.isEnabled = false
                }
            }
        }
        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
    }

    override fun initBottomMenu() {
        super.initBottomMenu()
    }

    override fun openAddItemMenu() {
        AddNoteBottomSheet(this, colorInt).show(supportFragmentManager, AddNoteBottomSheet.TAG)
    }

    override fun openTextFormattingMenu() {
        if (binding.EnterBody.isActionModeOn) {
            initBottomTextFormattingMenu()
        } else {
            binding.EnterBody.requestFocus()
            binding.EnterBody.setSelection(binding.EnterBody.length())
        }
    }

    override fun openMoreMenu() {
        MoreNoteBottomSheet(this, createFolderActions(), colorInt)
            .show(supportFragmentManager, MoreNoteBottomSheet.TAG)
    }

    private fun ensureAICenterButton() {
        if (binding.BottomAppBarCenter.childCount > 0) return
        val button =
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
                .apply {
                    text = getString(R.string.ai_action_button_label)
                    icon = ContextCompat.getDrawable(this@EditNoteActivity, R.drawable.ai_sparkle)
                    iconPadding = 8.dp
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    minWidth = 0
                    minimumWidth = 0
                    setInsetTop(0)
                    setInsetBottom(0)
                    setPaddingRelative(20.dp, 6.dp, 20.dp, 6.dp)
                    cornerRadius = resources.getDimensionPixelSize(R.dimen.dp_20)
                    val primary =
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorPrimary,
                            0,
                        )
                    val onPrimary =
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorOnPrimary,
                            Color.WHITE,
                        )
                    setBackgroundTintList(ColorStateList.valueOf(primary))
                    setTextColor(onPrimary)
                    iconTint = ColorStateList.valueOf(onPrimary)
                    strokeWidth = resources.getDimensionPixelSize(R.dimen.dp_1)
                    strokeColor =
                        ColorStateList.valueOf(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorPrimaryContainer,
                                primary,
                            )
                        )
                    setOnClickListener { openAIActionsMenu() }
                }
        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        binding.BottomAppBarCenter.addView(button, params)
    }

    override fun openAIActionsMenu() {
        val noteText = binding.EnterBody.text?.toString().orEmpty()
        if (noteText.isBlank()) {
            showToast(R.string.ai_error_empty_note)
            return
        }
        runTextAIAndShowActions(noteText)
    }

    private fun showTextActionsBottomSheet(cachedResult: SummaryResponse, backendNoteId: String) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_actions, null)

        sheetView.findViewById<View>(R.id.ActionSummary).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.SUMMARY,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionBullet).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.BULLET_POINTS,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionQuestions).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.QUESTIONS,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionMCQ).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.MCQ,
                isVocabMode = false,
            )
        }

        // ?n 2 ch?c n?ng: AI from File và View History (ch? gi? 4 ch?c n?ng ??u)
        sheetView.findViewById<View>(R.id.ActionFile).visibility = View.GONE
        sheetView.findViewById<View>(R.id.ActionHistory).visibility = View.GONE

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun getAttachedFileUris(): List<Uri> {
        val uris = mutableListOf<Uri>()

        // Files
        val filesRoot = notallyModel.filesRoot
        val fileAttachments = notallyModel.files.value ?: emptyList()
        if (filesRoot != null && fileAttachments.isNotEmpty()) {
            fileAttachments.forEach { attachment ->
                val file = File(filesRoot, attachment.localName)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        // Images
        val imagesRoot = notallyModel.imageRoot
        val imageAttachments = notallyModel.images.value ?: emptyList()
        if (imagesRoot != null && imageAttachments.isNotEmpty()) {
            imageAttachments.forEach { attachment ->
                val file = File(imagesRoot, attachment.localName)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        // Audios
        val audioRoot = notallyModel.audioRoot
        val audioAttachments = notallyModel.audios.value ?: emptyList()
        if (audioRoot != null && audioAttachments.isNotEmpty()) {
            audioAttachments.forEach { audio ->
                val file = File(audioRoot, audio.name)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        return uris.distinct()
    }

    /**
     * Ch?y AI cho note text m?t l?n, sau ?ó hi?n th? bottom sheet ch?c n?ng N?u n?i dung không ??i,
     * th? dùng k?t qu? cache (hash + GET note theo note_id)
     */
    private fun runTextAIAndShowActions(noteText: String) {
        val userId = getAiUserId()
        // V?n l?y attachments (image/audio/file) n?u ghi chú có, ?? g?i /process/combined
        val attachmentUris = getAttachedFileUris()
        val mode = "text"
        val currentHash = computeContentHash(noteText, attachmentUris)
        val localNoteId = notallyModel.id

        // N?u có cachedResult và hash kh?p -> dùng ngay
        if (cachedTextResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                )
            if (currentHash == storedHash) {
                val backendNoteId =
                    com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                        this,
                        localNoteId,
                    ) ?: ensureBackendNoteIdForCurrentNote(noteText, attachmentUris)
                showTextActionsBottomSheet(cachedTextResult!!, backendNoteId)
                return
            }
        }

        val backendNoteId = ensureBackendNoteIdForCurrentNote(noteText, attachmentUris)

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        // Th? GET cache trên server tr??c khi g?i POST
        lifecycleScope.launch {
            try {
                val serverCached =
                    aiRepository!!.getCachedNote(userId, backendNoteId, checkVocabData = false)
                if (serverCached != null) {
                    cachedTextResult = serverCached
                    // L?u hash ?? l?n sau kh?p
                    if (localNoteId != -1L && currentHash != null) {
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                            this@EditNoteActivity,
                            localNoteId,
                            mode,
                            currentHash,
                        )
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                            this@EditNoteActivity,
                            localNoteId,
                            backendNoteId,
                        )
                    }
                    showTextActionsBottomSheet(serverCached, backendNoteId)
                    return@launch
                }
            } catch (_: Exception) {
                // ignore GET cache errors, s? g?i POST
            }

            // G?i POST summarize
            val loadingDialog =
                android.app.ProgressDialog(this@EditNoteActivity).apply {
                    setMessage(getString(R.string.ai_processing))
                    setCancelable(false)
                    show()
                }
            try {
                val result =
                    aiRepository!!.processCombinedInputs(
                        noteText = noteText,
                        attachments = attachmentUris,
                        userId = userId,
                        noteId = backendNoteId,
                        contentType = null,
                        checkedVocabItems = null,
                        useCache = true,
                    )
                loadingDialog.dismiss()
                when (result) {
                    is AIResult.Success -> {
                        cachedTextResult = result.data
                        // L?u hash và backend_note_id
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(
                                    this@EditNoteActivity,
                                    localNoteId,
                                    mode,
                                    currentHash,
                                )
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setBackendNoteId(this@EditNoteActivity, localNoteId, backendNoteId)
                        }
                        showTextActionsBottomSheet(result.data, backendNoteId)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * ??m b?o dùng cùng backend_note_id cho cùng n?i dung.
     * - N?u ?ã có mapping và hash kh?p ? dùng l?i backend_note_id c?.
     * - N?u ch?a có ho?c n?i dung ??i ? sinh UUID m?i, l?u mapping và hash.
     */
    private fun ensureBackendNoteIdForCurrentNote(
        noteText: String,
        attachments: List<Uri>,
    ): String {
        val mode = if (attachments.isEmpty()) "text" else "combined"
        val currentHash = computeContentHash(noteText, attachments)
        val localNoteId = notallyModel.id

        if (localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                )
            val storedBackend =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                    this,
                    localNoteId,
                )

            android.util.Log.d(
                "EditNoteActivity",
                "ensureBackendNoteIdForCurrentNote: localNoteId=$localNoteId, mode=$mode, currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}..., storedBackend=$storedBackend",
            )

            if (currentHash == storedHash && storedBackend != null) {
                android.util.Log.d(
                    "EditNoteActivity",
                    "ensureBackendNoteIdForCurrentNote: Reusing existing backend_note_id=$storedBackend",
                )
                return storedBackend
            }
        }

        // Generate new UUID and persist mapping/hash if possible
        android.util.Log.d(
            "EditNoteActivity",
            "ensureBackendNoteIdForCurrentNote: Hash mismatch or no mapping, generating new UUID. currentHash=${currentHash?.take(16)}...",
        )
        val generated = UUID.randomUUID().toString()
        if (localNoteId != -1L) {
            com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                this,
                localNoteId,
                generated,
            )
            currentHash?.let {
                com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                    it,
                )
            }
            android.util.Log.d(
                "EditNoteActivity",
                "ensureBackendNoteIdForCurrentNote: Generated and saved new backend_note_id=$generated",
            )
        }
        return generated
    }

    private fun computeContentHash(noteText: String, attachments: List<Uri>): String? {
        val trimmed = noteText.trim()
        val hasAttachments = attachments.isNotEmpty()
        if (trimmed.isBlank() && !hasAttachments) return null

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(if (hasAttachments) "combined::".toByteArray() else "text::".toByteArray())
        if (trimmed.isNotBlank()) {
            digest.update(trimmed.toByteArray())
        }
        if (hasAttachments) {
            attachments
                .sortedBy { it.toString() }
                .forEach { uri -> digest.update(uri.toString().toByteArray()) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun initBottomTextFormattingMenu() {
        binding.BottomAppBarCenter.visibility = GONE
        val extractColor = colorInt
        binding.BottomAppBarRight.apply {
            removeAllViews()
            addView(
                RecyclerToggleBinding.inflate(layoutInflater, this, false).root.apply {
                    setIconResource(R.drawable.close)
                    contentDescription = context.getString(R.string.cancel)
                    setOnClickListener { initBottomMenu() }

                    updateLayoutParams<LinearLayout.LayoutParams> {
                        marginEnd = 0
                        marginStart = 10.dp
                    }
                    setControlsContrastColorForAllViews(extractColor)
                    setBackgroundColor(0)
                }
            )
        }
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            requestLayout()
            val layout = BottomTextFormattingMenuBinding.inflate(layoutInflater, this, false)
            layout.RecyclerView.apply {
                textFormattingAdapter =
                    TextFormattingAdapter(this@EditNoteActivity, binding.EnterBody, colorInt)
                adapter = textFormattingAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            textFormatMenu = layout.root
            addView(layout.root)
        }
    }

    override fun linkNote() {
        linkNote(pickNoteNewActivityResultLauncher)
    }

    fun linkNote(activityResultLauncher: ActivityResultLauncher<Intent>) {
        val intent =
            Intent(this, PickNoteActivity::class.java).apply {
                putExtra(EXTRA_EXCLUDE_NOTE_ID, notallyModel.id)
            }
        activityResultLauncher.launch(intent)
    }

    private fun setupMovementMethod() {
        val movementMethod = LinkMovementMethod { span ->
            val items =
                if (span.url.isNoteUrl()) {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.change_note),
                        getString(R.string.edit),
                        getString(R.string.open_note),
                    )
                } else {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.copy),
                        getString(R.string.edit),
                        getString(R.string.open_link),
                    )
                }
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    if (span.url.isNoteUrl())
                        "${getString(R.string.note)}: ${
                            binding.EnterBody.getSpanText(span)
                        }"
                    else span.url
                )
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            binding.EnterBody.removeSpanWithHistory(
                                span,
                                span.url.isNoteUrl() ||
                                    span.url == binding.EnterBody.getSpanText(span),
                            )
                        }
                        1 ->
                            if (span.url.isNoteUrl()) {
                                selectedSpan = span
                                linkNote(pickNoteUpdateActivityResultLauncher)
                            } else {
                                copyToClipBoard(span.url)
                                showToast(R.string.copied_link)
                            }

                        2 -> {
                            binding.EnterBody.showEditDialog(span)
                        }

                        3 -> {
                            span.url?.let {
                                if (it.isNoteUrl()) {
                                    span.navigateToNote()
                                } else {
                                    openLink(span.url)
                                }
                            }
                        }
                    }
                }
                .show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }

    private fun openLink(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).wrapWithChooser(this)
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            showToast(R.string.cant_open_link)
        }
    }

    private fun URLSpan.navigateToNote() {
        val noteId = url.getNoteIdFromUrl()
        val noteType = url.getNoteTypeFromUrl()
        when (noteType) {
            Type.NOTE -> goToActivity(EditNoteActivity::class.java, noteId)
            Type.LIST -> goToActivity(EditListActivity::class.java, noteId)
        }
    }

    private fun goToActivity(activity: Class<out Activity>, noteId: Long) {
        val intent = Intent(this, activity)
        intent.putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
        startActivity(intent)
    }

    private fun Intent?.getPickedNoteData(): Triple<String, String, Boolean> {
        val noteId = this?.getLongExtra(EXTRA_PICKED_NOTE_ID, -1L)!!
        if (noteId == -1L) {
            throw IllegalArgumentException("Invalid note picked!")
        }
        var emptyTitle = false
        val noteTitle =
            this.getStringExtra(EXTRA_PICKED_NOTE_TITLE)!!.ifEmpty {
                emptyTitle = true
                this@EditNoteActivity.getString(R.string.note)
            }
        val noteType = Type.valueOf(this.getStringExtra(EXTRA_PICKED_NOTE_TYPE)!!)
        val noteUrl = noteId.createNoteUrl(noteType)
        return Triple(noteTitle, noteUrl, emptyTitle)
    }

    companion object {
        private const val TAG = "EditNoteActivity"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
