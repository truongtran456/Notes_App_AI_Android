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
import com.philkes.notallyx.presentation.view.main.PinnedNoteAdapter
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView
import com.philkes.notallyx.presentation.viewmodel.preference.Theme

abstract class NotallyFragment : Fragment(), ItemListener {

    private var notesAdapter: BaseNoteAdapter? = null
    private var pinnedNotesAdapter: PinnedNoteAdapter? = null
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
        binding?.PinnedRecyclerView?.adapter = null
        notesAdapter = null
        pinnedNotesAdapter = null
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
        binding?.ImageView?.setImageResource(getBackground())

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
        
        // Dùng nền gradient Home Today cho toàn bộ fragment Notes
        binding?.root?.setBackgroundResource(R.drawable.bg_background_layer)
        
        return binding?.root
    }

    // See [RecyclerView.ViewHolder.getAdapterPosition]
    override fun onClick(position: Int) {
        // Xử lý click từ other notes (position bình thường)
        if (position != -1) {
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
            if (model.actionMode.selectedNotes.isNotEmpty()) {
                if (lastSelectedNotePosition > position) {
                        position..lastSelectedNotePosition
                    } else {
                        lastSelectedNotePosition..position
                    }
                    .forEach { pos ->
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

    private fun setupSearch() {
        // Setup search từ toolbar thay vì search bar ở dưới (đã xóa EnterSearchKeywordLayout)
        (activity as? MainActivity)?.setupNotesSearch { query ->
            val trimmedQuery = query.trim()
            model.keyword = trimmedQuery
            view?.let { view ->
                val navController = view.findNavController()
                if (
                    trimmedQuery.isNotEmpty() &&
                    navController.currentDestination?.id != R.id.Search
                ) {
                    val bundle =
                        Bundle().apply {
                            putSerializable(EXTRA_INITIAL_FOLDER, model.folder.value)
                            putSerializable(EXTRA_INITIAL_LABEL, model.currentLabel)
                        }
                    navController.navigate(R.id.Search, bundle)
                }
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
        
        // Setup adapter cho pinned notes
        pinnedNotesAdapter = PinnedNoteAdapter(
            model.actionMode.selectedIds,
            model.preferences.dateFormat.value,
            preferences,
            model.imageRoot,
            this@NotallyFragment,
            onNoteClick = { note ->
                // Xử lý click vào pinned note
                if (model.actionMode.isEnabled()) {
                    val position = findNotePositionInList(note.id)
                    handleNoteSelection(note.id, position, note)
                } else {
                    when (note.type) {
                        Type.NOTE -> goToActivity(EditNoteActivity::class.java, note)
                        Type.LIST -> goToActivity(EditListActivity::class.java, note)
                    }
                }
            },
            onNoteLongClick = { note ->
                // Xử lý long click vào pinned note
                val position = findNotePositionInList(note.id)
                if (model.actionMode.selectedNotes.isNotEmpty()) {
                    // Xử lý range selection
                    if (lastSelectedNotePosition > position) {
                        position..lastSelectedNotePosition
                    } else {
                        lastSelectedNotePosition..position
                    }.forEach { pos ->
                        getObservable().value?.getOrNull(pos)?.let { item ->
                            if (item is BaseNote && !model.actionMode.selectedNotes.contains(item.id)) {
                                handleNoteSelection(item.id, pos, item)
                            }
                        }
                    }
                } else {
                    handleNoteSelection(note.id, position, note)
                }
            }
        )

        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (itemCount > 0) {
                    binding?.RecyclerView?.scrollToPosition(positionStart)
                }
            }
        }
        notesAdapter?.registerAdapterDataObserver(adapterDataObserver!!)
        binding?.RecyclerView?.apply {
            adapter = notesAdapter
            setHasFixedSize(false)
        }
        
        // Setup RecyclerView ngang cho pinned notes
        binding?.PinnedRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = pinnedNotesAdapter
            setHasFixedSize(false)
        }
        model.actionMode.addListener = { 
            notesAdapter?.notifyDataSetChanged()
            pinnedNotesAdapter?.notifyDataSetChanged()
        }
        if (activity is MainActivity) {
            (activity as MainActivity).getCurrentFragmentNotes = {
                notesAdapter?.currentList?.filterIsInstance<BaseNote>()
            }
        }
    }

    private fun setupObserver() {
        getObservable().observe(viewLifecycleOwner) { list ->
            // Tách pinned notes và other notes
            val pinnedNotes = mutableListOf<BaseNote>()
            val otherItems = mutableListOf<Item>()
            var foundOthersHeader = false
            
            list.forEach { item ->
                when {
                    item is BaseNote && item.pinned -> {
                        pinnedNotes.add(item)
                    }
                    item is Header -> {
                        if (item.label == requireContext().getString(R.string.others)) {
                            foundOthersHeader = true
                            // Không thêm header "Others" vào otherItems vì đã có TextView riêng
                        }
                        // Bỏ qua header "Pinned" vì đã có TextView riêng
                    }
                    foundOthersHeader && item is BaseNote && !item.pinned -> {
                        otherItems.add(item)
                    }
                    !foundOthersHeader && item is BaseNote && !item.pinned -> {
                        // Nếu chưa có header "Others" nhưng có unpinned notes, thêm vào
                        otherItems.add(item)
                    }
                }
            }
            
            // Cập nhật pinned notes RecyclerView
            if (pinnedNotes.isNotEmpty()) {
                binding?.PinnedHeader?.visibility = View.VISIBLE
                binding?.PinnedRecyclerView?.visibility = View.VISIBLE
                pinnedNotesAdapter?.submitList(pinnedNotes)
            } else {
                binding?.PinnedHeader?.visibility = View.GONE
                binding?.PinnedRecyclerView?.visibility = View.GONE
            }
            
            // Cập nhật other notes RecyclerView
            if (otherItems.isNotEmpty()) {
                binding?.OthersHeader?.visibility = View.VISIBLE
                notesAdapter?.submitList(otherItems)
            } else {
                binding?.OthersHeader?.visibility = View.GONE
                notesAdapter?.submitList(emptyList())
            }
            
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
            layoutManager =
                if (model.preferences.notesView.value == NotesView.GRID) {
                    StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
                } else LinearLayoutManager(requireContext())
            
            // Optimize RecyclerView performance
            setHasFixedSize(false) // Dynamic content size
            setItemViewCacheSize(20) // Cache more views for smoother scrolling
            recycledViewPool.setMaxRecycledViews(0, 20) // Cache more header views
            recycledViewPool.setMaxRecycledViews(1, 20) // Cache more note views
            
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
