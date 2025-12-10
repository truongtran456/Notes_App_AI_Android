package com.philkes.notallyx.presentation.activity.note

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.presentation.activity.ai.AISummaryActivity
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.action.AddBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.action.MoreListBottomSheet
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
import com.philkes.notallyx.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList
    private lateinit var listManager: ListManager
    private val aiRepository by lazy { AIRepository(this) }
    private var cachedVocabResult: SummaryResponse? = null

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
        // B? c?c thanh d??i t??ng t? EditNoteActivity:
        // LEFT: +, Undo
        binding.BottomAppBarLeft.apply {
            removeAllViews()

            addIconButton(R.string.adding_files, R.drawable.add, marginStart = 0) {
                AddBottomSheet(this@EditListActivity, colorInt)
                    .show(supportFragmentManager, AddBottomSheet.TAG)
            }

            undo =
                addIconButton(R.string.undo, R.drawable.undo, marginStart = 2) {
                        try {
                            changeHistory.undo()
                        } catch (
                            e:
                                com.philkes.notallyx.utils.changehistory.ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canUndo.value }
        }

        // CENTER: n?t AI nh?
        binding.BottomAppBarCenter.apply {
            visibility = android.view.View.GONE
            removeAllViews()
        }

        // T?o FAB AI gradient ? góc d??i bên ph?i
        setupAIFloatingButton()

        // RIGHT: Redo + Draw + More
        binding.BottomAppBarRight.apply {
            removeAllViews()

            redo =
                addIconButton(R.string.redo, R.drawable.redo, marginStart = 0) {
                        try {
                            changeHistory.redo()
                        } catch (
                            e:
                                com.philkes.notallyx.utils.changehistory.ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canRedo.value }

            addIconButton(R.string.draw, R.drawable.ic_pen_pencil, marginStart = 0) {
                openDrawingScreen()
            }

            addIconButton(R.string.more, R.drawable.more_vert, marginStart = 0) {
                MoreListBottomSheet(this@EditListActivity, createFolderActions(), colorInt)
                    .show(supportFragmentManager, MoreListBottomSheet.TAG)
            }
        }

        setBottomAppBarColor(colorInt)
    }

    private fun ensureAICenterButtonForList() {
        if (binding.BottomAppBarCenter.childCount > 0) return
        val button =
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
                .apply {
                    text = getString(R.string.ai_action_button_label)
                    icon = ContextCompat.getDrawable(this@EditListActivity, R.drawable.ai_sparkle)
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
        // L?y text t? c?c item checklist l?m input cho AI (t?m th?i ch? m? ph?n Summary)
        // L?y checked items (ch? nh?ng items ?ã tick) cho checklist mode
        val allItems = items.toMutableList()
        val checkedItems = allItems.filter { it.checked }

        // Text t? checked items (ch? nh?ng items ?ã tick) - dùng cho checklist mode
        val checkedItemsText = checkedItems.joinToString("\n") { item -> item.body.toString() }

        // Text t? t?t c? items (cho các ch?c n?ng không ph?i checklist)
        val noteText = allItems.joinToString("\n") { item -> item.body.toString() }

        // Format checked items thành JSON array ?? g?i lên API
        val checkedVocabItemsJson =
            if (checkedItems.isNotEmpty()) {
                com.google.gson.Gson().toJson(checkedItems.map { it.body.toString() })
            } else {
                null
            }

        android.util.Log.d(
            TAG,
            "openAIActionsMenu: checkedItems=${checkedItems.size}, totalItems=${allItems.size}",
        )
        android.util.Log.d(TAG, "checkedVocabItemsJson: $checkedVocabItemsJson")

        val attachmentUris = getAttachedFileUris()

        if (checkedItems.isEmpty() && noteText.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        // N?u ?ã có cache vocab, hi?n th? bottom sheet ?? ch?n ch?c n?ng
        cachedVocabResult?.let {
            showVocabFunctionSelectionBottomSheet(it)
            return
        }

        val userId = getAiUserId()
        val noteId = notallyModel.id.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val result =
                aiRepository.processCombinedInputs(
                    noteText = checkedItemsText.ifBlank { noteText.ifBlank { null } },
                    attachments = attachmentUris,
                    userId = userId,
                    noteId = noteId,
                    contentType = "checklist",
                    checkedVocabItems = checkedVocabItemsJson,
                    useCache = true,
                )
            withContext(Dispatchers.Main) {
                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data
                        showVocabFunctionSelectionBottomSheet(result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showVocabFunctionSelectionBottomSheet(summaryResponse: SummaryResponse) {
        val bottomSheet = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_vocab_actions, null)
        bottomSheet.setContentView(bottomSheetView)

        // X? lý click cho t?ng m?c ch?c n?ng vocab
        bottomSheetView.findViewById<View>(R.id.ActionSummaryTable)?.setOnClickListener {
            bottomSheet.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = summaryResponse,
                noteId = notallyModel.id,
                showAllSections = false, // Ch? hi?n th? 1 section
                initialSection = AISummaryActivity.AISection.VOCAB_SUMMARY_TABLE,
                isVocabMode = true,
            )
        }

        bottomSheetView.findViewById<View>(R.id.ActionVocabStory)?.setOnClickListener {
            bottomSheet.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = summaryResponse,
                noteId = notallyModel.id,
                showAllSections = false, // Ch? hi?n th? 1 section
                initialSection = AISummaryActivity.AISection.VOCAB_STORY,
                isVocabMode = true,
            )
        }

        bottomSheetView.findViewById<View>(R.id.ActionVocabMCQ)?.setOnClickListener {
            bottomSheet.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = summaryResponse,
                noteId = notallyModel.id,
                showAllSections = false, // Ch? hi?n th? 1 section
                initialSection = AISummaryActivity.AISection.VOCAB_MCQ,
                isVocabMode = true,
            )
        }

        bottomSheetView.findViewById<View>(R.id.ActionFlashcards)?.setOnClickListener {
            bottomSheet.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = summaryResponse,
                noteId = notallyModel.id,
                showAllSections = false, // Ch? hi?n th? 1 section
                initialSection = AISummaryActivity.AISection.VOCAB_FLASHCARDS,
                isVocabMode = true,
            )
        }

        bottomSheetView.findViewById<View>(R.id.ActionMindmap)?.setOnClickListener {
            bottomSheet.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = summaryResponse,
                noteId = notallyModel.id,
                showAllSections = false, // Ch? hi?n th? 1 section
                initialSection = AISummaryActivity.AISection.VOCAB_MINDMAP,
                isVocabMode = true,
            )
        }

        bottomSheet.show()
    }

    private fun getAttachedFileUris(): List<android.net.Uri> {
        val uris = mutableListOf<android.net.Uri>()

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

        return uris
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
        binding.AddItem.setOnClickListener { listManager.add() }
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
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.EXTRA_ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
