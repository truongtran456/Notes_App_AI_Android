package com.philkes.notallyx.presentation.activity.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.NoteHistory
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.ActivityAiHistoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AIHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiHistoryBinding
    private lateinit var repository: AIRepository
    private lateinit var adapter: HistoryAdapter

    private var userId: String = "default_user"
    private var searchJob: Job? = null

    companion object {
        private const val EXTRA_USER_ID = "user_id"

        fun start(context: Context, userId: String? = null) {
            val resolvedId = userId ?: context.getAiUserId()
            val intent =
                Intent(context, AIHistoryActivity::class.java).apply {
                    putExtra(EXTRA_USER_ID, resolvedId)
                }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AIRepository(this)
        userId = intent.getStringExtra(EXTRA_USER_ID) ?: getAiUserId()

        setupToolbar()
        setupRecyclerView()
        setupSearch()

        loadHistory()
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { item ->
            val text =
                item.summaries?.shortParagraph ?: item.summary ?: item.summaries?.oneSentence ?: ""
            if (text.isNotBlank()) {
                AISummaryActivity.start(this, text, noteId = item.noteId?.toLongOrNull() ?: -1L)
            }
        }
        binding.HistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.HistoryRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.SearchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString().orEmpty()
                    searchJob?.cancel()
                    searchJob =
                        lifecycleScope.launch {
                            delay(300)
                            if (query.isBlank()) {
                                loadHistory()
                            } else {
                                searchHistory(query)
                            }
                        }
                }
            }
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.LoadingLayout.isVisible = loading
    }

    private fun updateEmptyState(listEmpty: Boolean) {
        binding.EmptyLayout.isVisible = listEmpty && !binding.LoadingLayout.isVisible
    }

    private fun loadHistory() {
        setLoading(true)
        lifecycleScope.launch {
            when (val result = repository.getUserHistory(userId)) {
                is AIResult.Success -> {
                    adapter.submitList(result.data)
                    setLoading(false)
                    updateEmptyState(result.data.isEmpty())
                }
                is AIResult.Error -> {
                    adapter.submitList(emptyList())
                    setLoading(false)
                    updateEmptyState(true)
                }
                is AIResult.Loading -> setLoading(true)
            }
        }
    }

    private fun searchHistory(query: String) {
        setLoading(true)
        lifecycleScope.launch {
            when (val result = repository.searchHistory(userId, query)) {
                is AIResult.Success -> {
                    adapter.submitList(result.data)
                    setLoading(false)
                    updateEmptyState(result.data.isEmpty())
                }
                is AIResult.Error -> {
                    adapter.submitList(emptyList())
                    setLoading(false)
                    updateEmptyState(true)
                }
                is AIResult.Loading -> setLoading(true)
            }
        }
    }

    private class HistoryAdapter(val onClick: (NoteHistory) -> Unit) :
        RecyclerView.Adapter<HistoryAdapter.Holder>() {

        private val items = mutableListOf<NoteHistory>()

        fun submitList(list: List<NoteHistory>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recycler_ai_history_item, parent, false)
            return Holder(view, onClick)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        class Holder(itemView: View, val onClick: (NoteHistory) -> Unit) :
            RecyclerView.ViewHolder(itemView) {

            private val titleText: TextView = itemView.findViewById(R.id.TitleText)
            private val summaryPreviewText: TextView =
                itemView.findViewById(R.id.SummaryPreviewText)
            private val metaText: TextView = itemView.findViewById(R.id.MetaText)

            fun bind(item: NoteHistory) {
                val context = itemView.context
                titleText.text =
                    item.filename ?: item.noteId ?: context.getString(R.string.ai_history_untitled)

                val preview =
                    item.summary
                        ?: item.summaries?.shortParagraph
                        ?: item.summaries?.oneSentence
                        ?: context.getString(R.string.ai_history_no_summary)
                summaryPreviewText.text = preview

                val typeLabel =
                    when (item.fileType) {
                        "image" -> context.getString(R.string.ai_history_type_image)
                        "audio" -> context.getString(R.string.ai_history_type_audio)
                        "pdf" -> context.getString(R.string.ai_history_type_pdf)
                        "docx" -> context.getString(R.string.ai_history_type_docx)
                        "text" -> context.getString(R.string.ai_history_type_text)
                        else -> item.fileType ?: ""
                    }
                val date = item.createdAt ?: ""
                metaText.text =
                    if (typeLabel.isNotBlank() && date.isNotBlank()) {
                        "$typeLabel ? $date"
                    } else {
                        typeLabel.ifBlank { date }
                    }

                itemView.setOnClickListener { onClick(item) }
            }
        }
    }
}
