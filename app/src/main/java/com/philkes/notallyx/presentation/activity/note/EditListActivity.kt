package com.philkes.notallyx.presentation.activity.note

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.philkes.notallyx.presentation.view.vocab.VocabStatAdapter
import com.philkes.notallyx.presentation.view.vocab.VocabStatItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.presentation.activity.ai.AISummaryActivity
import com.philkes.notallyx.presentation.activity.ai.TextMcqQuizActivity
import com.philkes.notallyx.data.api.models.VocabQuiz
import com.philkes.notallyx.data.api.models.MCQ
import android.content.Intent
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.action.AddBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.indices
import com.philkes.notallyx.presentation.view.note.listitem.sorting.mapIndexed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.copyToClipBoard
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList
    private lateinit var listManager: ListManager

    // Cache for AI results
    private var cachedVocabResult: SummaryResponse? = null
    private var aiRepository: AIRepository? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady: Boolean = false
    private val vocabCachePrefs by lazy { getSharedPreferences("ai_vocab_cache", MODE_PRIVATE) }
    private val wordStatusPrefs by lazy { getSharedPreferences("word_status_store", MODE_PRIVATE) }

    private val wordStatusStore =
        object : ListItemAdapter.WordStatusStore {
            override fun get(key: String): ListItemAdapter.WordStatus {
                val raw = wordStatusPrefs.getString(key, null)
                return raw?.let {
                    runCatching { ListItemAdapter.WordStatus.valueOf(it) }.getOrNull()
                } ?: ListItemAdapter.WordStatus.NEW
            }

            override fun set(key: String, status: ListItemAdapter.WordStatus) {
                wordStatusPrefs.edit().putString(key, status.name).apply()
            }
        }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initTextToSpeech()
        loadCachedVocabResult()
    }

    override fun onResume() {
        super.onResume()
        // KHÔNG tự động hiển thị table summary
        // Chỉ hiển thị khi user click vào icon ≡ hoặc chọn SUMMARY từ menu
    }

    override fun onDestroy() {
        // Stop and shutdown TTS to prevent memory leaks
        tts?.stop()
        tts?.shutdown()
        tts = null
        
        // Clear adapter reference
        adapter = null
        
        // Clear repository reference
        aiRepository = null
        
        // Clear cached AI results
        cachedVocabResult = null
        
        // Call parent cleanup
        super.onDestroy()
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
        AddBottomSheet(this, colorInt).show(supportFragmentManager, AddBottomSheet.TAG)
    }

    override fun openTextFormattingMenu() {}

    override fun openMoreMenu() {
        val anchor = binding.Toolbar.findViewById<View>(R.id.ivMore) ?: return
        var popup: android.widget.PopupWindow? = null
        val content =
            layoutInflater.inflate(R.layout.popup_more_note, null).apply {
                findViewById<View>(R.id.itemShare).setOnClickListener {
                    share()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemExport).setOnClickListener {
                    showExportDialog()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemChangeColor).setOnClickListener {
                    changeColor()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemReminders).setOnClickListener {
                    changeReminders()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemLabels).setOnClickListener {
                    changeLabels()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemArchive).setOnClickListener {
                    archive()
                    popup?.dismiss()
                }
                findViewById<View>(R.id.itemDelete).setOnClickListener {
                    delete()
                    popup?.dismiss()
                }
            }

        popup =
            android.widget.PopupWindow(
                content,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                elevation = 8f
            }

        val location = IntArray(2)
        binding.Toolbar.getLocationOnScreen(location)
        val toolbarBottom = location[1] + binding.Toolbar.height

        popup.showAtLocation(binding.root, Gravity.TOP or Gravity.END, 0, toolbarBottom)
    }

    /**
     * Data class để lưu thống kê từng từ
     */
    private data class VocabStat(
        val vocab: String,
        var earnedPoints: Int = 0,
        var maxPoints: Int = 0,
    ) {
        val percentage: Int
            get() = if (maxPoints > 0) (earnedPoints * 100 / maxPoints) else 0
    }

    /**
     * Data class cho Match Pairs progress
     */
    private data class MatchPairVocabProgress(
        val vocab: String,
        val status: String, // "pending" | "completed"
    )

    /**
     * Hiển thị thống kê vocabulary quizzes trong bottom sheet
     * Tương tự như showOverallStatistics trong AISummaryActivity
     */
    private fun showStats() {
        val localNoteId = notallyModel.id
        val prefs = getSharedPreferences("quiz_results", MODE_PRIVATE)
        val mcqDone = prefs.getBoolean("note_${localNoteId}_vocab_mcq_completed", false)
        val clozeDone = prefs.getBoolean("note_${localNoteId}_cloze_completed", false)
        val matchDone = prefs.getBoolean("note_${localNoteId}_match_pairs_completed", false)

        if (mcqDone && clozeDone && matchDone) {
            // Cần có cachedResult để hiển thị thống kê
            // Nếu chưa có, cần process AI trước
            if (cachedVocabResult != null) {
                showStatsBottomSheet(cachedVocabResult!!)
            } else {
                // Chưa có cached result, cần process AI trước
                showToast(getString(R.string.ai_processing))
                // Trigger AI processing để có cached result
                val checkedItems = items.toMutableList()
                    .filter { it.checked }
                    .map { it.body.toString().trim() }
                    .filter { it.isNotBlank() }
                val allItemsText = items.toMutableList().joinToString("\n") { item -> item.body.toString() }
                val checkedVocabItems = checkedItems.joinToString("\n")
                val attachmentUris = getAttachedFileUris()
                
                // Process AI và sau đó hiển thị stats
                processVocabAIForStats(checkedVocabItems, attachmentUris, allItemsText)
            }
        } else {
            showToast(getString(R.string.ai_overall_not_ready))
        }
    }

    /**
     * Hiển thị bottom sheet với thống kê vocabulary
     * Tính toán giống như AISummaryActivity.showOverallStatistics()
     */
    private fun showStatsBottomSheet(cachedResult: SummaryResponse) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_vocab_stats, null)

        val localNoteId = notallyModel.id
        val prefs = getSharedPreferences("quiz_results", MODE_PRIVATE)
        val gson = Gson()

        // Data class để lưu thống kê từng từ
        val vocabStats = mutableMapOf<String, VocabStat>()

        // ---- MCQ từ prefs ----
        val mcqs = (cachedResult.vocabMcqs ?: cachedResult.review?.vocabMcqs).orEmpty()
        if (mcqs.isNotEmpty()) {
            val answersJson = prefs.getString("note_${localNoteId}_vocab_mcq_answers", null)
            val answersById: Map<Int, String> =
                try {
                    answersJson?.let {
                        gson.fromJson(
                            it,
                            object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}.type,
                        )
                    } ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

            mcqs.forEach { quiz ->
                val vocab = quiz.vocabTarget?.lowercase()?.trim() ?: return@forEach
                val weight = 1
                val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                stat.maxPoints += weight
                val qid = quiz.id
                if (qid != null && answersById[qid] == quiz.answer) {
                    stat.earnedPoints += weight
                }
            }
        }

        // ---- Cloze từ prefs ----
        val clozeTests = (cachedResult.clozeTests ?: cachedResult.review?.clozeTests).orEmpty()
        if (clozeTests.isNotEmpty()) {
            val answersJson = prefs.getString("note_${localNoteId}_cloze_answers", null)
            val answersById: Map<Int, String> =
                try {
                    answersJson?.let {
                        gson.fromJson(
                            it,
                            object : com.google.gson.reflect.TypeToken<Map<Int, String>>() {}.type,
                        )
                    } ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

            clozeTests.forEach { cloze ->
                cloze.blanks?.forEach { blank ->
                    val bid = blank.id ?: return@forEach
                    val ans = blank.answer ?: return@forEach
                    val vocab = (cloze.vocab?.lowercase()?.trim() ?: ans.lowercase().trim())
                    val weight = 2
                    val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                    stat.maxPoints += weight
                    if (answersById[bid]?.equals(ans, ignoreCase = true) == true) {
                        stat.earnedPoints += weight
                    }
                }
            }
        }

        // ---- Match Pairs từ prefs ----
        val progressJson = prefs.getString("note_${localNoteId}_match_pairs_vocab_progress", null)
        if (!progressJson.isNullOrBlank()) {
            try {
                // MatchPairVocabProgress là private class trong AISummaryActivity, nên parse thủ công
                val jsonArray = JsonParser.parseString(progressJson).asJsonArray
                jsonArray.forEach { element ->
                    val obj = element.asJsonObject
                    val vocab = obj.get("vocab")?.asString?.lowercase()?.trim() ?: return@forEach
                    val status = obj.get("status")?.asString ?: ""
                    if (vocab.isNotBlank()) {
                        val stat = vocabStats.getOrPut(vocab) { VocabStat(vocab) }
                        stat.maxPoints += 1
                        if (status == "completed") {
                            stat.earnedPoints += 1
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore parse errors
            }
        }

        // Tính tổng điểm
        var totalEarned = 0
        var totalMax = 0
        vocabStats.values.forEach { s ->
            totalEarned += s.earnedPoints
            totalMax += s.maxPoints
        }
        val overallPercentage = if (totalMax > 0) (totalEarned * 100 / totalMax) else 0

        // Lưu overallPercentage vào SharedPreferences để sử dụng trong StudySetsFragment
        // Lưu cả totalEarned và totalMax để có thể tính lại nếu cần
        prefs.edit()
            .putInt("note_${localNoteId}_overall_percentage", overallPercentage)
            .putInt("note_${localNoteId}_total_earned", totalEarned)
            .putInt("note_${localNoteId}_total_max", totalMax)
            .apply()

        // Hiển thị tổng mastery score
        val totalMasteryText = "$totalEarned / $totalMax ($overallPercentage%)"
        val totalMasteryView = sheetView.findViewById<TextView>(R.id.TotalMasteryScore)
        totalMasteryView.text = totalMasteryText

        // Tính số từ đã hoàn thành (100%)
        val completedCount = vocabStats.values.count { it.percentage >= 100 }
        val totalCount = vocabStats.size
        val completedCountView = sheetView.findViewById<TextView>(R.id.CompletedCount)
        completedCountView.text = "$completedCount/$totalCount completed"

        // Hiển thị thống kê từng từ với RecyclerView
        val statsRecyclerView = sheetView.findViewById<RecyclerView>(R.id.VocabStatsRecyclerView)
        statsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        val vocabStatItems = vocabStats.values
            .sortedByDescending { it.percentage }
            .map { stat ->
                VocabStatItem(
                    vocab = stat.vocab,
                    earnedPoints = stat.earnedPoints,
                    maxPoints = stat.maxPoints,
                    percentage = stat.percentage
                )
            }
        
        val adapter = VocabStatAdapter()
        statsRecyclerView.adapter = adapter
        adapter.submitList(vocabStatItems)

        dialog.setContentView(sheetView)
        dialog.show()
    }

    /**
     * Process AI và tự động hiển thị stats sau khi có kết quả
     */
    private fun processVocabAIForStats(
        checkedVocabItems: String,
        attachmentUris: List<android.net.Uri>,
        allItemsText: String,
    ) {
        if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        val userId = getAiUserId()
        val localNoteId = notallyModel.id
        val currentHash = computeVocabContentHash(checkedVocabItems, attachmentUris, allItemsText)
        val noteIdToUse = ensureBackendNoteIdForVocab(localNoteId, currentHash)

        // Show loading dialog
        val loadingDialog = android.app.ProgressDialog(this).apply {
            setMessage(getString(R.string.ai_processing))
            setCancelable(false)
            show()
        }

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    aiRepository!!.processCombinedInputs(
                        noteText = checkedVocabItems.ifBlank { null },
                        attachments = attachmentUris,
                        userId = userId,
                        noteId = noteIdToUse,
                        contentType = "checklist",
                        checkedVocabItems = checkedVocabItems,
                        useCache = true,
                    )
                }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data

                        // Save content hash và backend_note_id
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(this@EditListActivity, localNoteId, "vocab", currentHash)
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setBackendNoteId(this@EditListActivity, localNoteId, noteIdToUse)
                        }

                        // Tự động hiển thị stats bottom sheet sau khi có kết quả
                        showStatsBottomSheet(result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // Should not happen in sync call
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    override fun openAIActionsMenu() {
        // GỌI API TRƯỚC để xử lý các từ được TICK, sau đó mới hiển thị menu
        val allItems = items.toMutableList()
        
        // CHỈ lấy các từ được TICK
        val checkedItems = allItems
            .filter { it.checked }
            .map { it.body.toString().trim() }
            .filter { it.isNotBlank() }
        
        val allItemsText = allItems.joinToString("\n") { item -> item.body.toString() }
        val checkedVocabItems = checkedItems.joinToString("\n")
        val attachmentUris = getAttachedFileUris()

        if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        val userId = getAiUserId()
        val localNoteId = notallyModel.id
        val currentHash = computeVocabContentHash(checkedVocabItems, attachmentUris, allItemsText)
        val noteIdToUse = ensureBackendNoteIdForVocab(localNoteId, currentHash)

        // Kiểm tra cache trước
        if (cachedVocabResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash = com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                this,
                localNoteId,
                "vocab",
            )
            if (currentHash == storedHash) {
                // Đã có cache, hiển thị menu ngay
                showAIOptionsMenu(cachedVocabResult!!)
                return
            }
        }

        // Chưa có cache, gọi API trước
        val loadingDialog = android.app.ProgressDialog(this).apply {
            setMessage(getString(R.string.ai_processing))
            setCancelable(false)
            show()
        }

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    aiRepository!!.processCombinedInputs(
                        noteText = checkedVocabItems.ifBlank { null },
                        attachments = attachmentUris,
                        userId = userId,
                        noteId = noteIdToUse,
                        contentType = "checklist",
                        checkedVocabItems = checkedVocabItems,
                        useCache = true,
                    )
                }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data
                        saveVocabCache(notallyModel.id, result.data)
                        
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(this@EditListActivity, localNoteId, "vocab", currentHash)
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setBackendNoteId(this@EditListActivity, localNoteId, noteIdToUse)
                        }

                        // Chỉ hiển thị menu, KHÔNG tự động mở table summary
                        showAIOptionsMenu(result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // Should not happen
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Hiển thị menu AI options sau khi đã có cached result
     */
    private fun showAIOptionsMenu(cachedResult: SummaryResponse) {
        val toolbar = binding.Toolbar
        val ivAI = toolbar.findViewById<View>(R.id.ivAI)
        
        if (ivAI != null && ivAI.visibility == View.VISIBLE) {
            try {
                val options = com.philkes.notallyx.presentation.view.note.ai.AIOption.getDefaultForVocab()
                com.philkes.notallyx.presentation.view.note.ai.AIToolBarMenuPopupView.show(
                    context = this,
                    anchor = ivAI,
                    options = options,
                    listener = object : com.philkes.notallyx.presentation.view.note.ai.AIToolBarMenuPopupView.OnItemClickListener {
                        override fun onClick(option: com.philkes.notallyx.presentation.view.note.ai.AIOption) {
                            // Dùng cache đã có, không cần gọi API lại
                            showAIFeatureFromCache(option, cachedResult)
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("EditListActivity", "Error showing AI popup", e)
                showToast("Error showing menu")
            }
        }
    }

    /**
     * Hiển thị chức năng AI từ cache (không gọi API)
     */
    private fun showAIFeatureFromCache(
        option: com.philkes.notallyx.presentation.view.note.ai.AIOption,
        cachedResult: SummaryResponse
    ) {
        when (option.type) {
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.SUMMARY -> {
                showInlineVocabSummary(cachedResult)
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.KEY -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.VOCAB_STORY,
                    isVocabMode = true,
                )
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.QUESTION -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.VOCAB_FLASHCARDS,
                    isVocabMode = true,
                )
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.MCQ -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.VOCAB_MCQ,
                    isVocabMode = true,
                )
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.CLOZE -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.VOCAB_CLOZE,
                    isVocabMode = true,
                )
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.MATCH -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.VOCAB_MATCH,
                    isVocabMode = true,
                )
            }
        }
    }
    
    /**
     * Invalidate cache when checklist items change (checked/unchecked, added/deleted) This ensures
     * fresh AI results when content changes
     */
    private fun invalidateVocabCache() {
        cachedVocabResult = null
        adapter?.clearInlineSummary()
    }

    private fun processVocabAI(
        checkedVocabItems: String,
        attachmentUris: List<android.net.Uri>,
        allItemsText: String,
    ) {
        if (checkedVocabItems.isBlank() && attachmentUris.isEmpty()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        val userId = getAiUserId()
        val localNoteId = notallyModel.id

        // Check if content has changed by comparing hash
        // This ensures we don't use old backend_note_id for new content
        // Logic similar to AISummaryActivity.extractIntentData() and summarizeNote()
        val currentHash = computeVocabContentHash(checkedVocabItems, attachmentUris, allItemsText)
        val noteIdToUse = ensureBackendNoteIdForVocab(localNoteId, currentHash)

        // N?u ?� c� cached result v� hash kh?p, d�ng l?i lu�n kh�ng c?n g?i API
        if (cachedVocabResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    "vocab",
                )
            if (currentHash == storedHash) {
                android.util.Log.d(
                    "EditListActivity",
                    "processVocabAI: Using cached result, hash matches. noteId=$noteIdToUse",
                )
                showVocabActionsBottomSheet(checkedVocabItems, attachmentUris, cachedVocabResult!!)
                return
            }
        }

        // Show loading dialog
        val loadingDialog =
            android.app.ProgressDialog(this).apply {
                setMessage(getString(R.string.ai_processing))
                setCancelable(false)
                show()
            }

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                // Lu�n d�ng /process/combined ?? ??ng b? v?i note Text v� h? tr? file trong t??ng
                // lai
                val result =
                    withContext(Dispatchers.IO) {
                        aiRepository!!.processCombinedInputs(
                            noteText = checkedVocabItems.ifBlank { null },
                            attachments = attachmentUris,
                            userId = userId,
                            noteId = noteIdToUse,
                            contentType = "checklist",
                            checkedVocabItems = checkedVocabItems,
                            useCache = true, // Backend will check cache based on content hash
                        )
                    }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        cachedVocabResult = result.data

                        // Save content hash v� backend_note_id n?u c� local noteId
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(
                                    this@EditListActivity,
                                    localNoteId,
                                    "vocab",
                                    currentHash,
                                )

                            val existingBackendNoteId =
                                com.philkes.notallyx.data.preferences.AIUserPreferences
                                    .getBackendNoteId(this@EditListActivity, localNoteId)
                            if (
                                existingBackendNoteId == null ||
                                    existingBackendNoteId != noteIdToUse
                            ) {
                                com.philkes.notallyx.data.preferences.AIUserPreferences
                                    .setBackendNoteId(
                                        this@EditListActivity,
                                        localNoteId,
                                        noteIdToUse,
                                    )
                                android.util.Log.d(
                                    "EditListActivity",
                                    "processVocabAI: Saved backend_note_id=$noteIdToUse for localNoteId=$localNoteId",
                                )
                            }
                        }

                        showVocabActionsBottomSheet(checkedVocabItems, attachmentUris, result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // Should not happen in sync call
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun showVocabActionsBottomSheet(
        checkedVocabItems: String,
        attachmentUris: List<android.net.Uri>,
        cachedResult: SummaryResponse,
    ) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_vocab_actions, null)

        // Vocab Story
        sheetView.findViewById<View>(R.id.ActionVocabStory).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_STORY,
                isVocabMode = true,
            )
        }

        // Vocab MCQ - Start quiz trực tiếp, bỏ qua chọn difficulty
        sheetView.findViewById<View>(R.id.ActionVocabMCQ).setOnClickListener {
            dialog.dismiss()
            startChecklistMcqFlow(cachedResult)
        }

        // Flashcards
        sheetView.findViewById<View>(R.id.ActionFlashcards).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_FLASHCARDS,
                isVocabMode = true,
            )
        }

        // Cloze
        sheetView.findViewById<View>(R.id.ActionCloze).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_CLOZE,
                isVocabMode = true,
            )
        }

        // Match pairs
        sheetView.findViewById<View>(R.id.ActionMatchPairs).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.VOCAB_MATCH,
                isVocabMode = true,
            )
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showInlineVocabSummary(cachedResult: SummaryResponse) {
        val rows = cachedResult.summaryTable ?: cachedResult.review?.summaryTable
        if (rows.isNullOrEmpty()) {
            showToast(getString(R.string.ai_error_generic))
            return
        }

        val existingStates = adapter?.getInlineSummaries().orEmpty()
        val list = items.toMutableList()
        val newStates = mutableMapOf<Int, ListItemAdapter.InlineSummaryState>()

        rows.forEach { row ->
            val word = row.word?.trim()
            if (!word.isNullOrBlank()) {
                val pos = findItemPositionByWord(word)
                if (pos != -1) {
                    val keepExpanded =
                        existingStates[pos]?.let { it.word.equals(word, true) && it.expanded } ?: true
                    newStates[pos] =
                        ListItemAdapter.InlineSummaryState(
                            word = word,
                            row = row,
                            expanded = keepExpanded,
                        )
                }
            }
        }

        if (newStates.isEmpty()) {
            showToast(getString(R.string.ai_error_empty_note))
            return
        }

        adapter?.setInlineSummaries(newStates)

        // Scroll đến mục đầu tiên có summary
        val firstPos = newStates.keys.minOrNull()
        if (firstPos != null && firstPos != RecyclerView.NO_POSITION) {
            binding.RecyclerView.smoothScrollToPosition(firstPos)
        }
    }

    private fun saveVocabCache(noteId: Long, data: SummaryResponse) {
        if (noteId == -1L) return
        runCatching {
            val json = Gson().toJson(data)
            vocabCachePrefs.edit().putString("note_${noteId}_vocab_summary", json).apply()
        }
    }

    private fun loadCachedVocabResult() {
        val noteId = notallyModel.id
        if (noteId == -1L) return
        val json = vocabCachePrefs.getString("note_${noteId}_vocab_summary", null) ?: return
        runCatching {
            val type = object : TypeToken<SummaryResponse>() {}.type
            val cached = Gson().fromJson<SummaryResponse>(json, type)
            cachedVocabResult = cached
        }
    }

    private fun initTextToSpeech() {
        tts =
            android.speech.tts.TextToSpeech(this) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    ttsReady =
                        result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                            result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) {
                        showToast(getString(R.string.ai_tts_not_ready))
                    }
            } else {
                    ttsReady = false
                    showToast(getString(R.string.ai_tts_not_ready))
                }
            }
    }

    private fun onSpeakWord(word: String) {
        if (word.isBlank()) return
        if (!ttsReady) {
            showToast(getString(R.string.ai_tts_not_ready))
            return
        }
        try {
            tts?.stop()
            tts?.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts-$word")
        } catch (e: Exception) {
            showToast("TTS error: ${e.message ?: "unknown"}")
        }
    }

    private fun onInlineVocabClick(word: String) {
        if (word.isBlank()) {
            showToast(R.string.ai_error_empty_note)
            return
        }

        // Kiểm tra cache trước - nếu có thì hiển thị ngay, không cần gọi API
        cachedVocabResult?.let { cached ->
            val rows = cached.summaryTable ?: cached.review?.summaryTable
            val hasWord = rows?.any { it.word.equals(word, true) } == true
            if (hasWord) {
                showInlineSummaryForWord(word, cached)
                return
            }
        }

        // Chưa có trong cache - có thể là từ mới thêm sau khi đã chạy AI
        // Gọi API để xử lý từ này (chỉ từ này thôi, không xử lý lại toàn bộ)
        val loadingDialog =
            android.app.ProgressDialog(this).apply {
                setMessage(getString(R.string.ai_processing))
                setCancelable(false)
                show()
            }

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        aiRepository!!.processCombinedInputs(
                            noteText = word,
                            attachments = emptyList(), // Chỉ xử lý từ này, không cần attachments
                            userId = getAiUserId(),
                            noteId = ensureBackendNoteIdForVocab(notallyModel.id, null),
                            contentType = "checklist",
                            checkedVocabItems = word,
                            useCache = false,  // KHÔNG dùng cache vì đây là từ mới
                        )
                    }

                loadingDialog.dismiss()

                when (result) {
                    is AIResult.Success -> {
                        // Merge kết quả mới vào cache hiện tại
                        mergeVocabResultIntoCache(result.data)
                        showInlineSummaryForWord(word, result.data)
                    }
                    is AIResult.Error -> showToast(result.message ?: getString(R.string.ai_error_generic))
                    else -> {}
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Merge kết quả AI của 1 từ vào cache hiện tại
     * Để khi click vào từ khác, vẫn có đầy đủ kết quả
     */
    private fun mergeVocabResultIntoCache(newResult: SummaryResponse) {
        if (cachedVocabResult == null) {
            cachedVocabResult = newResult
            saveVocabCache(notallyModel.id, newResult)
            return
        }

        // Merge summary table
        val existingRows = (cachedVocabResult!!.summaryTable ?: cachedVocabResult!!.review?.summaryTable).orEmpty().toMutableList()
        val newRows = (newResult.summaryTable ?: newResult.review?.summaryTable).orEmpty()
        
        newRows.forEach { newRow ->
            val existingIndex = existingRows.indexOfFirst { it.word.equals(newRow.word, true) }
            if (existingIndex >= 0) {
                existingRows[existingIndex] = newRow // Update existing
            } else {
                existingRows.add(newRow) // Add new
            }
        }

        // Update cache
        cachedVocabResult = cachedVocabResult!!.copy(
            summaryTable = existingRows
        )
        saveVocabCache(notallyModel.id, cachedVocabResult!!)
    }

    private fun showInlineSummaryForWord(word: String, response: SummaryResponse) {
        val rows = response.summaryTable ?: response.review?.summaryTable
        if (rows.isNullOrEmpty()) {
            showToast(getString(R.string.ai_error_generic))
            return
        }
        val matchedRow =
            rows.firstOrNull { it.word?.equals(word, true) == true }
                ?: rows.firstOrNull()
        if (matchedRow == null) {
            showToast(getString(R.string.ai_error_empty_note))
            return
        }

        val pos = findItemPositionByWord(matchedRow.word ?: word)
        if (pos == -1) {
            showToast(getString(R.string.ai_error_empty_note))
            return
        }

        val state =
            ListItemAdapter.InlineSummaryState(
                word = matchedRow.word ?: word,
                row = matchedRow,
                expanded = true,
            )
        adapter?.setInlineSummaries(mapOf(pos to state))
        binding.RecyclerView.smoothScrollToPosition(pos)
    }

    private fun resolveSelectedVocabItem(
        rows: List<com.philkes.notallyx.data.api.models.VocabSummaryRow>,
    ): Pair<String, Int>? {
        // Không còn dùng, giữ để tránh lỗi reference nếu nơi khác gọi
        return null
    }

    private fun findItemPositionByWord(word: String): Int {
        if (word.isBlank()) return -1
        val normalized = word.trim().lowercase()
        val list = items.toMutableList()
        return list.indexOfFirst { it.body.toString().trim().lowercase() == normalized }
    }

    private fun launchAIVocabSummary(
        checkedVocabItems: String,
        section: AISummaryActivity.AISection,
    ) {
        AISummaryActivity.start(
            this,
            checkedVocabItems,
            notallyModel.id,
            section,
            useProcessEndpointForText = true,
            contentType = "checklist",
            checkedVocabItems = checkedVocabItems,
        )
    }

    private fun getAttachedFileUris(): List<android.net.Uri> {
        val uris = mutableListOf<android.net.Uri>()

        // Files
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

        // Images
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

        // Audios
        val audioRoot = notallyModel.audioRoot
        val audioAttachments = notallyModel.audios.value ?: emptyList()
        if (audioRoot != null && audioAttachments.isNotEmpty()) {
            audioAttachments.forEach { audio ->
                val file = java.io.File(audioRoot, audio.name)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        return uris.distinct()
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

        // Hiển thị icon thống kê cho checklist với màu sắc
        val statsIcon = findViewById<ImageView>(R.id.StatsIcon)
        statsIcon?.apply {
            visibility = View.VISIBLE
            // Thêm màu cho icon (màu primary của app)
            val primaryColor = com.google.android.material.color.MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(this@EditListActivity, android.R.color.holo_blue_dark)
            )
            setColorFilter(
                primaryColor,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            setOnClickListener {
                showStats()
            }
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.AddItem.setOnClickListener {
            listManager.add()
            // Invalidate cache when item is added
            invalidateVocabCache()
        }

        // Invalidate cache when items change (checked/unchecked, deleted, etc.)
        // This is handled by ListManager callbacks, but we can also add explicit invalidation
        // when needed. For now, cache will be checked against backend content_hash.
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
                ::onInlineVocabClick,
                ::onSpeakWord,
                notallyModel.id,
                wordStatusStore,
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
        // Nếu đã có cache summary, hiển thị ngay khi vào màn
        binding.RecyclerView.post {
            cachedVocabResult?.let { showInlineVocabSummary(it) }
        }
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

    /**
     * Compute hash for vocab content to detect content changes Similar to
     * AISummaryActivity.computeContentHash() but for vocab mode
     */
    private fun computeVocabContentHash(
        checkedContent: String,
        attachmentUris: List<android.net.Uri>,
        allItemsText: String,
    ): String? {
        val trimmedChecked = checkedContent.trim()
        val hasAttachments = attachmentUris.isNotEmpty()
        val trimmedAllItems = allItemsText.trim()

        if (trimmedChecked.isBlank() && !hasAttachments && trimmedAllItems.isBlank()) return null

        // Include checked items, all items (unchecked), and attachment metadata to detect changes
        val attachmentsMeta =
            if (hasAttachments) {
                attachmentUris.joinToString("|") { uri -> uri.toString() }
            } else {
                ""
            }

        val payload = buildString {
            append("vocab::")
            append(trimmedChecked)
            if (trimmedAllItems.isNotBlank()) {
                append("::all_items::")
                append(trimmedAllItems)
            }
            if (attachmentsMeta.isNotBlank()) {
                append("::attachments::")
                append(attachmentsMeta)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "EditListActivity"
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }

    /**
     * Đảm bảo dùng cùng backend_note_id cho checklist nếu nội dung không đổi.
     * - Nếu hash khớp và có backend_note_id đã lưu → dùng lại.
     * - Nếu chưa có hoặc hash khác → sinh UUID mới, lưu mapping và clear hash cũ.
     */
    private fun ensureBackendNoteIdForVocab(localNoteId: Long, currentHash: String?): String {
        if (localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    "vocab",
                )
            val storedBackend =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                    this,
                    localNoteId,
                )

            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: localNoteId=$localNoteId, currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}..., storedBackend=$storedBackend",
            )

            if (currentHash == storedHash && storedBackend != null) {
                android.util.Log.d(
                    "EditListActivity",
                    "ensureBackendNoteIdForVocab: Reusing existing backend_note_id=$storedBackend",
                )
                return storedBackend
            }

            // Hash ??i ho?c ch?a c� mapping: clear c?, sinh m?i
            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: Hash mismatch or no mapping, generating new UUID. currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}...",
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.removeBackendNoteId(
                this,
                localNoteId,
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.clearNoteContentHash(
                this,
                localNoteId,
                "vocab",
            )
            val generated = UUID.randomUUID().toString()
            com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                this,
                localNoteId,
                generated,
            )
            com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                this,
                localNoteId,
                "vocab",
                currentHash,
            )
            android.util.Log.d(
                "EditListActivity",
                "ensureBackendNoteIdForVocab: Generated and saved new backend_note_id=$generated",
            )
            return generated
        }

        // localNoteId = -1 ho?c kh�ng c� hash ? sinh UUID nh?ng kh�ng l?u mapping
        android.util.Log.d(
            "EditListActivity",
            "ensureBackendNoteIdForVocab: localNoteId=$localNoteId or no hash, generating temporary UUID",
        )
        return UUID.randomUUID().toString()
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_format, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Ensure dialog can display full content
        dialog.window?.let { window ->
            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val formatGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.FormatGrid)
        val buttonCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.ButtonCancel)
        val buttonExport = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.ButtonExport)

        // Map format to icon drawable
        val formatIconMap = mapOf(
            ExportMimeType.TXT to R.drawable.txt,
            ExportMimeType.PDF to R.drawable.pdf,
            ExportMimeType.JSON to R.drawable.json,
            ExportMimeType.HTML to R.drawable.html,
            ExportMimeType.JPEG to R.drawable.jpeg,
            ExportMimeType.TIFF to R.drawable.tiff
        )

        var selectedFormat: ExportMimeType? = null

        // Create format items
        ExportMimeType.entries.forEach { format ->
            val itemView = layoutInflater.inflate(R.layout.item_export_format, formatGrid, false)
            val formatIcon = itemView.findViewById<android.widget.ImageView>(R.id.FormatIcon)
            val formatText = itemView.findViewById<android.widget.TextView>(R.id.FormatText)
            val formatCard = itemView as com.google.android.material.card.MaterialCardView

            formatIcon.setImageResource(formatIconMap[format] ?: R.drawable.note)
            
            // For TXT, HTML, JSON: icon is 32x32 inside larger canvas (76x62) with transparent background
            // ImageView is wrapped in 32dp FrameLayout, ImageView itself is 76dp x 62dp
            // Using centerCrop will crop to show only the center portion (the icon)
            when (format) {
                ExportMimeType.TXT, ExportMimeType.HTML, ExportMimeType.JSON -> {
                    // ImageView is already 76dp x 62dp in layout, centerCrop will show center (icon)
                    formatIcon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }
                else -> {
                    // PDF, JPEG, TIFF: icons are already correct size, resize ImageView to 32dp
                    formatIcon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    val density = resources.displayMetrics.density
                    formatIcon.layoutParams.width = (32 * density).toInt()
                    formatIcon.layoutParams.height = (32 * density).toInt()
                }
            }
            formatIcon.adjustViewBounds = false
            formatText.text = format.name

            // Set initial selection (first item)
            if (format == ExportMimeType.entries.first()) {
                selectedFormat = format
                formatCard.strokeColor = android.graphics.Color.parseColor("#FF9800")
                formatCard.strokeWidth = 2
            } else {
                formatCard.strokeColor = android.graphics.Color.TRANSPARENT
                formatCard.strokeWidth = 0
            }

            itemView.setOnClickListener {
                // Reset all cards
                for (i in 0 until formatGrid.childCount) {
                    val child = formatGrid.getChildAt(i) as? com.google.android.material.card.MaterialCardView
                    child?.strokeColor = android.graphics.Color.TRANSPARENT
                    child?.strokeWidth = 0
                }
                // Select clicked card
                formatCard.strokeColor = android.graphics.Color.parseColor("#FF9800")
                formatCard.strokeWidth = 2
                selectedFormat = format
            }

            // Calculate row and column based on index
            val index = ExportMimeType.entries.indexOf(format)
            val row = index / 3
            val col = index % 3
            
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(6, 6, 6, 6)
            }
            // Use FILL to ensure proper sizing
            params.columnSpec = android.widget.GridLayout.spec(
                col,
                android.widget.GridLayout.FILL,
                1f
            )
            params.rowSpec = android.widget.GridLayout.spec(
                row,
                android.widget.GridLayout.FILL,
                1f
            )
            formatGrid.addView(itemView, params)
        }

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonExport.setOnClickListener {
            selectedFormat?.let { format ->
                export(format)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Start checklist MCQ quiz flow - bỏ qua chọn difficulty, vào quiz luôn
     */
    private fun startChecklistMcqFlow(cachedResult: SummaryResponse) {
        val vocabMcqs = (cachedResult.vocabMcqs ?: cachedResult.review?.vocabMcqs).orEmpty()
        if (vocabMcqs.isEmpty()) {
            showToast(getString(R.string.ai_error_generic))
            return
        }

        // Convert VocabQuiz sang MCQ format
        val mcqList = vocabMcqs.map { vocabQuiz ->
            MCQ(
                question = vocabQuiz.question ?: "",
                options = vocabQuiz.options ?: emptyMap(),
                answer = vocabQuiz.answer ?: ""
            )
        }

        if (mcqList.isEmpty()) {
            showToast("No questions available")
            return
        }

        // Start quiz activity trực tiếp, không cần chọn difficulty
        val json = Gson().toJson(mcqList)
        val intent = Intent(this, TextMcqQuizActivity::class.java).apply {
            putExtra(TextMcqQuizActivity.EXTRA_MCQS_JSON, json)
            putExtra(TextMcqQuizActivity.EXTRA_DIFFICULTY, "medium") // Default difficulty
        }
        startActivity(intent)
    }
}
