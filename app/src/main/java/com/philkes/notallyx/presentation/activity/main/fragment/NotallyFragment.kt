package com.philkes.notallyx.presentation.activity.main.fragment

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.FragmentNotesBinding
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_FOLDER
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_LABEL
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_FOLDER_FROM
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_FOLDER_TO
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.hideKeyboard
import com.philkes.notallyx.presentation.movedToResId
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.main.BaseNoteVH
import com.philkes.notallyx.presentation.view.main.BaseNoteVHPreferences
import com.philkes.notallyx.presentation.view.main.NotesGridAdapter
import com.philkes.notallyx.presentation.view.main.PinnedNoteAdapter
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView
import com.philkes.notallyx.presentation.viewmodel.preference.Theme

abstract class NotallyFragment : Fragment(), ItemListener {

    private var notesAdapter: BaseNoteAdapter? = null
    private lateinit var openNoteActivityResultLauncher: ActivityResultLauncher<Intent>
    private var lastSelectedNotePosition = -1
    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null

    internal var binding: FragmentNotesBinding? = null

    internal val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister adapter data observer to prevent memory leaks
        adapterDataObserver?.let { observer ->
            notesAdapter?.unregisterAdapterDataObserver(observer)
        }
        adapterDataObserver = null
        // Clear adapters
        binding?.RecyclerView?.adapter = null
        notesAdapter = null
        binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = binding?.RecyclerView?.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)
                val offset = firstVisibleView?.top ?: 0
                outState.putInt(EXTRA_SCROLL_POS, firstVisiblePosition)
                outState.putInt(EXTRA_SCROLL_OFFSET, offset)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.ImageView?.apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.logo)
        }

        setupAdapter()
        setupRecyclerView()
        setupObserver()
        setupSearch()

        setupActivityResultLaunchers()

        savedInstanceState?.let { bundle ->
            val scrollPosition = bundle.getInt(EXTRA_SCROLL_POS, -1)
            val scrollOffset = bundle.getInt(EXTRA_SCROLL_OFFSET, 0)
            if (scrollPosition > -1) {
                binding?.RecyclerView?.post {
                    val layoutManager = binding?.RecyclerView?.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(scrollPosition, scrollOffset)
                }
            }
        }
    }

    private fun setupActivityResultLaunchers() {
        openNoteActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    // If a note has been moved inside of EditActivity
                    // present snackbar to undo it
                    val data = result.data
                    val id = data?.getLongExtra(EXTRA_NOTE_ID, -1)
                    if (id != null) {
                        val folderFrom = Folder.valueOf(data.getStringExtra(EXTRA_FOLDER_FROM)!!)
                        val folderTo = Folder.valueOf(data.getStringExtra(EXTRA_FOLDER_TO)!!)
                        Snackbar.make(
                                binding!!.root,
                                requireContext().getQuantityString(folderTo.movedToResId(), 1),
                                Snackbar.LENGTH_SHORT,
                            )
                            .apply {
                                setAction(R.string.undo) {
                                    model.moveBaseNotes(longArrayOf(id), folderFrom)
                                }
                            }
                            .show()
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        setHasOptionsMenu(true)
        binding = FragmentNotesBinding.inflate(inflater)
        
        // Chỉ dùng gradient khi ở System mode, Light/Dark dùng màu thuần
        if (model.preferences.theme.value == com.philkes.notallyx.presentation.viewmodel.preference.Theme.FOLLOW_SYSTEM) {
            binding?.root?.setBackgroundResource(R.drawable.bg_background)
        }
        
        return binding?.root
    }

    // See [RecyclerView.ViewHolder.getAdapterPosition]
    override fun onClick(position: Int) {
        // Xử lý click từ other notes (position bình thường)
        if (position != -1) {
            val adapter = binding?.RecyclerView?.adapter
            if (adapter is NotesGridAdapter) {
                // NotesGridAdapter: position 0 = "New note", position 1+ = notes
                if (position == 0) return // "New note" được xử lý trong adapter
                // Gọi getItem với position gốc (NotesGridAdapter sẽ tự điều chỉnh)
                val item = adapter.getItem(position)
                item?.let {
                    if (it is BaseNote) {
                        // actualPosition cho handleNoteSelection là position trong baseNoteAdapter
                        val actualPosition = position - 1
                        if (model.actionMode.isEnabled()) {
                            handleNoteSelection(it.id, actualPosition, it)
                        } else {
                            when (it.type) {
                                Type.NOTE -> goToActivity(EditNoteActivity::class.java, it)
                                Type.LIST -> goToActivity(EditListActivity::class.java, it)
                            }
                        }
                    }
                }
            } else {
                // BaseNoteAdapter bình thường
                notesAdapter?.getItem(position)?.let { item ->
                    if (item is BaseNote) {
                        if (model.actionMode.isEnabled()) {
                            handleNoteSelection(item.id, position, item)
                        } else {
                            when (item.type) {
                                Type.NOTE -> goToActivity(EditNoteActivity::class.java, item)
                                Type.LIST -> goToActivity(EditListActivity::class.java, item)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun findNotePositionInList(noteId: Long): Int {
        // Tìm position trong list gốc
        getObservable().value?.forEachIndexed { index, item ->
            if (item is BaseNote && item.id == noteId) {
                return index
            }
        }
        return -1
    }

    override fun onLongClick(position: Int) {
        // Xử lý long click từ other notes (position bình thường)
        if (position != -1) {
            val adapter = binding?.RecyclerView?.adapter
            if (adapter is NotesGridAdapter) {
                // NotesGridAdapter: position 0 = "New note", position 1+ = notes
                if (position == 0) return // "New note" không có long click
                // Gọi getItem với position gốc (NotesGridAdapter sẽ tự điều chỉnh)
                val item = adapter.getItem(position)
                item?.let {
                    if (it is BaseNote) {
                        // actualPosition cho handleNoteSelection là position trong baseNoteAdapter
                        val actualPosition = position - 1
                        if (model.actionMode.selectedNotes.isNotEmpty() && lastSelectedNotePosition != -1) {
                            // Range selection - lastSelectedNotePosition đã là position trong baseNoteAdapter
                            val startPos = if (lastSelectedNotePosition > actualPosition) actualPosition else lastSelectedNotePosition
                            val endPos = if (lastSelectedNotePosition > actualPosition) lastSelectedNotePosition else actualPosition
                            (startPos..endPos).forEach { pos ->
                                notesAdapter!!.getItem(pos)?.let { noteItem ->
                                    if (noteItem is BaseNote) {
                                        if (!model.actionMode.selectedNotes.contains(noteItem.id)) {
                                            handleNoteSelection(noteItem.id, pos, noteItem)
                                        }
                                    }
                                }
                            }
                        } else {
                            handleNoteSelection(it.id, actualPosition, it)
                        }
                    }
                }
            } else {
                // BaseNoteAdapter bình thường
                if (model.actionMode.selectedNotes.isNotEmpty() && lastSelectedNotePosition != -1) {
                    // Range selection
                    val startPos = if (lastSelectedNotePosition > position) position else lastSelectedNotePosition
                    val endPos = if (lastSelectedNotePosition > position) lastSelectedNotePosition else position
                    (startPos..endPos).forEach { pos ->
                        notesAdapter!!.getItem(pos)?.let { item ->
                            if (item is BaseNote) {
                                if (!model.actionMode.selectedNotes.contains(item.id)) {
                                    handleNoteSelection(item.id, pos, item)
                                }
                            }
                        }
                    }
                } else {
                    notesAdapter?.getItem(position)?.let { item ->
                        if (item is BaseNote) {
                            handleNoteSelection(item.id, position, item)
                        }
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        // Setup search từ toolbar - filter ngay trên giao diện hiện tại
        (activity as? MainActivity)?.setupNotesSearch { query ->
            val trimmedQuery = query.trim()
            val oldKeyword = model.keyword
            model.keyword = trimmedQuery
            // Trigger update filteredNotes nếu là NotesFragment và keyword thay đổi
            if (this is NotesFragment && oldKeyword != trimmedQuery) {
                (this as NotesFragment).updateFilteredNotesForSearch()
            }
        }
    }

    private fun handleNoteSelection(id: Long, position: Int, baseNote: BaseNote) {
        if (model.actionMode.selectedNotes.contains(id)) {
            model.actionMode.remove(id)
        } else {
            model.actionMode.add(id, baseNote)
            lastSelectedNotePosition = position
        }
        notesAdapter?.notifyItemChanged(position, 0)
    }

    private fun setupAdapter() {
        val preferences = with(model.preferences) {
            BaseNoteVHPreferences(
                textSize.value,
                maxItems.value,
                maxLines.value,
                maxTitle.value,
                labelsHiddenInOverview.value,
            )
        }
        
        // Setup adapter cho other notes
        notesAdapter =
            with(model.preferences) {
                BaseNoteAdapter(
                    model.actionMode.selectedIds,
                    dateFormat.value,
                    notesSorting.value,
                    preferences,
                    model.imageRoot,
                    this@NotallyFragment,
                )
            }
        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (itemCount > 0) {
                    binding?.RecyclerView?.scrollToPosition(positionStart)
                }
            }
        }
        notesAdapter?.registerAdapterDataObserver(adapterDataObserver!!)
        
        // Wrap adapter với "New note" card cho NotesFragment
        val finalAdapter = if (this is NotesFragment) {
            NotesGridAdapter(
                notesAdapter!!,
                onNewNoteClick = {
                    // Click vào "New note" - mở EditNoteActivity để tạo ghi chú mới
                    val intent = android.content.Intent(requireContext(), com.philkes.notallyx.presentation.activity.note.EditNoteActivity::class.java)
                    val preparedIntent = prepareNewNoteIntent(intent)
                    openNoteActivityResultLauncher.launch(preparedIntent)
                },
                onNoteClick = { position ->
                    // Click vào card ghi chú - điều chỉnh position và gọi onClick
                    onClick(position)
                },
                onNoteLongClick = { position ->
                    // Long click vào card ghi chú - điều chỉnh position và gọi onLongClick
                    onLongClick(position)
                }
            )
        } else {
            notesAdapter
        }
        
        binding?.RecyclerView?.apply {
            adapter = finalAdapter
            setHasFixedSize(false)
        }
        
        model.actionMode.addListener = { 
            // Refresh only selected items instead of entire list
            notesAdapter?.currentList?.let { currentList ->
                currentList.forEachIndexed { index, item ->
                    if (item is BaseNote && model.actionMode.selectedIds.contains(item.id)) {
                        notesAdapter?.notifyItemChanged(index, 0)
                    }
                }
            }
        }
        if (activity is MainActivity) {
            (activity as MainActivity).getCurrentFragmentNotes = {
                notesAdapter?.currentList?.filterIsInstance<BaseNote>()
            }
        }
    }

    private fun setupObserver() {
        // Observe keyword để update khi search
        // Tạo một observer để watch keyword changes
        val keywordObserver = object : androidx.lifecycle.Observer<String> {
            private var lastKeyword = ""
            override fun onChanged(keyword: String) {
                if (keyword != lastKeyword) {
                    lastKeyword = keyword
                    // Trigger update bằng cách observe lại getObservable()
                    // getObservable() sẽ tự động trả về searchResults nếu có keyword
                }
            }
        }
        
        // Observe getObservable() - nó sẽ tự động switch giữa baseNotes và searchResults
        getObservable().observe(viewLifecycleOwner) { list ->
            // Hiển thị tất cả notes (pinned và unpinned) trong một list, không tách riêng
            val allItems = mutableListOf<Item>()
            
            list.forEach { item ->
                // Bỏ qua tất cả Header, chỉ lấy BaseNote
                if (item is BaseNote) {
                    allItems.add(item)
                }
            }
            
            // Cập nhật RecyclerView với tất cả notes
            notesAdapter?.submitList(allItems)
            
            binding?.ImageView?.isVisible = list.isEmpty()
        }

        model.preferences.notesSorting.observe(viewLifecycleOwner) { notesSort ->
            notesAdapter?.setNotesSort(notesSort)
        }

        model.actionMode.closeListener.observe(viewLifecycleOwner) { event ->
            event.handle { ids ->
                notesAdapter?.currentList?.forEachIndexed { index, item ->
                    if (item is BaseNote && ids.contains(item.id)) {
                        notesAdapter?.notifyItemChanged(index, 0)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding?.RecyclerView?.apply {
            // Luôn dùng GridLayoutManager 2 cột cho Notes
            layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            
            // Optimize RecyclerView performance
            setHasFixedSize(false) // Dynamic content size
            setItemViewCacheSize(30) // Cache more views for smoother scrolling (increased from 20)
            recycledViewPool.setMaxRecycledViews(0, 30) // Cache more header views (increased from 20)
            recycledViewPool.setMaxRecycledViews(1, 30) // Cache more note views (increased from 20)
            
            // Enable predictive animations for smoother scrolling
            itemAnimator = null // Disable animations for better performance
            isNestedScrollingEnabled = true
            
            // Thêm padding bottom để các item cuối cùng không bị che bởi bottom bar
            // Bottom bar có margin 20dp + chiều cao của nó (khoảng 56dp) + margin bottom từ window insets
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                resources.getDimensionPixelSize(R.dimen.dp_100) // Padding bottom đủ lớn để không bị che
            )
        }
    }

    private fun goToActivity(activity: Class<*>, baseNote: BaseNote) {
        val intent = Intent(requireContext(), activity)
        intent.putExtra(EXTRA_SELECTED_BASE_NOTE, baseNote.id)
        
        // Đơn giản: Chỉ dùng fade animation chậm hơn
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    requireContext(),
                    R.anim.fade_in_slow,
                    R.anim.fade_out_slow
                )
                openNoteActivityResultLauncher.launch(intent, options)
            } catch (e: Exception) {
                // Fallback nếu animation fail
                openNoteActivityResultLauncher.launch(intent)
            }
        } else {
            openNoteActivityResultLauncher.launch(intent)
        }
    }

    abstract fun getBackground(): Int

    abstract fun getObservable(): LiveData<List<Item>>

    open fun prepareNewNoteIntent(intent: Intent): Intent {
        return intent
    }

    companion object {
        private const val EXTRA_SCROLL_POS = "notallyx.intent.extra.SCROLL_POS"
        private const val EXTRA_SCROLL_OFFSET = "notallyx.intent.extra.SCROLL_OFFSET"
    }
}
