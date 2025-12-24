package com.philkes.notallyx.presentation.view.note.listitem

import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextView.INVISIBLE
import android.widget.TextView.VISIBLE
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.leaqi.drawer.SwipeDrawer.DIRECTION_LEFT
import cn.leaqi.drawer.SwipeDrawer.STATE_CLOSE
import cn.leaqi.drawer.SwipeDrawer.STATE_OPEN
import com.philkes.notallyx.data.imports.txt.extractListItems
import com.philkes.notallyx.data.imports.txt.findListSyntaxRegex
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.createListTextWatcherWithHistory
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.misc.EditTextAutoClearFocus
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class ListItemVH(
    val binding: RecyclerListItemBinding,
    val listManager: ListManager,
    touchHelper: ItemTouchHelper,
    textSize: TextSize,
    private val onToggleInlineSummary: (Int) -> Unit,
    private val onVocabClick: (String) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onStatusClick: (ListItem, Int) -> Unit,
    private val getStatus: (ListItem, Int) -> ListItemAdapter.WordStatus,
) : RecyclerView.ViewHolder(binding.root) {

    private var dragHandleInitialY: Float = 0f

    init {
        val body = textSize.editBodySize
        binding.EditText.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            setOnNextAction {
                val position = bindingAdapterPosition + 1
                listManager.add(position)
            }

            textWatcher =
                createListTextWatcherWithHistory(
                    listManager,
                    this@ListItemVH::getAdapterPosition,
                ) { text, start, count ->
                    if (count > 1) {
                        checkListPasted(text, start, count, this)
                    } else {
                        false
                    }
                }

            setOnFocusChangeListener { _, hasFocus ->
                binding.Delete.visibility = if (hasFocus) VISIBLE else INVISIBLE
            }
        }

        binding.DragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragHandleInitialY = event.y
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL -> {
                    val dY = Math.abs(dragHandleInitialY!! - event.y)
                    if (dY > binding.DragHandle.measuredHeight * 0.15f) {
                        touchHelper.startDrag(this)
                    }
                }
            }
            false
        }
        binding.DragHandle.setOnClickListener {
            val word = binding.EditText.text?.toString()?.trim().orEmpty()
            if (word.isNotBlank()) {
                onVocabClick(word)
            }
        }

        binding.SwipeLayout.setOnDrawerChange { view, state, progress ->
            when (state) {
                STATE_OPEN -> listManager.changeIsChild(absoluteAdapterPosition, true)
                STATE_CLOSE -> listManager.changeIsChild(absoluteAdapterPosition, false)
            }
        }
    }

    fun bind(
        @ColorInt backgroundColor: Int,
        item: ListItem,
        position: Int,
        highlights: List<ListItemAdapter.ListItemHighlight>?,
        autoSort: ListItemSort,
        inlineSummary: ListItemAdapter.InlineSummaryState?,
        onVocabClick: (String) -> Unit,
        onSpeakClick: (String) -> Unit,
        status: ListItemAdapter.WordStatus,
    ) {
        updateEditText(item, position)

        updateCheckBox(item, position)

        updateDeleteButton(item, position)

        updateSwipe(item.isChild, position != 0 && !item.checked)
        binding.DragHandle.apply {
            visibility = VISIBLE
            contentDescription = "Drag$position"
        }

        highlights?.let {
            it.forEach { highlight ->
                binding.EditText.highlight(highlight.startIdx, highlight.endIdx, highlight.selected)
            }
        } ?: binding.EditText.clearHighlights()

        renderInlineSummary(inlineSummary)

        renderStatus(item, absoluteAdapterPosition, status)

        binding.root.setControlsContrastColorForAllViews(backgroundColor)
    }

    fun focusEditText(
        selectionStart: Int = binding.EditText.text!!.length,
        selectionEnd: Int = selectionStart,
        inputMethodManager: InputMethodManager?,
    ) {
        binding.EditText.focusAndSelect(selectionStart, selectionEnd, inputMethodManager)
    }

    private fun updateDeleteButton(item: ListItem, position: Int) {
        binding.Delete.apply {
            setOnClickListener { listManager.delete(absoluteAdapterPosition) }
            contentDescription = "Delete$position"
        }
    }

    private fun updateEditText(item: ListItem, position: Int) {
        binding.EditText.apply {
            setText(item.body)
            isEnabled = !item.checked
            setOnKeyListener { _, keyCode, event ->
                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DEL &&
                        item.body.isEmpty()
                ) {
                    // TODO: when there are multiple checked items above it does not jump to the
                    // last
                    // unchecked item but always re-adds a new item
                    listManager.delete(absoluteAdapterPosition, false) != null
                } else {
                    false
                }
            }
            contentDescription = "EditText$position"
        }
    }

    private var checkBoxListener: OnCheckedChangeListener? = null

    private fun updateCheckBox(item: ListItem, position: Int) {
        if (checkBoxListener == null) {
            checkBoxListener = OnCheckedChangeListener { buttonView, isChecked ->
                buttonView!!.setOnCheckedChangeListener(null)
                listManager.changeChecked(absoluteAdapterPosition, isChecked)
                buttonView.setOnCheckedChangeListener(checkBoxListener)
            }
        }
        binding.CheckBox.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.checked
            setOnCheckedChangeListener(checkBoxListener)
            contentDescription = "CheckBox$position"
        }
    }

    private fun updateSwipe(open: Boolean, canSwipe: Boolean) {
        binding.SwipeLayout.apply {
            intercept = canSwipe
            post {
                if (open) {
                    openDrawer(DIRECTION_LEFT, false, false)
                } else {
                    closeDrawer(DIRECTION_LEFT, false, false)
                }
            }
        }
    }

    private fun checkListPasted(
        text: CharSequence,
        start: Int,
        count: Int,
        editText: EditTextAutoClearFocus,
    ): Boolean {
        val changedText = text.substring(start, start + count)
        val containsLines = changedText.isNotBlank() && changedText.contains("\n")
        if (containsLines) {
            changedText
                .findListSyntaxRegex(checkContains = true, plainNewLineAllowed = true)
                ?.let { listSyntaxRegex ->
                    val items = changedText.extractListItems(listSyntaxRegex)
                    if (text.trim().length > count) {
                        editText.setText(text.substring(0, start) + text.substring(start + count))
                    } else {
                        listManager.delete(absoluteAdapterPosition, pushChange = false)
                    }
                    items.forEachIndexed { idx, it ->
                        listManager.add(absoluteAdapterPosition + idx + 1, it, pushChange = true)
                    }
                }
        }
        return containsLines
    }

    fun getSelection(): Pair<Int, Int> {
        return Pair(binding.EditText.selectionStart, binding.EditText.selectionEnd)
    }

    private fun renderInlineSummary(inlineSummary: ListItemAdapter.InlineSummaryState?) {
        val container = binding.AiSummaryContainer
        container.removeAllViews()

        if (inlineSummary == null) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val context = container.context

        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 10.dp())
                gravity = android.view.Gravity.CENTER_VERTICAL
                setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onToggleInlineSummary(pos)
                    }
                }
            }

        val titleView =
            TextView(context).apply {
                text = inlineSummary.word
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        header.addView(titleView)

        val speak =
            android.widget.ImageView(context).apply {
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(8.dp(), 0, 8.dp(), 0)
                setOnClickListener {
                    onSpeakClick(inlineSummary.word)
                }
            }
        header.addView(speak)

        val chevron =
            android.widget.ImageView(context).apply {
                val iconRes =
                    if (inlineSummary.expanded) android.R.drawable.arrow_up_float
                    else android.R.drawable.arrow_down_float
                setImageResource(iconRes)
                setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
        header.addView(chevron)

        container.addView(header)

        val detailContainer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (inlineSummary.expanded) View.VISIBLE else View.GONE
                setPadding(0, 10.dp(), 0, 10.dp())
            }

        fun addField(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            val fieldView =
                TextView(context).apply {
                    text = "$label: $value"
                    textSize = 14.5f
                    setPadding(0, 6.dp(), 0, 6.dp())
                    setTextColor(
                        ContextCompat.getColor(context, android.R.color.secondary_text_light)
                    )
                    setTypeface(null, Typeface.BOLD)
                    setLineSpacing(0f, 1.18f)
                }
            detailContainer.addView(fieldView)
        }

        fun addBullet(label: String, values: List<String>?) {
            if (values.isNullOrEmpty()) return
            val title =
                TextView(context).apply {
                    text = "$label:"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setPadding(0, 6.dp(), 0, 2.dp())
                }
            detailContainer.addView(title)
            values.forEach {
                val bullet =
                    TextView(context).apply {
                        text = "• $it"
                        textSize = 14f
                        setPadding(12.dp(), 4.dp(), 0, 4.dp())
                        setTextColor(
                            ContextCompat.getColor(context, android.R.color.secondary_text_light)
                        )
                        setLineSpacing(0f, 1.16f)
                    }
                detailContainer.addView(bullet)
            }
        }

        val row = inlineSummary.row
        addField("Dịch", row.translation)
        row.phonetic?.let {
            val phonetic =
                TextView(context).apply {
                    text = it
                    textSize = 13.5f
                    setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    setPadding(0, 4.dp(), 0, 8.dp())
                    setLineSpacing(0f, 1.16f)
                }
            detailContainer.addView(phonetic)
        }
        addField("Loại từ", row.partOfSpeech)
        addField("Định nghĩa", row.definition)
        addField("Cách dùng", row.usageNote)
        addBullet("Cấu trúc", row.commonStructures)
        addBullet("Cụm từ", row.collocations)

        container.addView(detailContainer)
    }

    private fun Int.dp(): Int =
        (this * binding.root.resources.displayMetrics.density).toInt()

    private fun renderStatus(item: ListItem, position: Int, status: ListItemAdapter.WordStatus) {
        val view = binding.StatusIcon
        val (label, color) =
            when (status) {
                ListItemAdapter.WordStatus.NEW -> "●" to android.R.color.darker_gray
                ListItemAdapter.WordStatus.LEARNING -> "↺" to android.R.color.holo_orange_dark
                ListItemAdapter.WordStatus.MASTERED -> "✓" to android.R.color.holo_green_dark
            }
        view.text = label
        view.setTextColor(ContextCompat.getColor(view.context, color))
        view.setOnClickListener { onStatusClick(item, position) }
    }
}
