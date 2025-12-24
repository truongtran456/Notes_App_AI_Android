package com.philkes.notallyx.presentation.view.note.listitem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.api.models.VocabSummaryRow
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import com.philkes.notallyx.data.model.ListItem

class ListItemAdapter(
    @ColorInt var backgroundColor: Int,
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
    private val onVocabClick: (String) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val noteId: Long,
    private val statusStore: WordStatusStore,
) : RecyclerView.Adapter<ListItemVH>() {

    private lateinit var list: ListItemSortedList
    private val callback = ListItemDragCallback(elevation, listManager)
    private val touchHelper = ItemTouchHelper(callback)

    private val highlights = mutableMapOf<Int, MutableList<ListItemHighlight>>()
    private val inlineSummaries = mutableMapOf<Int, InlineSummaryState>()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemCount() = list.size()

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        val item = list[position]
        holder.bind(
            backgroundColor,
            item,
            position,
            highlights.get(position),
            preferences.listItemSorting.value,
            inlineSummaries[position],
            onVocabClick,
            onSpeakClick,
            getStatus(item, position),
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerListItemBinding.inflate(inflater, parent, false)
        binding.root.background = parent.background
        return ListItemVH(
            binding,
            listManager,
            touchHelper,
            textSize,
            ::toggleInlineSummary,
            onVocabClick,
            onSpeakClick,
            ::cycleStatus,
            ::getStatus,
        )
    }

    internal fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        notifyDataSetChanged()
    }

    internal fun setList(list: ListItemSortedList) {
        this.list = list
    }

    internal fun clearHighlights(): Set<Int> {
        val highlightedItemPos =
            highlights.entries.flatMap { (_, value) -> value.map { it.itemPos } }.toSet()
        highlights.clear()
        return highlightedItemPos
        //        itemPos.forEach { notifyItemChanged(it) }
    }

    internal fun highlightText(highlight: ListItemHighlight) {
        if (highlights.containsKey(highlight.itemPos)) {
            highlights[highlight.itemPos]!!.add(highlight)
        } else {
            highlights[highlight.itemPos] = mutableListOf(highlight)
        }
        notifyItemChanged(highlight.itemPos)
    }

    internal fun selectHighlight(pos: Int): Int {
        var selectedItemPos = -1
        highlights.entries.forEach { (_, value) ->
            value.forEach {
                val isSelected = it.selected
                it.selected = it.resultPos == pos
                if (isSelected != it.selected) {
                    notifyItemChanged(it.itemPos)
                }
                if (it.selected) {
                    selectedItemPos = it.itemPos
                }
            }
        }
        return selectedItemPos
    }

    data class ListItemHighlight(
        val itemPos: Int,
        val resultPos: Int,
        val startIdx: Int,
        val endIdx: Int,
        var selected: Boolean,
    )

    data class InlineSummaryState(
        val word: String,
        val row: VocabSummaryRow,
        var expanded: Boolean = true,
    )

    private fun toggleInlineSummary(position: Int) {
        val state = inlineSummaries[position] ?: return
        state.expanded = !state.expanded
        notifyItemChanged(position)
    }

    fun setInlineSummaries(newStates: Map<Int, InlineSummaryState>) {
        // Preserve expanded state for same word/position
        val merged =
            newStates.mapValues { (pos, incoming) ->
                val existing = inlineSummaries[pos]
                val expanded =
                    existing?.let { it.word.equals(incoming.word, true) && it.expanded } ?: true
                incoming.copy(expanded = expanded)
            }
        inlineSummaries.clear()
        inlineSummaries.putAll(merged)
        notifyDataSetChanged()
    }

    fun clearInlineSummary() {
        if (inlineSummaries.isEmpty()) return
        inlineSummaries.clear()
        notifyDataSetChanged()
    }

    fun getInlineSummaries(): Map<Int, InlineSummaryState> = inlineSummaries.toMap()

    private fun statusKey(item: ListItem, position: Int): String {
        val baseId = if (item.id != -1) item.id else position
        return "note_${noteId}_item_$baseId"
    }

    private fun getStatus(item: ListItem, position: Int): WordStatus {
        return statusStore.get(statusKey(item, position))
    }

    private fun cycleStatus(item: ListItem, position: Int) {
        val next = getStatus(item, position).next()
        statusStore.set(statusKey(item, position), next)
        notifyItemChanged(position)
    }

    enum class WordStatus {
        NEW,
        LEARNING,
        MASTERED;

        fun next(): WordStatus =
            when (this) {
                NEW -> LEARNING
                LEARNING -> MASTERED
                MASTERED -> NEW
            }
    }

    interface WordStatusStore {
        fun get(key: String): WordStatus
        fun set(key: String, status: WordStatus)
    }
}
