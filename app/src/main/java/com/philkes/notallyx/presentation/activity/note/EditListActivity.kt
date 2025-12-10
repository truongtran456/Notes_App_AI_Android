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

        // Collect all items (for file attachments or unchecked items)
        val allItemsText = items.toMutableList().joinToString("\n") { item -> item.body.toString() }
        val checkedVocabItems = checkedItems.joinToString("\n")

        val attachmentUris = getAttachedFileUris()

        // Always process AI (backend will check cache and return cached result if content
        // unchanged)
        // This ensures we always have the latest result and backend cache is checked
        processVocabAI(checkedVocabItems, attachmentUris)
    }

    /**
     * Invalidate cache when checklist items change (checked/unchecked, added/deleted) This ensures
     * fresh AI results when content changes
     */
    private fun invalidateVocabCache() {
        cachedVocabResult = null
    }

    private fun processVocabAI(checkedVocabItems: String, attachmentUris: List<android.net.Uri>) {
        if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
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

        val userId = getAiUserId()
        val noteId = if (notallyModel.id != -1L) notallyModel.id.toString() else null

        lifecycleScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        if (attachmentUris.isNotEmpty()) {
                            aiRepository!!.processCombinedInputs(
                                noteText = checkedVocabItems.ifBlank { null },
                                attachments = attachmentUris,
                                userId = userId,
                                noteId = noteId,
                                contentType = "checklist",
                                checkedVocabItems = checkedVocabItems,
                                useCache = true, // Use backend cache
                            )
                        } else {
                            aiRepository!!.processNoteText(
                                noteText = checkedVocabItems,
                                userId = userId,
                                noteId = noteId,
                                contentType = "checklist",
                                checkedVocabItems = checkedVocabItems,
                                useCache = true, // Use backend cache
                            )
                        }
                    }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data
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

        // Mindmap
        sheetView.findViewById<View>(R.id.ActionMindmap).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_MINDMAP,
                isVocabMode = true,
            )
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

    companion object {
        private const val TAG = "EditListActivity"
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
