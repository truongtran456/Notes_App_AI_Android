package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.main.HomeFilterPillAdapter
import com.philkes.notallyx.presentation.view.main.HomeFilterPillItem
import com.philkes.notallyx.presentation.view.main.DayChip
import com.philkes.notallyx.presentation.view.main.DayChipAdapter
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.hasAnyUpcomingNotifications
import com.philkes.notallyx.databinding.FragmentHomeBinding
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.view.main.HomeTaskAdapter
import com.philkes.notallyx.presentation.view.main.PinnedCarouselAdapter
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeFragment : Fragment() {

    private var binding: FragmentHomeBinding? = null
    private var homeTaskAdapter: HomeTaskAdapter? = null
    private var pinnedCarouselAdapter: PinnedCarouselAdapter? = null
    private var filterPillAdapter: HomeFilterPillAdapter? = null
    private var dayChipAdapter: DayChipAdapter? = null
    private val model: BaseNoteModel by activityViewModels()

    private var currentFilter: FilterType = FilterType.ALL
    private var filteredNotes: MediatorLiveData<List<BaseNote>>? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var currentSource: LiveData<List<Item>>? = null
    private var searchKeyword = ""
    
    // Cache for date conversion to avoid repeated Instant creation
    private val dateCache = mutableMapOf<Long, LocalDate>()
    private val zoneId = ZoneId.systemDefault()
    
    // Cache filterIsInstance results to avoid repeated filtering
    private val baseNoteCache = mutableMapOf<List<Item>, List<BaseNote>>()
    
    // Cache context and strings to avoid repeated calls
    private var cachedContext: android.content.Context? = null
    private var cachedAllString: String? = null
    private var cachedArchivedString: String? = null

    enum class FilterType {
        ALL, ARCHIVED, DELETED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFilterPills()
        setupSearch()
        setupHeader()
        setupObserver()
    }


    private fun setupRecyclerView() {
        binding?.HomeRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false) // Optimize for dynamic content
            setItemViewCacheSize(20) // Cache more views for smoother scrolling

            // Tasks
            homeTaskAdapter = HomeTaskAdapter { note ->
                val intent = when (note.type) {
                    Type.NOTE -> android.content.Intent(context, EditNoteActivity::class.java)
                    Type.LIST -> android.content.Intent(context, EditListActivity::class.java)
                }
                intent.putExtra("EXTRA_SELECTED_BASE_NOTE", note.id)
                startActivity(intent)
            }

            adapter = homeTaskAdapter
            // Padding đã được set trong layout (paddingBottom 140dp)
            clipToPadding = false
        }

        // Filter bar pinned in AppBar
        binding?.FilterPillsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true) // Fixed size for filter pills
            filterPillAdapter = HomeFilterPillAdapter { filterType ->
                selectFilter(filterType)
            }
            adapter = filterPillAdapter
        }

        // Pinned carousel
        binding?.PinnedCarouselRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(false) // Dynamic pinned items
            setItemViewCacheSize(10) // Cache for horizontal scrolling
            pinnedCarouselAdapter = PinnedCarouselAdapter { note ->
                openNote(note)
            }
            adapter = pinnedCarouselAdapter
            clipToPadding = false
            setPadding(0, 0, 20.dp, 0)
        }

        // Day strip
        binding?.DayStripRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true) // Fixed 7 days
            dayChipAdapter = DayChipAdapter { date ->
                selectedDate = date
                refreshDayChips()
                updateFilteredNotes()
                // Update filter pills count khi đổi ngày
                val notes = getBaseNotesFromItems(model.baseNotes?.value ?: emptyList())
                val archivedCount = getBaseNotesFromItems(model.archivedNotes?.value ?: emptyList()).size
                updateFilterPills(archivedCount, notes)
            }
            adapter = dayChipAdapter
            clipToPadding = false
            setPadding(4.dp, 0, 4.dp, 0)
            refreshDayChips()
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun setupFilterPills() {
        // Cache context and strings once
        cachedContext = requireContext()
        cachedAllString = cachedContext?.getString(R.string.all)
        cachedArchivedString = cachedContext?.getString(R.string.archived)
        
        // Setup filter pills with counts - observe LiveData for updates
        var archivedCount = 0
        model.archivedNotes?.observe(viewLifecycleOwner) { archived ->
            archivedCount = getBaseNotesFromItems(archived).size
            updateFilterPills(archivedCount)
        }
        
        model.baseNotes?.observe(viewLifecycleOwner) { items ->
            val notes = getBaseNotesFromItems(items)
            updateFilterPills(archivedCount, notes)
        }
        
        // Initial setup
        val items = model.baseNotes?.value ?: emptyList()
        val notes = getBaseNotesFromItems(items)
        val archivedItems = model.archivedNotes?.value ?: emptyList()
        val archived = getBaseNotesFromItems(archivedItems)
        updateFilterPills(archived.size, notes)
    }
    
    // Helper function to cache filterIsInstance results
    private fun getBaseNotesFromItems(items: List<Item>): List<BaseNote> {
        return baseNoteCache.getOrPut(items) {
            items.filterIsInstance<BaseNote>()
        }
    }
    
    private fun updateFilterPills(archivedCount: Int = 0, notes: List<BaseNote>? = null) {
        val notesList = notes ?: getBaseNotesFromItems(model.baseNotes?.value ?: emptyList())
        // Count notes có reminder trong ngày được chọn
        val allCount = notesList.count { note ->
            note.reminders.any { reminder ->
                isSameDay(reminder.dateTime.time, selectedDate)
            }
        }
        
        val filterItems = listOf(
            HomeFilterPillItem(FilterType.ALL, "${cachedAllString ?: "All"} ($allCount)"),
            HomeFilterPillItem(FilterType.ARCHIVED, "${cachedArchivedString ?: "Archived"} ($archivedCount)")
        )
        filterPillAdapter?.submitList(filterItems)
        if (filterPillAdapter?.currentList?.isEmpty() == true) {
            filterPillAdapter?.setSelectedType(FilterType.ALL)
        }
    }

    private fun selectFilter(filterType: FilterType) {
        currentFilter = filterType
        
        // Update UI
        filterPillAdapter?.setSelectedType(filterType)
        
        // Scroll to selected position trong filter pills (inside filter bar)
        val position = filterPillAdapter?.currentList?.indexOfFirst { it.type == filterType } ?: 0
        binding?.FilterPillsRecyclerView?.smoothScrollToPosition(position)
        
        // Update filtered notes
        updateFilteredNotes()
    }

    private fun setupObserver() {
        if (filteredNotes == null) {
            filteredNotes = MediatorLiveData<List<BaseNote>>()
        }
        
        // Setup observer cho filteredNotes - optimize filtering with debounce
        filteredNotes?.observe(viewLifecycleOwner) { notes ->
            val base = notes ?: emptyList()
            // Notes đã được filter theo reminder date trong processItems rồi
            // Chỉ cần filter search nếu có keyword
            val searched = if (searchKeyword.isBlank()) {
                base
            } else {
                val keyword = searchKeyword.lowercase() // Pre-lowercase for performance
                base.filter { note ->
                    note.title.lowercase().contains(keyword) ||
                        note.body.lowercase().contains(keyword)
                }
            }
            homeTaskAdapter?.submitList(searched)
        }
        
        // Setup filteredNotes ngay lập tức
        updateFilteredNotes()
        
        // Cũng observe baseNotes để update khi nó thay đổi (nếu filter là ALL)
        model.baseNotes?.observe(viewLifecycleOwner) { _ ->
            if (currentFilter == FilterType.ALL) {
                updateFilteredNotes()
            }
            updatePinnedCarousel()
        }
        
        model.archivedNotes?.observe(viewLifecycleOwner) { _ ->
            if (currentFilter == FilterType.ARCHIVED) {
                updateFilteredNotes()
            }
        }
        
        model.deletedNotes?.observe(viewLifecycleOwner) { _ ->
            if (currentFilter == FilterType.DELETED) {
                updateFilteredNotes()
            }
        }
    }

    private fun updatePinnedCarousel() {
        val notes = getBaseNotesFromItems(model.baseNotes?.value ?: emptyList())
        val pinned = notes
            .filter { it.pinned }
            .sortedByDescending { it.modifiedTimestamp } // mới ghim lên đầu
        pinnedCarouselAdapter?.submitList(pinned)
    }

    private fun refreshDayChips() {
        val start = LocalDate.now()
        val days = (0..6).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayChip(date = date, isSelected = date == selectedDate)
        }
        dayChipAdapter?.submitList(days)
    }

    private fun setupSearch() {
        (activity as? com.philkes.notallyx.presentation.activity.main.MainActivity)
            ?.setupHomeSearch { query ->
                searchKeyword = query
                updateFilteredNotes()
            }
    }
    
    private fun setupHeader() {
        (activity as? com.philkes.notallyx.presentation.activity.main.MainActivity)?.let { mainActivity ->
            mainActivity.setupHomeAddButton {
                // Mở dialog để chọn tạo note hoặc list
                showAddNoteDialog()
            }
            mainActivity.setupHomeAvatarButton {
                // Placeholder: mở drawer hoặc profile
                mainActivity.onSupportNavigateUp()
            }
        }
    }

    private fun openNote(note: BaseNote) {
        val intent = when (note.type) {
            Type.NOTE -> android.content.Intent(context, EditNoteActivity::class.java)
            Type.LIST -> android.content.Intent(context, EditListActivity::class.java)
        }
        intent.putExtra("EXTRA_SELECTED_BASE_NOTE", note.id)
        startActivity(intent)
    }
    
    private fun showAddNoteDialog() {
        val options = arrayOf(
            requireContext().getString(com.philkes.notallyx.R.string.take_note),
            requireContext().getString(com.philkes.notallyx.R.string.make_list)
        )
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(requireContext(), EditNoteActivity::class.java)
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(requireContext(), EditListActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    private fun updateFilteredNotes() {
        // Kiểm tra nếu source chưa sẵn sàng
        val source = when (currentFilter) {
            FilterType.ALL -> model.baseNotes
            FilterType.ARCHIVED -> model.archivedNotes
            FilterType.DELETED -> model.deletedNotes
        }
        
        if (source == null) {
            // Source chưa sẵn sàng, return và đợi
            filteredNotes?.value = emptyList()
            return
        }

        // Remove old source
        currentSource?.let { oldSource ->
            filteredNotes?.removeSource(oldSource)
        }

        currentSource = source
        
        // Process current value immediately if available
        val currentItems = source.value ?: emptyList()
        val currentNotes = processItems(currentItems)
        filteredNotes?.value = currentNotes
        
        // Add source for future updates
        filteredNotes?.addSource(source) { items ->
            val notes = processItems(items ?: emptyList())
            filteredNotes?.value = notes
        }
    }
    
    private fun processItems(items: List<Item>): List<BaseNote> {
        // Use cached filterIsInstance result
        val baseNotes = getBaseNotesFromItems(items)
        
        return when (currentFilter) {
            FilterType.ALL -> {
                // Filter notes có reminder trong ngày được chọn (selectedDate)
                baseNotes
                    .filter { note ->
                        note.reminders.any { reminder ->
                            isSameDay(reminder.dateTime.time, selectedDate)
                        }
                    }
                    .sortedByDescending { it.modifiedTimestamp }
            }
            FilterType.ARCHIVED -> {
                baseNotes.sortedByDescending { note -> note.modifiedTimestamp }
            }
            FilterType.DELETED -> {
                baseNotes.sortedByDescending { note -> note.modifiedTimestamp }
            }
        }
    }

    private fun isSameDay(timestamp: Long, date: LocalDate): Boolean {
        // Use cached date conversion to avoid repeated Instant creation
        val noteDate = dateCache.getOrPut(timestamp) {
            Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
        }
        return noteDate == date
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clear caches to free memory
        dateCache.clear()
        baseNoteCache.clear()
        cachedContext = null
        cachedAllString = null
        cachedArchivedString = null
        binding = null
        homeTaskAdapter = null
        pinnedCarouselAdapter = null
        filterPillAdapter = null
        dayChipAdapter = null
    }
}

