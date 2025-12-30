package com.philkes.notallyx.presentation.activity.note

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.AIResult
import com.philkes.notallyx.data.api.models.MCQs
import com.philkes.notallyx.data.api.models.SummaryResponse
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.data.model.getNoteIdFromUrl
import com.philkes.notallyx.data.model.getNoteTypeFromUrl
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.data.preferences.getAiUserId
import com.philkes.notallyx.data.repository.AIRepository
import com.philkes.notallyx.databinding.BottomSheetTextFormatBinding
import com.philkes.notallyx.databinding.BottomTextFormattingMenuBinding
import com.philkes.notallyx.databinding.RecyclerToggleBinding
import com.philkes.notallyx.presentation.activity.ai.AISummaryActivity
import com.philkes.notallyx.presentation.view.ai.AiSlideCompareView
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_EXCLUDE_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TITLE
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TYPE
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.hideKeyboard
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.TextFormattingAdapter
import com.philkes.notallyx.presentation.view.note.TextFormattingFormatAdapter
import com.philkes.notallyx.presentation.view.note.TextFormattingListsAdapter
import com.philkes.notallyx.presentation.view.note.TextFormattingStyleAdapter
import com.philkes.notallyx.presentation.view.note.FormatToolbarAdapter
import com.philkes.notallyx.databinding.FormatToolbarBinding
import com.philkes.notallyx.databinding.BottomSheetFormatNewBinding
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory
import com.philkes.notallyx.presentation.view.note.action.AddNoteActions
import com.philkes.notallyx.presentation.view.note.action.AddNoteBottomSheet
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.utils.LinkMovementMethod
import com.philkes.notallyx.utils.copyToClipBoard
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch

class EditNoteActivity : EditActivity(Type.NOTE), AddNoteActions {

    private lateinit var selectedSpan: URLSpan
    private lateinit var pickNoteNewActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickNoteUpdateActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var textFormatMenu: View
    private var textFormattingAdapter: TextFormattingAdapter? = null
    private var textFormatSheet: BottomSheetDialog? = null
    private var formatToolbarBinding: FormatToolbarBinding? = null
    private var formatToolbarAdapter: FormatToolbarAdapter? = null
    private var newFormatSheet: BottomSheetDialog? = null
    private var cachedTextResult: SummaryResponse? = null
    private var inlineSummaryOriginalText: String? = null
    private var inlineSummaryCurrentText: String? = null
    private var inlineSummaryVisible: Boolean = false
    private var aiRepository: AIRepository? = null
    private var searchResultIndices: List<Pair<Int, Int>>? = null

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        setupEditor()

        if (notallyModel.isNewNote) {
            binding.EnterBody.requestFocus()
        }

        // Ẩn icon thống kê cho note thường (chỉ hiển thị cho checklist)
        findViewById<View>(R.id.StatsIcon)?.visibility = View.GONE
    }

    private fun setupInlineSummaryToolbar() {
        val toolbar = findViewById<View>(R.id.InlineSummaryToolbarContainer) ?: return
        val btnReplace =
            toolbar.findViewById<com.google.android.material.button.MaterialButton>(R.id.BtnReplace)
        val btnCancel =
            toolbar.findViewById<com.google.android.material.button.MaterialButton>(R.id.BtnCancel)

        btnCancel?.setOnClickListener { restoreOriginalFromInlineSummary() } // Back
        btnReplace?.setOnClickListener { applyInlineSummaryReplace() } // Done
    }

    private fun showInlineSummaryPreview(
        summaryResponse: SummaryResponse,
        preferBulletPoints: Boolean = false,
    ) {
        val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView) ?: return

        // Lấy text gốc từ EnterBody
        val originalText = binding.EnterBody.text?.toString().orEmpty()
        
        // Ưu tiên các trường summary khác nhau
        val bulletPointsText = summaryResponse.summaries?.bulletPoints
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "• $it" }

        val summaryText =
            if (preferBulletPoints) {
                bulletPointsText
                    ?: summaryResponse.summary
                    ?: summaryResponse.summaries?.shortParagraph
                    ?: summaryResponse.summaries?.oneSentence
                    ?: summaryResponse.processedText
                    ?: summaryResponse.rawText
            } else {
                summaryResponse.summary
                    ?: summaryResponse.summaries?.shortParagraph
                    ?: summaryResponse.summaries?.oneSentence
                    ?: bulletPointsText
                    ?: summaryResponse.processedText
                    ?: summaryResponse.rawText
            }

        if (originalText.isBlank() || summaryText.isNullOrBlank()) {
            showToast(R.string.ai_error_generic)
            return
        }

        inlineSummaryOriginalText = originalText
        inlineSummaryCurrentText = summaryText
        inlineSummaryVisible = true

        // Hiển thị slide-to-compare view thay vì replace text
        slideView.setTexts(originalText, summaryText)
        slideView.visibility = View.VISIBLE
        
        // Reset về trạng thái ban đầu: text gốc xích sang trái, chừa handle bên phải
        slideView.reset(animated = false)
        
        // Ẩn EnterBody khi đang ở chế độ slide-to-compare
        binding.EnterBody.visibility = View.GONE
        
        // Bỏ paddingBottom của ContentLayout để summary sát rìa dưới
        binding.ContentLayout.setPadding(
            binding.ContentLayout.paddingLeft,
            binding.ContentLayout.paddingTop,
            binding.ContentLayout.paddingRight,
            0
        )
        
        // Lưu trạng thái "đang xem" (chưa áp dụng) vào SharedPreferences
        val prefs = getSharedPreferences("ai_summary_state", android.content.Context.MODE_PRIVATE)
        val noteId = notallyModel.id
        prefs.edit()
            .putString("summary_text_$noteId", summaryText)
            .putString("original_text_$noteId", originalText)
            .putBoolean("is_using_summary_$noteId", false) // Mặc định chọn gốc
            .putBoolean("has_applied_$noteId", false) // Đánh dấu CHƯA áp dụng
            .apply()
        
        // Hint chỉ 1 lần
        val prefs2 = getSharedPreferences("ai_hints", android.content.Context.MODE_PRIVATE)
        if (!prefs2.getBoolean("has_shown_slide_hint", false)) {
            slideView.showHintOnce(prefs2)
            prefs2.edit().putBoolean("has_shown_slide_hint", true).apply()
        }
        
        // Setup callback cho nút Áp dụng
        setupApplyButtonCallback(slideView, summaryText, originalText)
    }
    
    private fun setupApplyButtonCallback(
        slideView: com.philkes.notallyx.presentation.view.ai.AiSlideCompareView,
        summaryText: String,
        originalText: String
    ) {
        slideView.onApplyClicked = { selectedText, isShowingSummary ->
            // Save state to SharedPreferences
            val prefs = getSharedPreferences("ai_summary_state", android.content.Context.MODE_PRIVATE)
            val noteId = notallyModel.id
            
            // Mark as APPLIED - this is the key difference
            prefs.edit()
                .putString("summary_text_$noteId", summaryText)
                .putString("original_text_$noteId", originalText)
                .putBoolean("is_using_summary_$noteId", isShowingSummary)
                .putBoolean("has_applied_$noteId", true) // ⭐ Mark as applied
                .apply()
            
            // Apply selected text to EnterBody AND update model
            binding.EnterBody.setText(selectedText)
            notallyModel.body = android.text.Editable.Factory.getInstance().newEditable(selectedText) // ⭐ Update model
            binding.EnterBody.visibility = View.VISIBLE
            
            // Hide slide view
            slideView.visibility = View.GONE
            
            // Restore paddingBottom
            val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
            binding.ContentLayout.setPadding(
                binding.ContentLayout.paddingLeft,
                binding.ContentLayout.paddingTop,
                binding.ContentLayout.paddingRight,
                paddingBottomPx
            )
            
            // Reset state
            inlineSummaryVisible = false
            inlineSummaryOriginalText = null
            inlineSummaryCurrentText = null
            
            // Save note immediately
            lifecycleScope.launch {
                saveNote()
            }
            
            // Show toast
            val message = if (isShowingSummary) {
                "Applied summary version"
            } else {
                "Kept original version"
            }
            android.widget.Toast.makeText(this@EditNoteActivity, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreOriginalFromInlineSummary() {
        val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView)
        if (inlineSummaryVisible && inlineSummaryOriginalText != null) {
            // Khôi phục text gốc và ẩn slide view
            binding.EnterBody.setText(inlineSummaryOriginalText)
            binding.EnterBody.visibility = View.VISIBLE
            slideView?.visibility = View.GONE
            // Reset slide view về trạng thái ban đầu
            slideView?.reset(animated = false)
            
            // Khôi phục paddingBottom của ContentLayout
            val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
            binding.ContentLayout.setPadding(
                binding.ContentLayout.paddingLeft,
                binding.ContentLayout.paddingTop,
                binding.ContentLayout.paddingRight,
                paddingBottomPx
            )
            
            // Xóa trạng thái summary đã lưu
            val prefs = getSharedPreferences("ai_summary_state", android.content.Context.MODE_PRIVATE)
            val noteId = notallyModel.id
            prefs.edit()
                .remove("summary_text_$noteId")
                .remove("original_text_$noteId")
                .remove("is_using_summary_$noteId")
                .apply()
        }
        inlineSummaryVisible = false
        inlineSummaryCurrentText = null
    }

    private fun applyInlineSummaryReplace() {
        val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView)
        // Áp dụng summary vào EnterBody và ẩn slide view
        if (inlineSummaryCurrentText != null) {
            binding.EnterBody.setText(inlineSummaryCurrentText)
        }
        binding.EnterBody.visibility = View.VISIBLE
        slideView?.visibility = View.GONE
        slideView?.reset(animated = false)
        
        // Khôi phục paddingBottom của ContentLayout
        val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
        binding.ContentLayout.setPadding(
            binding.ContentLayout.paddingLeft,
            binding.ContentLayout.paddingTop,
            binding.ContentLayout.paddingRight,
            paddingBottomPx
        )
        
        // Xóa trạng thái summary đã lưu
        val prefs = getSharedPreferences("ai_summary_state", android.content.Context.MODE_PRIVATE)
        val noteId = notallyModel.id
        prefs.edit()
            .remove("summary_text_$noteId")
            .remove("original_text_$noteId")
            .remove("is_using_summary_$noteId")
            .apply()
        
        inlineSummaryVisible = false
        inlineSummaryOriginalText = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActivityResultLaunchers()
        setupInlineSummaryToolbar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putInt(EXTRA_SELECTION_START, binding.EnterBody.selectionStart)
            putInt(EXTRA_SELECTION_END, binding.EnterBody.selectionEnd)
        }
    }

    private fun setupActivityResultLaunchers() {
        pickNoteNewActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        if (emptyTitle) {
                            binding.EnterBody.showAddLinkDialog(
                                this,
                                presetDisplayText = title,
                                presetUrl = url,
                                isNewUnnamedLink = true,
                            )
                        } else {
                            binding.EnterBody.addSpans(title, listOf(UnderlineSpan(), URLSpan(url)))
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
        pickNoteUpdateActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        val newSpan = URLSpan(url)
                        binding.EnterBody.updateSpan(selectedSpan, newSpan, title)
                        if (emptyTitle) {
                            binding.EnterBody.showEditDialog(newSpan, isNewUnnamedLink = true)
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
    }

    override fun highlightSearchResults(search: String): Int {
        binding.EnterBody.clearHighlights()
        if (search.isEmpty()) {
            return 0
        }
        searchResultIndices =
            notallyModel.body.toString().findAllOccurrences(search).onEach { (startIdx, endIdx) ->
                binding.EnterBody.highlight(startIdx, endIdx, false)
            }
        return searchResultIndices!!.size
    }

    override fun selectSearchResult(resultPos: Int) {
        if (resultPos < 0) {
            binding.EnterBody.unselectHighlight()
            return
        }
        searchResultIndices?.get(resultPos)?.let { (startIdx, endIdx) ->
            val selectedLineTop = binding.EnterBody.highlight(startIdx, endIdx, true)
            selectedLineTop?.let { binding.ScrollView.scrollTo(0, it) }
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.EnterBody.initHistory(changeHistory) { text ->
            val textChanged = !notallyModel.body.toString().contentEquals(text)
            notallyModel.body = text
            if (textChanged) {
                updateSearchResults(search.query)
            }
        }
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        updateEditText()
        
        // Restore trạng thái AI summary nếu có
        restoreAISummaryState()
        
        savedInstanceState?.let {
            val selectionStart = it.getInt(EXTRA_SELECTION_START, -1)
            val selectionEnd = it.getInt(EXTRA_SELECTION_END, -1)
            if (selectionStart > -1) {
                binding.EnterBody.focusAndSelect(selectionStart, selectionEnd)
            }
        }
    }
    
    private fun restoreAISummaryState() {
        val prefs = getSharedPreferences("ai_summary_state", android.content.Context.MODE_PRIVATE)
        val noteId = notallyModel.id
        
        // Check if this note has saved summary
        val summaryText = prefs.getString("summary_text_$noteId", null)
        val originalText = prefs.getString("original_text_$noteId", null)
        val isUsingSummary = prefs.getBoolean("is_using_summary_$noteId", false)
        val hasApplied = prefs.getBoolean("has_applied_$noteId", false)
        
        if (summaryText != null && originalText != null) {
            // ⭐ KIỂM TRA: Nếu text hiện tại trong database khác với text đã lưu
            // → User đã chỉnh sửa ghi chú sau khi áp dụng → Xóa state cũ
            val currentTextInDb = notallyModel.body.toString()
            val savedAppliedText = if (isUsingSummary) summaryText else originalText
            
            if (hasApplied && currentTextInDb != savedAppliedText) {
                // Text đã thay đổi → Xóa state cũ và hiển thị text mới
                android.util.Log.d("EditNoteActivity", "Text changed after apply, clearing old AI state")
                prefs.edit()
                    .remove("summary_text_$noteId")
                    .remove("original_text_$noteId")
                    .remove("is_using_summary_$noteId")
                    .remove("has_applied_$noteId")
                    .apply()
                
                // Hiển thị text hiện tại từ database
                binding.EnterBody.visibility = View.VISIBLE
                val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView)
                slideView?.visibility = View.GONE
                
                // Normal padding
                val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
                binding.ContentLayout.setPadding(
                    binding.ContentLayout.paddingLeft,
                    binding.ContentLayout.paddingTop,
                    binding.ContentLayout.paddingRight,
                    paddingBottomPx
                )
                return
            }
            
            if (hasApplied) {
                // User has clicked "Apply" và text chưa thay đổi → Show only the applied text, no card
                val appliedText = if (isUsingSummary) summaryText else originalText
                binding.EnterBody.setText(appliedText)
                notallyModel.body = android.text.Editable.Factory.getInstance().newEditable(appliedText) // ⭐ Update model
                binding.EnterBody.visibility = View.VISIBLE
                
                // Don't show card
                val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView)
                slideView?.visibility = View.GONE
                
                // Normal padding
                val paddingBottomPx = (16 * resources.displayMetrics.density).toInt()
                binding.ContentLayout.setPadding(
                    binding.ContentLayout.paddingLeft,
                    binding.ContentLayout.paddingTop,
                    binding.ContentLayout.paddingRight,
                    paddingBottomPx
                )
            } else {
                // User has NOT clicked "Apply" yet → Show card with both versions
                val slideView = findViewById<com.philkes.notallyx.presentation.view.ai.AiSlideCompareView>(R.id.SlideCompareView)
                
                if (slideView != null) {
                    // Setup texts
                    slideView.setTexts(originalText, summaryText)
                    slideView.visibility = View.VISIBLE
                    
                    // Select the correct version
                    if (isUsingSummary) {
                        slideView.selectSummary(animated = false)
                    } else {
                        slideView.selectOriginal(animated = false)
                    }
                    
                    // Hide EnterBody
                    binding.EnterBody.visibility = View.GONE
                    
                    // Remove paddingBottom
                    binding.ContentLayout.setPadding(
                        binding.ContentLayout.paddingLeft,
                        binding.ContentLayout.paddingTop,
                        binding.ContentLayout.paddingRight,
                        0
                    )
                    
                    // Setup callback
                    setupApplyButtonCallback(slideView, summaryText, originalText)
                    
                    // Set state
                    inlineSummaryVisible = true
                    inlineSummaryOriginalText = originalText
                    inlineSummaryCurrentText = summaryText
                }
            }
        }
    }

    private fun updateEditText() {
        binding.EnterBody.text = notallyModel.body
    }

    private fun setupEditor() {
        setupMovementMethod()

        binding.EnterBody.customSelectionActionModeCallback =
            object : ActionMode.Callback {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    binding.EnterBody.isActionModeOn = true
                    try {
                        menu?.apply {
                            add(R.string.link, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.showAddLinkDialog(
                                    this@EditNoteActivity,
                                    mode = mode,
                                )
                            }
                            add(R.string.bold, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.BOLD))
                                mode?.finish()
                            }
                            add(R.string.italic, 0, showAsAction = MenuItem.SHOW_AS_ACTION_NEVER) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.ITALIC))
                                mode?.finish()
                            }
                            add(
                                R.string.monospace,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.applySpan(TypefaceSpan("monospace"))
                                mode?.finish()
                            }
                            add(
                                R.string.strikethrough,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.applySpan(StrikethroughSpan())
                                mode?.finish()
                            }
                            add(
                                R.string.clear_formatting,
                                0,
                                showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                            ) {
                                binding.EnterBody.clearFormatting()
                                mode?.finish()
                            }
                        }
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    binding.EnterBody.isActionModeOn = false
                }
            }

        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.EnterBody.customInsertionActionModeCallback =
                object : ActionMode.Callback {
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        binding.EnterBody.isActionModeOn = true
                        try {
                            menu?.apply {
                                add(R.string.link_note, 0, order = Menu.CATEGORY_CONTAINER + 1) {
                                    linkNote(pickNoteNewActivityResultLauncher)
                                    mode?.finish()
                                }
                            }
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        binding.EnterBody.isActionModeOn = false
                    }
                }
        }
        binding.EnterBody.setOnSelectionChange { selStart, selEnd ->
            if (selEnd - selStart > 0) {
                if (!::textFormatMenu.isInitialized || !textFormatMenu.isEnabled) {
                    initBottomTextFormattingMenu()
                }
                if (::textFormatMenu.isInitialized) {
                    textFormatMenu.isEnabled = true
                }
                textFormattingAdapter?.updateTextFormattingToggles(selStart, selEnd)
            } else {
                if (::textFormatMenu.isInitialized && textFormatMenu.isEnabled) {
                    initBottomMenu()
                }
                if (::textFormatMenu.isInitialized) {
                    textFormatMenu.isEnabled = false
                }
                hideFormatToolbar()
            }
        }
        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
    }

    override fun initBottomMenu() {
        super.initBottomMenu()
    }

    override fun openAddItemMenu() {
        AddNoteBottomSheet(this, colorInt).show(supportFragmentManager, AddNoteBottomSheet.TAG)
    }

    override fun openTextFormattingMenu() {
        binding.EnterBody.requestFocus()
        if (
            binding.EnterBody.selectionStart < 0 ||
                binding.EnterBody.selectionStart > binding.EnterBody.length()
        ) {
            binding.EnterBody.setSelection(binding.EnterBody.length())
        }

        showNewFormatSheet()
    }

    private var styleAdapter: TextFormattingStyleAdapter? = null
    private var formatAdapter: TextFormattingFormatAdapter? = null
    private var listsAdapter: TextFormattingListsAdapter? = null

    private fun showFormatToolbar() {
        if (formatToolbarBinding == null) {
            formatToolbarBinding = FormatToolbarBinding.inflate(layoutInflater)
            formatToolbarAdapter = FormatToolbarAdapter(this, binding.EnterBody, colorInt)
            
            formatToolbarBinding?.FormatRecyclerView?.apply {
                adapter = formatToolbarAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            
            // Add to coordinator layout with proper layout params
            val coordinatorLayout = binding.root as? androidx.coordinatorlayout.widget.CoordinatorLayout
            if (coordinatorLayout != null) {
                val layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = (16 * resources.displayMetrics.density).toInt() // 16dp margin from bottom
                }
                formatToolbarBinding?.root?.layoutParams = layoutParams
                coordinatorLayout.addView(formatToolbarBinding?.root)
            }
        }
        
        formatToolbarBinding?.root?.apply {
            visibility = View.VISIBLE
            bringToFront()
            requestLayout()
        }
    }
    
    private fun hideFormatToolbar() {
        formatToolbarBinding?.root?.visibility = View.GONE
    }

    private fun showNewFormatSheet() {
        if (newFormatSheet == null) {
            val layout = BottomSheetFormatNewBinding.inflate(layoutInflater)
            
            // Setup top toolbar icons
            setupTopToolbarIcons(layout)
            
            // Setup size slider
            setupSizeSlider(layout)
            
            // Setup color row
            setupColorRow(layout)
            
            newFormatSheet = BottomSheetDialog(this).apply {
                setContentView(layout.root)
                setOnDismissListener { 
                    this@EditNoteActivity.hideKeyboard(binding.EnterBody) 
                }
            }
        }
        
        newFormatSheet?.show()
    }
    
    private fun setupTopToolbarIcons(binding: BottomSheetFormatNewBinding) {
        binding.IconFont.setOnClickListener {
            showFontDialog()
        }
        
        binding.IconSize.setOnClickListener {
            // Show size number picker
            showSizeNumberPicker(binding)
        }
        
        binding.IconEllipse.setOnClickListener {
            showTextColorDialog()
        }
        
        binding.IconBold.setOnClickListener {
            toggleBoldFormat()
        }
        
        binding.IconItalic.setOnClickListener {
            toggleItalicFormat()
        }
        
        binding.IconUnderline.setOnClickListener {
            toggleUnderlineFormat()
        }
        
        binding.IconTextLine.setOnClickListener {
            toggleStrikethroughFormat()
        }
        
        binding.IconTextHighlight.setOnClickListener {
            showHighlightDialog()
        }
        
        binding.IconJustify.setOnClickListener {
            justifyText()
        }
    }
    
    private fun setupSizeSlider(binding: BottomSheetFormatNewBinding) {
        // Get current font size from selection or default to 14
        val currentSize = 14 // TODO: Get actual font size from selection
        binding.CurrentSize.text = currentSize.toString()
        
        // Setup SliderView similar to drawTool
        binding.SizeSliderView.apply {
            setFirstGradientColor("#75E073")
            setCurrentColor("#75E073")
            setSecondGradientColor("#75E073")
            setIsOpacitySlider(false)
            // Set initial progress (0.0 to 1.0, where 1.0 = 100)
            setProgress((currentSize / 100f).coerceIn(0.1f, 1.0f))
            sliderViewListener = object : com.philkes.notallyx.common.ui.view.sliderview.SliderView.SliderViewListener {
                override fun onProgressChanged(value: Float) {
                    val size = (value * 100).toInt().coerceIn(10, 100)
                    binding.CurrentSize.text = size.toString()
                    applyFontSize(size)
                }
            }
        }
        
        // Click on size text to show number picker
        binding.CurrentSize.setOnClickListener {
            showSizeNumberPicker(binding)
        }
    }
    
    private fun showSizeNumberPicker(binding: BottomSheetFormatNewBinding) {
        val sizes = (10..100).toList().toTypedArray()
        val currentSize = binding.CurrentSize.text.toString().toIntOrNull() ?: 14
        val selectedIndex = sizes.indexOfFirst { it == currentSize }.coerceAtLeast(0)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Font Size")
            .setSingleChoiceItems(sizes.map { it.toString() }.toTypedArray(), selectedIndex) { dialog, which ->
                val size = sizes[which]
                binding.CurrentSize.text = size.toString()
                binding.SizeSliderView.setProgress(size / 100f)
                applyFontSize(size)
                dialog.dismiss()
            }
            .show()
    }
    
    private fun setupColorRow(binding: BottomSheetFormatNewBinding) {
        // Click vào icon palette → Mở color picker dialog giống draw tool
        binding.ColorPaletteIcon.setOnClickListener {
            showMoreColor { selectedColor ->
                applyTextColor(selectedColor)
            }
        }
        
        binding.IconAlignLeft.setOnClickListener {
            alignLeft()
        }
        
        binding.IconAlignMiddle.setOnClickListener {
            alignCenter()
        }
        
        binding.IconAlignRight.setOnClickListener {
            alignRight()
        }
        
        binding.IconList.setOnClickListener {
            toggleBulletList()
        }
        
        binding.IconListNumbered.setOnClickListener {
            toggleNumberedList()
        }
        
        binding.IconListDash.setOnClickListener {
            toggleDashList()
        }
        
        binding.IconListPlus.setOnClickListener {
            togglePlusList()
        }
    }
    
    private fun showFontDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Font")
            .setMessage("Font selection will be implemented")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun applyTextColor(color: Int) {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        if (selStart == selEnd) {
            binding.EnterBody.setSelection(selStart)
        }
        binding.EnterBody.applySpan(
            ForegroundColorSpan(color), 
            selStart, 
            selEnd
        )
    }
    
    private fun showTextColorDialog() {
        val colors = intArrayOf(
            Color.BLACK, Color.RED, Color.BLUE, Color.GREEN,
            Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.GRAY
        )
        val colorNames = arrayOf(
            "Black", "Red", "Blue", "Green",
            "Yellow", "Magenta", "Cyan", "Gray"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Text Color")
            .setItems(colorNames) { _, which ->
                val selStart = binding.EnterBody.selectionStart
                val selEnd = binding.EnterBody.selectionEnd
                if (selStart == selEnd) {
                    binding.EnterBody.setSelection(selStart)
                }
                binding.EnterBody.applySpan(
                    ForegroundColorSpan(colors[which]), 
                    selStart, 
                    selEnd
                )
            }
            .show()
    }
    
    private fun toggleBoldFormat() {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val hasBold = binding.EnterBody.getSpans(selStart, selEnd)
            .any { it is StyleSpan && it.style == Typeface.BOLD }
        
        if (hasBold) {
            binding.EnterBody.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.BOLD)
        } else {
            binding.EnterBody.applySpan(StyleSpan(Typeface.BOLD))
        }
    }
    
    private fun toggleItalicFormat() {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val hasItalic = binding.EnterBody.getSpans(selStart, selEnd)
            .any { it is StyleSpan && it.style == Typeface.ITALIC }
        
        if (hasItalic) {
            binding.EnterBody.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.ITALIC)
        } else {
            binding.EnterBody.applySpan(StyleSpan(Typeface.ITALIC))
        }
    }
    
    private fun toggleUnderlineFormat() {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val hasUnderline = binding.EnterBody.getSpans(selStart, selEnd)
            .any { it is UnderlineSpan }
        
        if (hasUnderline) {
            binding.EnterBody.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.UNDERLINE)
        } else {
            binding.EnterBody.applySpan(UnderlineSpan())
        }
    }
    
    private fun toggleStrikethroughFormat() {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val hasStrikethrough = binding.EnterBody.getSpans(selStart, selEnd)
            .any { it is StrikethroughSpan }
        
        if (hasStrikethrough) {
            binding.EnterBody.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.STRIKETHROUGH)
        } else {
            binding.EnterBody.applySpan(StrikethroughSpan())
        }
    }
    
    private fun showHighlightDialog() {
        // Hiển thị color picker dialog giống hệt khi click palette trong draw tool
        showMoreColor { selectedColor ->
            applyHighlight(selectedColor)
        }
    }
    
    private fun applyHighlight(color: Int) {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        if (selStart == selEnd) {
            binding.EnterBody.setSelection(selStart)
        }
        binding.EnterBody.applySpan(
            android.text.style.BackgroundColorSpan(color), 
            selStart, 
            selEnd
        )
    }
    
    private fun applyFontSize(size: Int) {
        // Placeholder - implement font size change
        // This would typically use RelativeSizeSpan or AbsoluteSizeSpan
    }
    
    private fun alignLeft() {
        // Placeholder - implement alignment
    }
    
    private fun alignCenter() {
        // Placeholder - implement alignment
    }
    
    private fun alignRight() {
        // Placeholder - implement alignment
    }
    
    private fun justifyText() {
        // Placeholder - implement justify alignment
        // This would typically use AlignmentSpan.Standard with Layout.Alignment.ALIGN_NORMAL
    }
    
    private fun toggleBulletList() {
        applyListPrefix("• ")
    }
    
    private fun toggleNumberedList() {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val text = binding.EnterBody.text ?: return
        
        if (selStart == selEnd) {
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            
            val lineText = textStr.substring(lineStart, lineEnd)
            // Find line number by counting previous numbered lines
            val previousText = textStr.substring(0, lineStart)
            val previousLines = previousText.split('\n')
            var lineNumber = 1
            for (line in previousLines) {
                if (line.matches(Regex("^\\d+\\. .*"))) {
                    lineNumber++
                }
            }
            
            val prefix = "${lineNumber}. "
            if (!lineText.startsWith(prefix) && !lineText.matches(Regex("^\\d+\\. .*"))) {
                binding.EnterBody.changeTextWithHistory { editable ->
                    editable.insert(lineStart, prefix)
                }
            }
        } else {
            // Multiple lines - add numbered list
            val textStr = text.toString()
            val selectedText = textStr.substring(selStart, selEnd)
            val lines = selectedText.split('\n')
            var currentPos = selStart
            var lineNumber = 1
            
            binding.EnterBody.changeTextWithHistory { editable ->
                lines.forEachIndexed { index, line ->
                    val lineStartInEditable = currentPos
                    if (!line.matches(Regex("^\\d+\\. .*")) && line.isNotEmpty()) {
                        editable.insert(lineStartInEditable, "${lineNumber}. ")
                        currentPos += "${lineNumber}. ".length
                        lineNumber++
                    }
                    currentPos += line.length + if (index < lines.size - 1) 1 else 0
                }
            }
        }
    }
    
    private fun toggleDashList() {
        applyListPrefix("- ")
    }
    
    private fun togglePlusList() {
        applyListPrefix("+ ")
    }
    
    private fun applyListPrefix(prefix: String) {
        val selStart = binding.EnterBody.selectionStart
        val selEnd = binding.EnterBody.selectionEnd
        val text = binding.EnterBody.text ?: return
        
        if (selStart == selEnd) {
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            
            val lineText = textStr.substring(lineStart, lineEnd)
            if (!lineText.startsWith(prefix)) {
                binding.EnterBody.changeTextWithHistory { editable ->
                    editable.insert(lineStart, prefix)
                }
            }
        } else {
            val textStr = text.toString()
            val selectedText = textStr.substring(selStart, selEnd)
            val lines = selectedText.split('\n')
            var currentPos = selStart
            
            binding.EnterBody.changeTextWithHistory { editable ->
                lines.forEachIndexed { index, line ->
                    val lineStartInEditable = currentPos
                    if (!line.startsWith(prefix) && line.isNotEmpty()) {
                        editable.insert(lineStartInEditable, prefix)
                        currentPos += prefix.length
                    }
                    currentPos += line.length + if (index < lines.size - 1) 1 else 0
                }
            }
        }
    }

    private fun showTextFormatSheet() {
        val updateAll: () -> Unit = {
            styleAdapter?.updateToggles()
            formatAdapter?.updateToggles()
            listsAdapter?.updateToggles()
        }

        if (textFormatSheet == null) {
            val layout = BottomSheetTextFormatBinding.inflate(layoutInflater)

            styleAdapter =
                TextFormattingStyleAdapter(this, binding.EnterBody, colorInt, onUpdate = updateAll)
            layout.RecyclerViewStyles.apply {
                adapter = styleAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }

            formatAdapter =
                TextFormattingFormatAdapter(this, binding.EnterBody, colorInt, onUpdate = updateAll)
            layout.RecyclerViewFormatting.apply {
                adapter = formatAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }

            listsAdapter =
                TextFormattingListsAdapter(this, binding.EnterBody, colorInt, onUpdate = updateAll)
            layout.RecyclerViewLists.apply {
                adapter = listsAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }

            layout.CloseButton.setOnClickListener { textFormatSheet?.dismiss() }

            textFormatSheet =
                BottomSheetDialog(this).apply {
                    setContentView(layout.root)
                    setOnDismissListener { this@EditNoteActivity.hideKeyboard(binding.EnterBody) }
                }
        }

        updateAll()
        textFormatSheet?.show()
    }

    override fun openMoreMenu() {
        val toolbar = binding.Toolbar
        val ivMore = toolbar.findViewById<View>(R.id.ivMore) ?: return

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

    private fun ensureAICenterButton() {
        if (binding.BottomAppBarCenter.childCount > 0) return
        val button =
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
                .apply {
                    text = getString(R.string.ai_action_button_label)
                    icon = ContextCompat.getDrawable(this@EditNoteActivity, R.drawable.ai_sparkle)
                    iconPadding = 8.dp
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    minWidth = 0
                    minimumWidth = 0
                    setInsetTop(0)
                    setInsetBottom(0)
                    setPaddingRelative(20.dp, 6.dp, 20.dp, 6.dp)
                    cornerRadius = resources.getDimensionPixelSize(R.dimen.dp_20)
                    val primary =
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorPrimary,
                            0,
                        )
                    val onPrimary =
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorOnPrimary,
                            Color.WHITE,
                        )
                    setBackgroundTintList(ColorStateList.valueOf(primary))
                    setTextColor(onPrimary)
                    iconTint = ColorStateList.valueOf(onPrimary)
                    strokeWidth = resources.getDimensionPixelSize(R.dimen.dp_1)
                    strokeColor =
                        ColorStateList.valueOf(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorPrimaryContainer,
                                primary,
                            )
                        )
                    setOnClickListener { openAIActionsMenu() }
                }
        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        binding.BottomAppBarCenter.addView(button, params)
    }

    override fun openAIActionsMenu() {
        val noteText = binding.EnterBody.text?.toString().orEmpty()
        if (noteText.isBlank()) {
            showToast(R.string.ai_error_empty_note)
            return
        }
        
        // Process AI TRƯỚC, sau đó mới hiển thị menu
        processTextAIThenShowMenu(noteText)
    }
    
    /**
     * Xử lý AI trước, cache kết quả, sau đó hiển thị menu
     */
    private fun processTextAIThenShowMenu(noteText: String) {
        val userId = getAiUserId()
        val attachmentUris = getAttachedFileUris()
        val mode = "text"
        val currentHash = computeContentHash(noteText, attachmentUris)
        val localNoteId = notallyModel.id
        
        // Kiểm tra cache local trước
        if (cachedTextResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash = com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                this,
                localNoteId,
                mode,
            )
            if (currentHash == storedHash) {
                // Đã có cache và hash khớp → Hiển thị menu ngay
                showAIOptionsMenu(cachedTextResult!!)
                return
            }
        }
        
        // Không có cache hoặc hash không khớp → Gọi API
        val backendNoteId = ensureBackendNoteIdForCurrentNote(noteText, attachmentUris)
        
        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }
        
        lifecycleScope.launch {
            try {
                // Kiểm tra server cache trước
                val serverCached = aiRepository!!.getCachedNote(userId, backendNoteId, checkVocabData = false)
                if (serverCached != null) {
                    cachedTextResult = serverCached
                    if (localNoteId != -1L && currentHash != null) {
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                            this@EditNoteActivity,
                            localNoteId,
                            mode,
                            currentHash,
                        )
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                            this@EditNoteActivity,
                            localNoteId,
                            backendNoteId,
                        )
                    }
                    showAIOptionsMenu(serverCached)
                    return@launch
                }
            } catch (_: Exception) {}
            
            // Không có server cache → Gọi API xử lý
            val loadingDialog = android.app.ProgressDialog(this@EditNoteActivity).apply {
                setMessage(getString(R.string.ai_processing))
                setCancelable(false)
                show()
            }
            
            try {
                val result = aiRepository!!.processCombinedInputs(
                    noteText = noteText,
                    attachments = attachmentUris,
                    userId = userId,
                    noteId = backendNoteId,
                    contentType = null,
                    checkedVocabItems = null,
                    useCache = true,
                )
                
                loadingDialog.dismiss()
                
                when (result) {
                    is AIResult.Success -> {
                        cachedTextResult = result.data
                        
                        // Lưu hash và backend_note_id
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                                this@EditNoteActivity,
                                localNoteId,
                                mode,
                                currentHash,
                            )
                            com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                                this@EditNoteActivity,
                                localNoteId,
                                backendNoteId,
                            )
                        }
                        
                        // Hiển thị menu
                        showAIOptionsMenu(result.data)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // ignore
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
                val options = com.philkes.notallyx.presentation.view.note.ai.AIOption.getDefaultForText()
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
                android.util.Log.e("EditNoteActivity", "Error showing AI popup", e)
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
                showInlineSummaryPreview(cachedResult)
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.KEY -> {
                showInlineSummaryPreview(cachedResult, preferBulletPoints = true)
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.MCQ -> {
                startTextMcqFlow(cachedResult.mcqs)
            }
            com.philkes.notallyx.presentation.view.note.ai.AIOptionType.QUESTION -> {
                AISummaryActivity.startWithResult(
                    context = this,
                    summaryResponse = cachedResult,
                    noteId = notallyModel.id,
                    showAllSections = false,
                    initialSection = AISummaryActivity.AISection.QUESTIONS,
                    isVocabMode = false,
                )
            }
            else -> {
                showToast(R.string.ai_error_generic)
            }
        }
    }
    
    private fun showTextActionsBottomSheet(cachedResult: SummaryResponse, backendNoteId: String) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ai_actions, null)

        sheetView.findViewById<View>(R.id.ActionSummary).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.SUMMARY,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionBullet).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.BULLET_POINTS,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionQuestions).setOnClickListener {
            dialog.dismiss()
            AISummaryActivity.startWithResult(
                context = this,
                summaryResponse = cachedResult,
                noteId = notallyModel.id,
                showAllSections = false,
                initialSection = AISummaryActivity.AISection.QUESTIONS,
                isVocabMode = false,
            )
        }

        sheetView.findViewById<View>(R.id.ActionMCQ).setOnClickListener {
            dialog.dismiss()
            startTextMcqFlow(cachedResult.mcqs)
        }

        sheetView.findViewById<View>(R.id.ActionFile).visibility = View.GONE
        sheetView.findViewById<View>(R.id.ActionHistory).visibility = View.GONE

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun getAttachedFileUris(): List<Uri> {
        val uris = mutableListOf<Uri>()

        // Files
        val filesRoot = notallyModel.filesRoot
        val fileAttachments = notallyModel.files.value ?: emptyList()
        if (filesRoot != null && fileAttachments.isNotEmpty()) {
            fileAttachments.forEach { attachment ->
                val file = File(filesRoot, attachment.localName)
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
                val file = File(imagesRoot, attachment.localName)
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
                val file = File(audioRoot, audio.name)
                if (file.exists()) {
                    uris.add(this.getUriForFile(file))
                }
            }
        }

        return uris.distinct()
    }

    private fun runTextAIAndShowActions(noteText: String) {
        val userId = getAiUserId()
        // V?n l?y attachments (image/audio/file) n?u ghi ch? c?, ?? g?i /process/combined
        val attachmentUris = getAttachedFileUris()
        val mode = "text"
        val currentHash = computeContentHash(noteText, attachmentUris)
        val localNoteId = notallyModel.id

        // N?u c? cachedResult v? hash kh?p -> d?ng ngay
        if (cachedTextResult != null && localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                )
            if (currentHash == storedHash) {
                val backendNoteId =
                    com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                        this,
                        localNoteId,
                    ) ?: ensureBackendNoteIdForCurrentNote(noteText, attachmentUris)
                showTextActionsBottomSheet(cachedTextResult!!, backendNoteId)
                return
            }
        }

        val backendNoteId = ensureBackendNoteIdForCurrentNote(noteText, attachmentUris)

        if (aiRepository == null) {
            aiRepository = AIRepository(this)
        }

        lifecycleScope.launch {
            try {
                val serverCached =
                    aiRepository!!.getCachedNote(userId, backendNoteId, checkVocabData = false)
                if (serverCached != null) {
                    cachedTextResult = serverCached
                    // L?u hash ?? l?n sau kh?p
                    if (localNoteId != -1L && currentHash != null) {
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                            this@EditNoteActivity,
                            localNoteId,
                            mode,
                            currentHash,
                        )
                        com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                            this@EditNoteActivity,
                            localNoteId,
                            backendNoteId,
                        )
                    }
                    showTextActionsBottomSheet(serverCached, backendNoteId)
                    return@launch
                }
            } catch (_: Exception) {}

            val loadingDialog =
                android.app.ProgressDialog(this@EditNoteActivity).apply {
                    setMessage(getString(R.string.ai_processing))
                    setCancelable(false)
                    show()
                }
            try {
                val result =
                    aiRepository!!.processCombinedInputs(
                        noteText = noteText,
                        attachments = attachmentUris,
                        userId = userId,
                        noteId = backendNoteId,
                        contentType = null,
                        checkedVocabItems = null,
                        useCache = true,
                    )
                loadingDialog.dismiss()
                when (result) {
                    is AIResult.Success -> {
                        cachedTextResult = result.data
                        // L?u hash v? backend_note_id
                        if (localNoteId != -1L && currentHash != null) {
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setNoteContentHash(
                                    this@EditNoteActivity,
                                    localNoteId,
                                    mode,
                                    currentHash,
                                )
                            com.philkes.notallyx.data.preferences.AIUserPreferences
                                .setBackendNoteId(this@EditNoteActivity, localNoteId, backendNoteId)
                        }
                        showTextActionsBottomSheet(result.data, backendNoteId)
                    }
                    is AIResult.Error -> {
                        showToast(result.message ?: getString(R.string.ai_error_generic))
                    }
                    is AIResult.Loading -> {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showToast("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun ensureBackendNoteIdForCurrentNote(
        noteText: String,
        attachments: List<Uri>,
    ): String {
        val mode = if (attachments.isEmpty()) "text" else "combined"
        val currentHash = computeContentHash(noteText, attachments)
        val localNoteId = notallyModel.id

        if (localNoteId != -1L && currentHash != null) {
            val storedHash =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                )
            val storedBackend =
                com.philkes.notallyx.data.preferences.AIUserPreferences.getBackendNoteId(
                    this,
                    localNoteId,
                )

            android.util.Log.d(
                "EditNoteActivity",
                "ensureBackendNoteIdForCurrentNote: localNoteId=$localNoteId, mode=$mode, currentHash=${currentHash.take(16)}..., storedHash=${storedHash?.take(16)}..., storedBackend=$storedBackend",
            )

            if (currentHash == storedHash && storedBackend != null) {
                android.util.Log.d(
                    "EditNoteActivity",
                    "ensureBackendNoteIdForCurrentNote: Reusing existing backend_note_id=$storedBackend",
                )
                return storedBackend
            }
        }

        android.util.Log.d(
            "EditNoteActivity",
            "ensureBackendNoteIdForCurrentNote: Hash mismatch or no mapping, generating new UUID. currentHash=${currentHash?.take(16)}...",
        )
        val generated = UUID.randomUUID().toString()
        if (localNoteId != -1L) {
            com.philkes.notallyx.data.preferences.AIUserPreferences.setBackendNoteId(
                this,
                localNoteId,
                generated,
            )
            currentHash?.let {
                com.philkes.notallyx.data.preferences.AIUserPreferences.setNoteContentHash(
                    this,
                    localNoteId,
                    mode,
                    it,
                )
            }
            android.util.Log.d(
                "EditNoteActivity",
                "ensureBackendNoteIdForCurrentNote: Generated and saved new backend_note_id=$generated",
            )
        }
        return generated
    }

    private fun computeContentHash(noteText: String, attachments: List<Uri>): String? {
        val trimmed = noteText.trim()
        val hasAttachments = attachments.isNotEmpty()
        if (trimmed.isBlank() && !hasAttachments) return null

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(if (hasAttachments) "combined::".toByteArray() else "text::".toByteArray())
        if (trimmed.isNotBlank()) {
            digest.update(trimmed.toByteArray())
        }
        if (hasAttachments) {
            attachments
                .sortedBy { it.toString() }
                .forEach { uri -> digest.update(uri.toString().toByteArray()) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun initBottomTextFormattingMenu() {
        binding.BottomAppBarCenter.visibility = GONE
        val extractColor = colorInt
        binding.BottomAppBarRight.apply {
            removeAllViews()
            addView(
                RecyclerToggleBinding.inflate(layoutInflater, this, false).root.apply {
                    setIconResource(R.drawable.close)
                    contentDescription = context.getString(R.string.cancel)
                    setOnClickListener { initBottomMenu() }

                    updateLayoutParams<LinearLayout.LayoutParams> {
                        marginEnd = 0
                        marginStart = 10.dp
                    }
                    setControlsContrastColorForAllViews(extractColor)
                    setBackgroundColor(0)
                }
            )
        }
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            requestLayout()
            val layout = BottomTextFormattingMenuBinding.inflate(layoutInflater, this, false)
            layout.RecyclerView.apply {
                textFormattingAdapter =
                    TextFormattingAdapter(this@EditNoteActivity, binding.EnterBody, colorInt)
                adapter = textFormattingAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            textFormatMenu = layout.root
            addView(layout.root)
        }
    }

    override fun linkNote() {
        linkNote(pickNoteNewActivityResultLauncher)
    }

    fun linkNote(activityResultLauncher: ActivityResultLauncher<Intent>) {
        val intent =
            Intent(this, PickNoteActivity::class.java).apply {
                putExtra(EXTRA_EXCLUDE_NOTE_ID, notallyModel.id)
            }
        activityResultLauncher.launch(intent)
    }

    private fun setupMovementMethod() {
        val movementMethod = LinkMovementMethod { span ->
            val items =
                if (span.url.isNoteUrl()) {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.change_note),
                        getString(R.string.edit),
                        getString(R.string.open_note),
                    )
                } else {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.copy),
                        getString(R.string.edit),
                        getString(R.string.open_link),
                    )
                }
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    if (span.url.isNoteUrl())
                        "${getString(R.string.note)}: ${
                            binding.EnterBody.getSpanText(span)
                        }"
                    else span.url
                )
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            binding.EnterBody.removeSpanWithHistory(
                                span,
                                span.url.isNoteUrl() ||
                                    span.url == binding.EnterBody.getSpanText(span),
                            )
                        }
                        1 ->
                            if (span.url.isNoteUrl()) {
                                selectedSpan = span
                                linkNote(pickNoteUpdateActivityResultLauncher)
                            } else {
                                copyToClipBoard(span.url)
                                showToast(R.string.copied_link)
                            }

                        2 -> {
                            binding.EnterBody.showEditDialog(span)
                        }

                        3 -> {
                            span.url?.let {
                                if (it.isNoteUrl()) {
                                    span.navigateToNote()
                                } else {
                                    openLink(span.url)
                                }
                            }
                        }
                    }
                }
                .show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }

    private fun openLink(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).wrapWithChooser(this)
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            showToast(R.string.cant_open_link)
        }
    }

    private fun URLSpan.navigateToNote() {
        val noteId = url.getNoteIdFromUrl()
        val noteType = url.getNoteTypeFromUrl()
        when (noteType) {
            Type.NOTE -> goToActivity(EditNoteActivity::class.java, noteId)
            Type.LIST -> goToActivity(EditListActivity::class.java, noteId)
        }
    }

    private fun goToActivity(activity: Class<out Activity>, noteId: Long) {
        val intent = Intent(this, activity)
        intent.putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
        startActivity(intent)
    }

    // MCQ quiz flow for text notes: show difficulty directly from Notes
    private fun startTextMcqFlow(mcqs: MCQs?) {
        if (mcqs == null || (
                mcqs.easy.isNullOrEmpty() &&
                    mcqs.medium.isNullOrEmpty() &&
                    mcqs.hard.isNullOrEmpty()
            )
        ) {
            showToast(R.string.ai_error_generic)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("AI MCQ Practice")
            .setItems(arrayOf("Easy", "Medium", "Hard")) { dialog, which ->
                val difficulty =
                    when (which) {
                        0 -> "easy"
                        1 -> "medium"
                        else -> "hard"
                    }
                val list =
                    when (difficulty) {
                        "easy" -> mcqs.easy
                        "medium" -> mcqs.medium
                        "hard" -> mcqs.hard
                        else -> mcqs.easy
                    } ?: emptyList()

                if (list.isEmpty()) {
                    showToast(getString(R.string.ai_error_generic))
                } else {
                    val json = Gson().toJson(list)
                    val intent =
                        Intent(this, com.philkes.notallyx.presentation.activity.ai.TextMcqQuizActivity::class.java)
                            .apply {
                                putExtra(
                                    com.philkes.notallyx.presentation.activity.ai.TextMcqQuizActivity.EXTRA_MCQS_JSON,
                                    json,
                                )
                                putExtra(
                                    com.philkes.notallyx.presentation.activity.ai.TextMcqQuizActivity.EXTRA_DIFFICULTY,
                                    difficulty,
                                )
                            }
                    startActivity(intent)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun Intent?.getPickedNoteData(): Triple<String, String, Boolean> {
        val noteId = this?.getLongExtra(EXTRA_PICKED_NOTE_ID, -1L)!!
        if (noteId == -1L) {
            throw IllegalArgumentException("Invalid note picked!")
        }
        var emptyTitle = false
        val noteTitle =
            this.getStringExtra(EXTRA_PICKED_NOTE_TITLE)!!.ifEmpty {
                emptyTitle = true
                this@EditNoteActivity.getString(R.string.note)
            }
        val noteType = Type.valueOf(this.getStringExtra(EXTRA_PICKED_NOTE_TYPE)!!)
        val noteUrl = noteId.createNoteUrl(noteType)
        return Triple(noteTitle, noteUrl, emptyTitle)
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_format, null)
        val dialog = MaterialAlertDialogBuilder(this)
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
        val buttonCancel = dialogView.findViewById<MaterialButton>(R.id.ButtonCancel)
        val buttonExport = dialogView.findViewById<MaterialButton>(R.id.ButtonExport)

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
                formatCard.strokeColor = Color.parseColor("#6B4EFF")
                formatCard.strokeWidth = 2
            } else {
                formatCard.strokeColor = Color.TRANSPARENT
                formatCard.strokeWidth = 0
            }

            itemView.setOnClickListener {
                // Reset all cards
                for (i in 0 until formatGrid.childCount) {
                    val child = formatGrid.getChildAt(i) as? com.google.android.material.card.MaterialCardView
                    child?.strokeColor = Color.TRANSPARENT
                    child?.strokeWidth = 0
                }
                // Select clicked card
                formatCard.strokeColor = Color.parseColor("#6B4EFF")
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

    override fun onDestroy() {
        // Dismiss BottomSheetDialogs to prevent window leaks
        textFormatSheet?.dismiss()
        textFormatSheet = null
        
        newFormatSheet?.dismiss()
        newFormatSheet = null
        
        // Clear adapter references to prevent memory leaks
        textFormattingAdapter = null
        formatToolbarAdapter = null
        styleAdapter = null
        formatAdapter = null
        listsAdapter = null
        
        // Clear repository reference
        aiRepository = null
        
        // Clear cached AI results
        cachedTextResult = null
        
        // Clear search results
        searchResultIndices = null
        
        // Call parent cleanup
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EditNoteActivity"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
