package com.philkes.notallyx.presentation.activity.note

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.preferences.getAiUserId
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

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList
    private lateinit var listManager: ListManager

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
        AddBottomSheet(this, colorInt)
                    .show(supportFragmentManager, AddBottomSheet.TAG)
            }

    override fun openTextFormattingMenu() {
    }

    override fun openAIActionsMenu() {
        val noteText = items.toMutableList().joinToString("\n") { item -> item.body.toString() }
        val attachmentUris = getAttachedFileUris()
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_actions, null)

        sheetView.findViewById<android.view.View>(R.id.ActionSummary).setOnClickListener {
            if (noteText.isBlank() && attachmentUris.isEmpty()) {
                showToast(R.string.ai_error_empty_note)
                return@setOnClickListener
            }
            dialog.dismiss()
            if (attachmentUris.isNotEmpty()) {
                com.philkes.notallyx.presentation.activity.ai.AIFileProcessActivity.startWithAttachments(
                    context = this,
                    noteText = noteText.ifBlank { null },
                    noteId = notallyModel.id,
                    attachments = attachmentUris,
                    initialSection = com.philkes.notallyx.presentation.activity.ai.AISummaryActivity.AISection.SUMMARY,
                )
            } else {
                AISummaryActivity.start(
                    this,
                    noteText,
                    notallyModel.id,
                    AISummaryActivity.AISection.SUMMARY,
                )
            }
        }

        sheetView.findViewById<android.view.View>(R.id.ActionBullet).setOnClickListener {
            if (noteText.isBlank() && attachmentUris.isEmpty()) {
                showToast(R.string.ai_error_empty_note)
                return@setOnClickListener
            }
            dialog.dismiss()
            if (attachmentUris.isNotEmpty()) {
                com.philkes.notallyx.presentation.activity.ai.AIFileProcessActivity.startWithAttachments(
                    context = this,
                    noteText = noteText.ifBlank { null },
                    noteId = notallyModel.id,
                    attachments = attachmentUris,
                    initialSection = com.philkes.notallyx.presentation.activity.ai.AISummaryActivity.AISection.BULLET_POINTS,
                )
            } else {
                AISummaryActivity.start(
                    this,
                    noteText,
                    notallyModel.id,
                    AISummaryActivity.AISection.BULLET_POINTS,
                )
            }
        }

        sheetView.findViewById<android.view.View>(R.id.ActionQuestions).setOnClickListener {
            if (noteText.isBlank() && attachmentUris.isEmpty()) {
                showToast(R.string.ai_error_empty_note)
                return@setOnClickListener
            }
            dialog.dismiss()
            if (attachmentUris.isNotEmpty()) {
                com.philkes.notallyx.presentation.activity.ai.AIFileProcessActivity.startWithAttachments(
                    context = this,
                    noteText = noteText.ifBlank { null },
                    noteId = notallyModel.id,
                    attachments = attachmentUris,
                    initialSection = com.philkes.notallyx.presentation.activity.ai.AISummaryActivity.AISection.QUESTIONS,
                )
            } else {
                AISummaryActivity.start(
                            this,
                    noteText,
                    notallyModel.id,
                    AISummaryActivity.AISection.QUESTIONS,
                )
            }
        }

        sheetView.findViewById<android.view.View>(R.id.ActionMCQ).setOnClickListener {
            if (noteText.isBlank() && attachmentUris.isEmpty()) {
                showToast(R.string.ai_error_empty_note)
                return@setOnClickListener
            }
            dialog.dismiss()
            if (attachmentUris.isNotEmpty()) {
                com.philkes.notallyx.presentation.activity.ai.AIFileProcessActivity.startWithAttachments(
                    context = this,
                    noteText = noteText.ifBlank { null },
                    noteId = notallyModel.id,
                    attachments = attachmentUris,
                    initialSection = com.philkes.notallyx.presentation.activity.ai.AISummaryActivity.AISection.MCQ,
                )
            } else {
        AISummaryActivity.start(
            this,
            noteText,
            notallyModel.id,
                    AISummaryActivity.AISection.MCQ,
                )
            }
        }

        sheetView.findViewById<android.view.View>(R.id.ActionFile).setOnClickListener {
            dialog.dismiss()
            com.philkes.notallyx.presentation.activity.ai.AIFileProcessActivity.start(
                context = this,
                noteText = noteText,
                noteId = notallyModel.id,
            )
        }

        sheetView.findViewById<android.view.View>(R.id.ActionHistory).setOnClickListener {
            dialog.dismiss()
            com.philkes.notallyx.presentation.activity.ai.AIHistoryActivity.start(this, userId = getAiUserId())
        }

        dialog.setContentView(sheetView)
        dialog.show()
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
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}

