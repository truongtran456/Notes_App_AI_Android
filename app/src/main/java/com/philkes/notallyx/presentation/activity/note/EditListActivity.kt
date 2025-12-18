package com.philkes.notallyx.presentation.activity.note

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.presentation.activity.ai.AISummaryActivity
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.action.AddBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.indices
import com.philkes.notallyx.presentation.view.note.listitem.sorting.mapIndexed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.getUriForFile
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList
    private lateinit var listManager: ListManager

    // Cache for AI results
    private var cachedVocabResult: SummaryResponse? = null
    private var aiRepository: AIRepository? = null

    override fun finish() {
        notallyModel.setItems(items.toMutableList())
        super.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        notallyModel.setItems(items.toMutableList())
        binding.RecyclerView.focusedChild?.let { focusedChild ->
            val viewHolder = binding.RecyclerView.findContainingViewHolder(focusedChild)
            if (viewHolder is ListItemVH) {
                val itemPos = binding.RecyclerView.getChildAdapterPosition(focusedChild)
                if (itemPos > -1) {
                    val (selectionStart, selectionEnd) = viewHolder.getSelection()
                    outState.apply {
                        putInt(EXTRA_ITEM_POS, itemPos)
                        putInt(EXTRA_SELECTION_START, selectionStart)
                        putInt(EXTRA_SELECTION_END, selectionEnd)
                    }
                }
            }
        }

        super.onSaveInstanceState(outState)
    }

    override fun deleteChecked() {
        listManager.deleteCheckedItems()
    }

    override fun checkAll() {
        listManager.changeCheckedForAll(true)
    }

    override fun uncheckAll() {
        listManager.changeCheckedForAll(false)
    }

    override fun initBottomMenu() {
        super.initBottomMenu()
    }

    override fun openAddItemMenu() {
        AddBottomSheet(this, colorInt).show(supportFragmentManager, AddBottomSheet.TAG)
    }

    override fun openTextFormattingMenu() {}

    override fun openAIActionsMenu() {
        // Collect checked items (vocabulary words)
        val checkedItems =
            items
                .toMutableList()
                .filter { it.checked }
                .map { it.body.toString().trim() }
                .filter { it.isNotBlank() }

        // Collect all items (for better content hash, including unchecked items)
        val allItemsText = items.toMutableList().joinToString("\n") { item -> item.body.toString() }
        val checkedVocabItems = checkedItems.joinToString("\n")

        val attachmentUris = getAttachedFileUris()

        // Always process AI (backend will check cache and return cached result if content
        // unchanged)
        // This ensures we always have the latest result and backend cache is checked
        processVocabAI(checkedVocabItems, attachmentUris, allItemsText)
    }

    /**
     * Invalidate cache when checklist items change (checked/unchecked, added/deleted) This ensures
     * fresh AI results when content changes
     */
    private fun invalidateVocabCache() {
        cachedVocabResult = null
    }

    private fun processVocabAI(
        checkedVocabItems: String,
        attachmentUris: List<android.net.Uri>,
        allItemsText: String,
    ) {
        if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        val userId = getAiUserId()
        val localNoteId = notallyModel.id

        // Check if content has changed by comparing hash
        // This ensures we don't use old backend_note_id for new content
        // Logic similar to AISummaryActivity.extractIntentData() and summarizeNote()
        val currentHash = computeVocabContentHash(checkedVocabItems, attachmentUris, allItemsText)
        val noteIdToUse = ensureBackendNoteIdForVocab(localNoteId, currentHash)

        // N?u ?ã có cached result và hash kh?p, dùng l?i luôn không c?n g?i API
        if (cachedVocabResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    "vocab",
                )
            if (currentHash == storedHash) {
                android.util.Log.d(
                    "EditListActivity",
                    "processVocabAI: Using cached result, hash matches. noteId=$noteIdToUse",
                )
                showVocabActionsBottomSheet(checkedVocabItems, attachmentUris, cachedVocabResult!!)
                return
            }
        }

        // Show loading dialog
        val loadingDialog =
            android.app.ProgressDialog(this).apply {
                setMessage(getString(R.string.ai_processing))
                setCancelable(false)
                show()
            }

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                // Luôn dùng /process/combined ?? ??ng b? v?i note Text và h? tr? file trong t??ng
                // lai
                val result =
                    withContext(Dispatchers.IO) {
                        aiRepository!!.processCombinedInputs(
                            noteText = checkedVocabItems.ifBlank { null },
                            attachments = attachmentUris,
                            userId = userId,
                            noteId = noteIdToUse,
                            contentType = "checklist",
                            checkedVocabItems = checkedVocabItems,
                            useCache = true, // Backend will check cache based on content hash
                        )
                    }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data

                        // Save content hash và backend_note_id n?u có local noteId
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(
                                    this@EditListActivity,
                                    localNoteId,
                                    "vocab",
                                    currentHash,
                                )

                            val existingBackendNoteId =
                                com.philkes.notallyx.data.preferences.AIUserPreferences
                                    .getBackendNoteId(this@EditListActivity, localNoteId)
                            if (
                                existingBackendNoteId == null ||
                                    existingBackendNoteId != noteIdToUse
                            ) {
                                com.philkes.notallyx.data.preferences.AIUserPreferences
                                    .setBackendNoteId(
                                        this@EditListActivity,
                                        localNoteId,
                                        noteIdToUse,
                                    )
                                android.util.Log.d(
                                    "EditListActivity",
                                    "processVocabAI: Saved backend_note_id=$noteIdToUse for localNoteId=$localNoteId",
                                )
                            }
                        }

                        showVocabActionsBottomSheet(checkedVocabItems, attachmentUris, result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // Should not happen in sync call
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun showVocabActionsBottomSheet(
        checkedVocabItems: String,
        attachmentUris: List<android.net.Uri>,
        cachedResult: SummaryResponse,
    ) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_vocab_actions, null)

        // Summary Table
        sheetView.findViewById<View>(R.id.ActionSummaryTable).setOnClickListener {
            dialog.dismiss()
            // Use cached result to display immediately
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_SUMMARY_TABLE,
                isVocabMode = true,
            )
        }

        // Vocab Story
        sheetView.findViewById<View>(R.id.ActionVocabStory).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_STORY,
                isVocabMode = true,
            )
        }

        // Vocab MCQ
        sheetView.findViewById<View>(R.id.ActionVocabMCQ).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_MCQ,
                isVocabMode = true,
            )
        }

        // Flashcards
        sheetView.findViewById<View>(R.id.ActionFlashcards).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_FLASHCARDS,
                isVocabMode = true,
            )
        }

        // Mindmap - ?n ch?c n?ng này (ch? có 6 ch?c n?ng)
        sheetView.findViewById<View>(R.id.ActionMindmap).visibility = View.GONE

        // Cloze
        sheetView.findViewById<View>(R.id.ActionCloze).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_CLOZE,
                isVocabMode = true,
            )
        }

        // Match pairs
        sheetView.findViewById<View>(R.id.ActionMatchPairs).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_MATCH,
                isVocabMode = true,
            )
        }

        // Overall Stats
        sheetView.findViewById<View>(R.id.ActionOverallStats).setOnClickListener {
            val localNoteId = notallyModel.id
            val prefs = getSharedPreferences("quiz_results", MODE_PRIVATE)
            val mcqDone = prefs.getBoolean("note_${localNoteId}_vocab_mcq_completed", false)
            val clozeDone = prefs.getBoolean("note_${localNoteId}_cloze_completed", false)
            val matchDone = prefs.getBoolean("note_${localNoteId}_match_pairs_completed", false)

            if (mcqDone && clozeDone && matchDone) {
                dialog.dismiss()
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = true,
                    initialSection = AISummaryActivity.AISection.VOCAB_MCQ,
                    isVocabMode = true,
                    statsOnly = true, // hi?n th? b?ng th?ng kê, ?n n?i dung khác
                )
            } else {
                showToast(getString(R.string.ai_overall_not_ready))
            }
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun launchAIVocabSummary(
        checkedVocabItems: String,
        section: AISummaryActivity.AISection,
    ) {
        AISummaryActivity.start(
            this,
            checkedVocabItems,
            notallyModel.id,
            section,
            useProcessEndpointForText = true,
            contentType = "checklist",
            checkedVocabItems = checkedVocabItems,
        )
    }

    private fun getAttachedFileUris(): List<android.net.Uri> {
        val uris = mutableListOf<android.net.Uri>()

        // Files
        val filesRoot = notallyModel.filesRoot
        val fileAttachments = notallyModel.files.value ?: emptyList()
        if (filesRoot != null && fileAttachments.isNotEmpty()) {
            fileAttachments.forEach { attachment ->
                val file = java.io.File(filesRoot, attachment.localName)
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
                val file = java.io.File(imagesRoot, attachment.localName)
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
                val file = java.io.File(audioRoot, audio.name)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        return uris.distinct()
    }

    override fun highlightSearchResults(search: String): Int {
        var resultPos = 0
        val alreadyNotifiedItemPos = mutableSetOf<Int>()
        adapter?.clearHighlights()
        val amount =
            items
                .mapIndexed { idx, item ->
                    val occurrences = item.body.findAllOccurrences(search)
                    occurrences.onEach { (startIdx, endIdx) ->
                        adapter?.highlightText(
                            ListItemAdapter.ListItemHighlight(
                                idx,
                                resultPos++,
                                startIdx,
                                endIdx,
                                false,
                            )
                        )
                    }
                    if (occurrences.isNotEmpty()) {
                        alreadyNotifiedItemPos.add(idx)
                    }
                    occurrences.size
                }
                .sum()
        items.indices
            .filter { !alreadyNotifiedItemPos.contains(it) }
            .forEach { adapter?.notifyItemChanged(it) }
        return amount
    }

    override fun selectSearchResult(resultPos: Int) {
        val selectedItemPos = adapter!!.selectHighlight(resultPos)
        if (selectedItemPos != -1) {
            binding.RecyclerView.post {
                binding.RecyclerView.findViewHolderForAdapterPosition(selectedItemPos)
                    ?.itemView
                    ?.let { binding.ScrollView.scrollTo(0, binding.RecyclerView.top + it.top) }
            }
        }
    }

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { listManager.moveFocusToNext(-1) }

        if (notallyModel.isNewNote || notallyModel.items.isEmpty()) {
            listManager.add(pushChange = false)
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.AddItem.setOnClickListener {
            listManager.add()
            // Invalidate cache when item is added
            invalidateVocabCache()
        }

        // Invalidate cache when items change (checked/unchecked, deleted, etc.)
        // This is handled by ListManager callbacks, but we can also add explicit invalidation
        // when needed. For now, cache will be checked against backend content_hash.
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        val elevation = resources.displayMetrics.density * 2
        listManager =
            ListManager(
                binding.RecyclerView,
                changeHistory,
                preferences,
                inputMethodManager,
                {
                    if (isInSearchMode()) {
                        endSearch()
                    }
                },
            ) { _ ->
                if (isInSearchMode() && search.results.value > 0) {
                    updateSearchResults(search.query)
                }
            }
        adapter =
            ListItemAdapter(
                colorInt,
                notallyModel.textSize,
                elevation,
                NotallyXPreferences.getInstance(application),
                listManager,
            )
        val sortCallback =
            when (preferences.listItemSorting.value) {
                ListItemSort.AUTO_SORT_BY_CHECKED -> ListItemSortedByCheckedCallback(adapter)
                else -> ListItemNoSortCallback(adapter)
            }
        items = ListItemSortedList(sortCallback)
        if (sortCallback is ListItemSortedByCheckedCallback) {
            sortCallback.setList(items)
        }
        items.init(notallyModel.items, true)
        adapter?.setList(items)
        binding.RecyclerView.adapter = adapter
        listManager.adapter = adapter!!
        listManager.initList(items)
        savedInstanceState?.let {
            val itemPos = it.getInt(EXTRA_ITEM_POS, -1)
            if (itemPos > -1) {
                binding.RecyclerView.apply {
                    post {
                        scrollToPosition(itemPos)
                        val viewHolder = findViewHolderForLayoutPosition(itemPos)
                        if (viewHolder is ListItemVH) {
                            val selectionStart = it.getInt(EXTRA_SELECTION_START, -1)
                            val selectionEnd = it.getInt(EXTRA_SELECTION_END, -1)
                            viewHolder.focusEditText(
                                selectionStart,
                                selectionEnd,
                                inputMethodManager,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun setColor() {
        super.setColor()
        adapter?.setBackgroundColor(colorInt)
    }

    /**
     * Compute hash for vocab content to detect content changes Similar to
     * AISummaryActivity.computeContentHash() but for vocab mode
     */
    private fun computeVocabContentHash(
        checkedContent: String,
        attachmentUris: List<android.net.Uri>,
        allItemsText: String,
    ): String? {
        val trimmedChecked = checkedContent.trim()
        val hasAttachments = attachmentUris.isNotEmpty()
        val trimmedAllItems = allItemsText.trim()

        if (trimmedChecked.isBlank() && !hasAttachments && trimmedAllItems.isBlank()) return null

        // Include checked items, all items (unchecked), and attachment metadata to detect changes
        val attachmentsMeta =
            if (hasAttachments) {
                attachmentUris.joinToString("|") { uri -> uri.toString() }
            } else {
                ""
            }

        val payload = buildString {
            append("vocab::")
            append(trimmedChecked)
            if (trimmedAllItems.isNotBlank()) {
                append("::all_items::")
                append(trimmedAllItems)
            }
            if (attachmentsMeta.isNotBlank()) {
                append("::attachments::")
                append(attachmentsMeta)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "EditListActivity"
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }

    /**
     * ??m b?o dùng cùng backend_note_id cho checklist n?u n?i dung không ??i.
     * - N?u hash kh?p và có backend_note_id ?ã l?u ? dùng l?i.
     * - N?u ch?a có ho?c hash khác ? sinh UUID m?i, l?u mapping và clear hash c?.
     */
    private fun ensureBackendNoteIdForVocab(localNoteId: Long, currentHash: String?): String {
        if (localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    "vocab",
                )
            val storedBackend =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                    this,
                    localNoteId,
                )

            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: localNoteId=$localNoteId, currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}..., storedBackend=$storedBackend",
            )

            if (currentHash == storedHash && storedBackend != null) {
                android.util.Log.d(
                    "EditListActivity",
                    "ensureBackendNoteIdForVocab: Reusing existing backend_note_id=$storedBackend",
                )
                return storedBackend
            }

            // Hash ??i ho?c ch?a có mapping: clear c?, sinh m?i
            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: Hash mismatch or no mapping, generating new UUID. currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}...",
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.removeBackendNoteId(
                this,
                localNoteId,
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.clearNoteContentHash(
                this,
                localNoteId,
                "vocab",
            )
            val generated = UUID.randomUUID().toString()
            com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                this,
                localNoteId,
                generated,
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                this,
                localNoteId,
                "vocab",
                currentHash,
            )
            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: Generated and saved new backend_note_id=$generated",
            )
            return generated
        }

        // localNoteId = -1 ho?c không có hash ? sinh UUID nh?ng không l?u mapping
        android.util.Log.d(
            "EditListActivity",
            "ensureBackendNoteIdForVocab: localNoteId=$localNoteId or no hash, generating temporary UUID",
        )
        return UUID.randomUUID().toString()
    }
}
