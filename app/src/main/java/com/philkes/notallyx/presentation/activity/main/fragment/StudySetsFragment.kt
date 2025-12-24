package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.FragmentStudySetsBinding
import com.philkes.notallyx.presentation.activity.note.EditActivity
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.view.study.StudySetUI
import com.philkes.notallyx.presentation.view.study.StudySetsAdapter
import com.philkes.notallyx.presentation.view.study.StudyState
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

class StudySetsFragment : Fragment() {

    private var binding: FragmentStudySetsBinding? = null
    private var studiedAdapter: StudySetsAdapter? = null
    private var notStudiedAdapter: StudySetsAdapter? = null
    
    private val model: BaseNoteModel by activityViewModels()
    private var quizPrefs: SharedPreferences? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentStudySetsBinding.inflate(inflater, container, false)
        quizPrefs = requireContext().getSharedPreferences("quiz_results", Context.MODE_PRIVATE)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAdapters()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.StudiedSetsRecyclerView?.adapter = null
        binding?.NotStudiedSetsRecyclerView?.adapter = null
        binding = null
        studiedAdapter = null
        notStudiedAdapter = null
    }

    private fun setupAdapters() {
        // Studied adapter
        binding?.StudiedSetsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            studiedAdapter = StudySetsAdapter(
                onItemClick = { studySet ->
                    // Toggle expand/collapse
                    toggleExpand(studySet, true)
                },
                onActionClick = { studySet ->
                    openStudySet(studySet)
                }
            )
            adapter = studiedAdapter
        }

        // Not studied adapter
        binding?.NotStudiedSetsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            notStudiedAdapter = StudySetsAdapter(
                onItemClick = { studySet ->
                    openStudySet(studySet)
                },
                onActionClick = { studySet ->
                    openStudySet(studySet)
                }
            )
            adapter = notStudiedAdapter
        }
    }

    private fun setupObservers() {
        model.baseNotes?.observe(viewLifecycleOwner) { items ->
            lifecycleScope.launch {
                val studySets = processStudySets(items)
                updateUI(studySets)
            }
        }
    }

    private suspend fun processStudySets(items: List<Item>): List<StudySetUI> = withContext(Dispatchers.IO) {
        val checklistNotes = items.filterIsInstance<BaseNote>()
            .filter { it.type == Type.LIST }
        
        checklistNotes.map { note ->
            val noteId = note.id
            // Tính total tất cả từ vựng (kể cả chưa tick)
            val allItems = note.items ?: emptyList()
            val total = allItems.size
            
            val hasStats = hasStatistics(noteId)
            
            // Tính total từ checkedItems cho TẤT CẢ checklist notes (cả học và chưa học)
            // Đảm bảo total được tính nhất quán cho cả hai trường hợp
            if (hasStats) {
                // Đã hoàn thành cả 3 quiz -> hiển thị ở "Đã học"
                val stats = calculateStats(noteId, note)
                StudySetUI(
                    noteId = noteId,
                    title = note.title.ifBlank { requireContext().getString(R.string.empty_list) },
                    state = if (stats.progressPercent >= 100) StudyState.COMPLETED else StudyState.IN_PROGRESS,
                    total = total, // đếm toàn bộ từ
                    mastered = stats.mastered,
                    weak = stats.weak,
                    unlearned = stats.unlearned,
                    progressPercent = stats.progressPercent,
                    lastStudied = getLastStudiedTime(noteId)
                )
            } else {
                // Chưa hoàn thành cả 3 quiz -> hiển thị ở "Chưa học"
                StudySetUI(
                    noteId = noteId,
                    title = note.title.ifBlank { requireContext().getString(R.string.empty_list) },
                    state = StudyState.NOT_STARTED,
                    total = total // tổng từ (kể cả chưa tick)
                )
            }
        }
    }

    /**
     * Kiểm tra xem checklist đã có thống kê chưa (đã làm đủ 3 phần: MCQ, Cloze, Match)
     * Chỉ khi hoàn thành cả 3 quiz thì mới hiển thị ở phần "Đã học"
     */
    private fun hasStatistics(noteId: Long): Boolean {
        val prefs = quizPrefs ?: return false
        val mcqDone = prefs.getBoolean("note_${noteId}_vocab_mcq_completed", false)
        val clozeDone = prefs.getBoolean("note_${noteId}_cloze_completed", false)
        val matchDone = prefs.getBoolean("note_${noteId}_match_pairs_completed", false)
        // Phải hoàn thành cả 3 phần mới có thống kê và hiển thị ở "Đã học"
        val hasAllQuizzes = mcqDone && clozeDone && matchDone
        
        // Nếu đã có phần trăm được lưu từ Total Mastery Score thì cũng coi là có thống kê
        val hasSavedPercent = prefs.getInt("note_${noteId}_overall_percentage", -1) >= 0
        
        return hasAllQuizzes || hasSavedPercent
    }
    

    private fun calculateStats(noteId: Long, note: BaseNote): StatsResult {
        val prefs = quizPrefs ?: return StatsResult(0, 0, 0, 0, 0)

        // Get all items (kể cả chưa tick)
        val allItems = note.items ?: emptyList()
        val total = allItems.size

        if (total == 0) {
            return StatsResult(0, 0, 0, 0, 0)
        }

        // Đếm trạng thái từ WordStatus (NEW / LEARNING / MASTERED) trong SharedPreferences
        val statusPrefs =
            requireContext().getSharedPreferences("word_status_store", android.content.Context.MODE_PRIVATE)

        var mastered = 0
        var weak = 0

        allItems.forEachIndexed { index, item ->
            val baseId = if (item.id != -1) item.id else index
            val key = "note_${noteId}_item_$baseId"
            val rawStatus = statusPrefs.getString(key, null)
            val status =
                rawStatus?.let {
                    runCatching {
                        com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter.WordStatus
                            .valueOf(it)
                    }.getOrNull()
                } ?: com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter.WordStatus.NEW

            when (status) {
                com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter.WordStatus.MASTERED -> mastered++
                com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter.WordStatus.LEARNING -> weak++
                com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter.WordStatus.NEW -> {
                    // new -> sẽ tính ở unlearned
                }
            }
        }

        val unlearned = total - mastered - weak
        
        // Tính phần trăm từ quiz results (giống như trong EditListActivity)
        // Ưu tiên lấy từ SharedPreferences nếu đã được lưu
        // Kiểm tra xem có totalEarned và totalMax đã được lưu không (từ showStatsBottomSheet)
        val savedTotalEarned = prefs.getInt("note_${noteId}_total_earned", -1)
        val savedTotalMax = prefs.getInt("note_${noteId}_total_max", -1)
        val savedPercent = prefs.getInt("note_${noteId}_overall_percentage", -1)
        
        // Ưu tiên lấy phần trăm từ Total Mastery Score đã được lưu
        val progressPercent = when {
            savedPercent >= 0 -> {
                // Sử dụng phần trăm đã được lưu từ thống kê vocabulary (từ Total Mastery Score)
                savedPercent
            }
            savedTotalEarned >= 0 && savedTotalMax > 0 -> {
                // Tính lại từ totalEarned và totalMax đã lưu (từ Total Mastery Score)
                (savedTotalEarned * 100 / savedTotalMax)
            }
            else -> {
                // Tính lại từ quiz results nếu có
                // Chỉ tính từ quiz results, không fallback về mastered/total để tránh hiển thị sai
                val matchPairsProgressJson = prefs.getString("note_${noteId}_match_pairs_vocab_progress", null)
                val mcqAnswersJson = prefs.getString("note_${noteId}_vocab_mcq_answers", null)
                val clozeAnswersJson = prefs.getString("note_${noteId}_cloze_answers", null)
                
                // Nếu không có quiz results nào, trả về 0 thay vì tính từ mastered/total
                if (matchPairsProgressJson.isNullOrBlank() && mcqAnswersJson.isNullOrBlank() && clozeAnswersJson.isNullOrBlank()) {
                    0 // Không có quiz results, không hiển thị phần trăm
                } else {
                    // Tính từ Match Pairs progress (có thể tính được mà không cần cachedResult)
                    var totalEarned = 0
                    var totalMax = 0
                    
                    // Tính từ Match Pairs
                    if (!matchPairsProgressJson.isNullOrBlank()) {
                        try {
                            val vocabStats = mutableMapOf<String, Int>() // vocab -> earned points
                            val vocabMaxPoints = mutableMapOf<String, Int>() // vocab -> max points
                            
                            val jsonArray = JsonParser.parseString(matchPairsProgressJson).asJsonArray
                            jsonArray.forEach { element ->
                                val obj = element.asJsonObject
                                val vocab = obj.get("vocab")?.asString?.lowercase()?.trim() ?: return@forEach
                                val status = obj.get("status")?.asString ?: ""
                                if (vocab.isNotBlank()) {
                                    vocabMaxPoints[vocab] = (vocabMaxPoints[vocab] ?: 0) + 1
                                    if (status == "completed") {
                                        vocabStats[vocab] = (vocabStats[vocab] ?: 0) + 1
                                    }
                                }
                            }
                            
                            totalEarned += vocabStats.values.sum()
                            totalMax += vocabMaxPoints.values.sum()
                        } catch (_: Exception) {
                            // ignore parse errors
                        }
                    }
                    
                    // Nếu có totalMax từ quiz results, tính phần trăm
                    if (totalMax > 0) {
                        (totalEarned * 100 / totalMax)
                    } else {
                        // Không có quiz results hợp lệ, trả về 0
                        0
                    }
                }
            }
        }
        
        return StatsResult(total, mastered, weak, unlearned, progressPercent)
    }

    private fun getLastStudiedTime(noteId: Long): Long? {
        val prefs = quizPrefs ?: return null
        val lastStudied = prefs.getLong("note_${noteId}_last_studied", 0L)
        return if (lastStudied > 0) lastStudied else null
    }

    private fun updateUI(studySets: List<StudySetUI>) {
        // "Đã học" = đã hoàn thành cả 3 quiz (COMPLETED) hoặc đã bắt đầu học (IN_PROGRESS)
        val studied = studySets.filter { it.state == StudyState.COMPLETED || it.state == StudyState.IN_PROGRESS }
        // "Chưa học" = chưa học gì cả (NOT_STARTED)
        val notStudied = studySets.filter { it.state == StudyState.NOT_STARTED }
        
        // Update overview cards
        // Sets: tổng số checklist (cả học và chưa học)
        val totalSets = studySets.size
        // Words: tổng số từ vựng của tất cả checklist (cả học và chưa học)
        val totalWords = studySets.sumOf { it.total }
        // Streak: số ngày học liên tục
        val streak = calculateStreak()
        
        // Update Card 1: Sets (tổng số)
        binding?.CardLearnedContent?.setBackgroundResource(R.drawable.bg_overview_learned)
        binding?.CardLearnedIcon?.setImageResource(R.drawable.notebook)
        binding?.CardLearnedIcon?.setColorFilter(android.graphics.Color.parseColor("#5E5CE6"))
        binding?.CardLearnedValue?.text = "$totalSets Sets"
        binding?.CardLearnedLabel?.text = "Topic"
        
        // Update Card 2: Words
        binding?.CardWordsContent?.setBackgroundResource(R.drawable.bg_overview_words)
        binding?.CardWordsIcon?.setImageResource(R.drawable.text_format)
        binding?.CardWordsIcon?.setColorFilter(android.graphics.Color.parseColor("#FF9800"))
        binding?.CardWordsValue?.text = "$totalWords Words"
        binding?.CardWordsLabel?.text = "Words"
        
        // Update Card 3: Streak
        binding?.CardStreakContent?.setBackgroundResource(R.drawable.bg_overview_streak)
        binding?.CardStreakIcon?.setImageResource(R.drawable.ai_sparkle)
        binding?.CardStreakIcon?.setColorFilter(android.graphics.Color.parseColor("#E53935"))
        binding?.CardStreakValue?.text = "$streak-day"
        binding?.CardStreakLabel?.text = "Streak"
        
        // Update sections
        if (studied.isNotEmpty()) {
            binding?.SectionStudiedHeader?.visibility = View.VISIBLE
            studiedAdapter?.submitList(studied)
        } else {
            binding?.SectionStudiedHeader?.visibility = View.GONE
        }
        
        if (notStudied.isNotEmpty()) {
            binding?.SectionNotStudiedHeader?.visibility = View.VISIBLE
            notStudiedAdapter?.submitList(notStudied)
        } else {
            binding?.SectionNotStudiedHeader?.visibility = View.GONE
        }
        
        // Show empty state if no sets
        if (studySets.isEmpty()) {
            binding?.EmptyState?.visibility = View.VISIBLE
            binding?.OverviewCardsContainer?.visibility = View.GONE
            binding?.SectionStudiedHeader?.visibility = View.GONE
            binding?.SectionNotStudiedHeader?.visibility = View.GONE
        } else {
            binding?.EmptyState?.visibility = View.GONE
            binding?.OverviewCardsContainer?.visibility = View.VISIBLE
        }
    }

    private fun calculateStreak(): Int {
        // Simple streak calculation - can be improved
        val prefs = quizPrefs ?: return 0
        return prefs.getInt("study_streak", 0)
    }

    private fun toggleExpand(studySet: StudySetUI, isStudied: Boolean) {
        val adapter = if (isStudied) studiedAdapter else notStudiedAdapter
        val currentList = adapter?.currentList?.toMutableList() ?: return
        
        val index = currentList.indexOfFirst { it.noteId == studySet.noteId }
        if (index >= 0) {
            val updated = currentList[index].copy(isExpanded = !currentList[index].isExpanded)
            currentList[index] = updated
            adapter.submitList(currentList)
        }
    }

    private fun openStudySet(studySet: StudySetUI) {
        val intent = Intent(requireContext(), EditListActivity::class.java).apply {
            putExtra(EditActivity.EXTRA_SELECTED_BASE_NOTE, studySet.noteId)
        }
        startActivity(intent)
    }

    private data class StatsResult(
        val total: Int,
        val mastered: Int,
        val weak: Int,
        val unlearned: Int,
        val progressPercent: Int
    )
}

