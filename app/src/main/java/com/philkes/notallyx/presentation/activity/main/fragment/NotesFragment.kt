package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.presentation.view.main.FilterTabAdapter
import com.philkes.notallyx.presentation.view.main.FilterTabItem
import kotlinx.coroutines.launch

class NotesFragment : NotallyFragment() {

    private var filterTabAdapter: FilterTabAdapter? = null
    private var selectedLabel: String? = FilterTabAdapter.TAB_ALL
    private var filteredNotes: MediatorLiveData<List<Item>>? = null
    private var currentSource: LiveData<List<Item>>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
        setupFilterTabs()
    }

    private fun setupFilterTabs() {
        binding?.FilterTabsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            filterTabAdapter = FilterTabAdapter { label ->
                selectedLabel = label
                updateFilteredNotes()
                // Tìm vị trí của label trong list và set selected
                val position = filterTabAdapter?.currentList?.indexOfFirst { it.label == label } ?: 0
                filterTabAdapter?.setSelectedPosition(position)
                // Scroll tab được chọn về đầu tiên (bên trái)
                smoothScrollToPosition(position)
            }
            adapter = filterTabAdapter

            // Load labels và setup adapter
            lifecycleScope.launch {
                val labels = model.getAllLabels()
                val tabs = mutableListOf<FilterTabItem>(
                    FilterTabItem(FilterTabAdapter.TAB_ALL)
                )
                tabs.addAll(labels.map { FilterTabItem(it) })
                filterTabAdapter?.submitList(tabs)
                // Set selected position cho "All" tab
                filterTabAdapter?.setSelectedPosition(0)
            }
        }
    }

    private fun updateFilteredNotes() {
        currentSource?.let { source ->
            filteredNotes?.removeSource(source)
        }
        
        val newSource = if (selectedLabel == FilterTabAdapter.TAB_ALL || selectedLabel == null) {
            model.baseNotes!!
        } else {
            model.getNotesByLabel(selectedLabel!!)
        }
        
        currentSource = newSource
        filteredNotes?.addSource(newSource) { list ->
            filteredNotes?.value = list
        }
    }

    override fun getObservable(): LiveData<List<Item>> {
        if (filteredNotes == null) {
            filteredNotes = MediatorLiveData()
            updateFilteredNotes()
        }
        return filteredNotes!!
    }

    override fun getBackground() = R.drawable.notebook
}
