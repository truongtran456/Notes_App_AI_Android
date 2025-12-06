package com.philkes.notallyx.presentation.activity.note

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.VISIBLE
import android.widget.ImageView
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.philkes.notallyx.R
import com.philkes.notallyx.common.datasource.AppSharePrefs
import com.philkes.notallyx.common.datasource.AppSharePrefsImpl
import com.philkes.notallyx.common.extension.rawColor
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.databinding.ActivityEditBinding
import com.philkes.notallyx.draw.ui.background.BackgroundBottomSheet
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolData
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolPickerView
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.main.fragment.DisplayLabelFragment.Companion.EXTRA_DISPLAYED_LABEL
import com.philkes.notallyx.presentation.activity.note.SelectLabelsActivity.Companion.EXTRA_SELECTED_LABELS
import com.philkes.notallyx.presentation.activity.note.reminders.RemindersActivity
import android.widget.LinearLayout
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addFastScroll
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.bindLabels
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.isLightColor
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setLightStatusAndNavBar
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.note.ErrorAdapter
import com.philkes.notallyx.presentation.view.note.action.Action
import com.philkes.notallyx.presentation.view.note.action.AddActions
import com.philkes.notallyx.presentation.view.note.action.AddBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreActions
import com.philkes.notallyx.presentation.view.note.action.MoreNoteBottomSheet
import com.philkes.notallyx.presentation.view.note.audio.AudioAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewFileAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewImageAdapter
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.backup.exportNotes
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.mergeSkipFirst
import com.philkes.notallyx.utils.observeSkipFirst
import com.philkes.notallyx.utils.shareNote
import com.philkes.notallyx.utils.showColorSelectDialog
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class EditActivity(private val type: Type) :
    LockedActivity<ActivityEditBinding>(), AddActions, MoreActions {
    private lateinit var audioAdapter: AudioAdapter
    private lateinit var fileAdapter: PreviewFileAdapter
    private lateinit var recordAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var addImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var selectLabelsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var playAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var attachFilesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportNotesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>

    protected var search = Search()

    internal val notallyModel: NotallyModel by viewModels()

    internal lateinit var changeHistory: ChangeHistory
    protected var undo: View? = null
    protected var redo: View? = null

    private val drawTools by lazy { DrawToolData.getDefault() }

    private val appSharePrefs: AppSharePrefs by lazy {
        val sharedPrefs =
            getSharedPreferences(AppSharePrefsImpl.Keys.SHARED_PREFS_NAME, MODE_PRIVATE)
        val gson = Gson()
        AppSharePrefsImpl(sharedPrefs, gson)
    }

    private var currentDrawTool: DrawToolBrush? = null
    private var isDrawingModeActive: Boolean = false
    private var canvasFixedHeight: Int = 0 // Chiều cao canvas khi ở draw mode

    protected var colorInt: Int = -1
    protected var inputMethodManager: InputMethodManager? = null

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (notallyModel.isEmpty()) {
                notallyModel.deleteBaseNote(checkAutoSave = false)
            } else if (notallyModel.isModified()) {
                saveNote()
            }
            super.finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", notallyModel.id)
        if (notallyModel.isModified()) {
            lifecycleScope.launch { saveNote() }
        }
    }

    open suspend fun saveNote() {
        // L?u strokes v�o notallyModel tr??c khi save
        if (isDrawingModeActive) {
            val strokes = binding.DrawingCanvas.getStrokes()
            notallyModel.drawingStrokes = ArrayList(strokes)
        }

        notallyModel.modifiedTimestamp = System.currentTimeMillis()
        notallyModel.saveNote()
        WidgetProvider.sendBroadcast(application, longArrayOf(notallyModel.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bật edge-to-edge mode để background full màn hình
        enableEdgeToEdge()
        
        inputMethodManager =
            ContextCompat.getSystemService(baseContext, InputMethodManager::class.java)
        notallyModel.type = type
        initialiseBinding()
        setContentView(binding.root)
        
        // Xử lý window insets để padding đúng cho content
        setupWindowInsets()

        initChangeHistory()
        lifecycleScope.launch {
            val persistedId = savedInstanceState?.getLong("id")
            val selectedId = intent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0L)
            val id = persistedId ?: selectedId
            if (persistedId == null) {
                notallyModel.setState(id)
            }
            if (notallyModel.isNewNote && intent.action == Intent.ACTION_SEND) {
                handleSharedNote()
            } else if (notallyModel.isNewNote) {
                intent.getStringExtra(EXTRA_DISPLAYED_LABEL)?.let {
                    notallyModel.setLabels(listOf(it))
                }
            }

            setupToolbars()
            setupWindowInsets()
            setupListeners()
            setStateFromModel(savedInstanceState)

            configureUI()
            binding.ScrollView.apply {
                visibility = View.VISIBLE
                addFastScroll(this@EditActivity)
            }
        }

        setupActivityResultLaunchers()
    }

    private fun setupActivityResultLaunchers() {
        recordAudioActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    notallyModel.addAudio()
                }
            }
        addImagesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    val clipData = result.data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        notallyModel.addImages(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        notallyModel.addImages(uris)
                    }
                }
            }
        viewImagesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val list =
                        result.data?.let {
                            IntentCompat.getParcelableArrayListExtra(
                                it,
                                ViewImageActivity.EXTRA_DELETED_IMAGES,
                                FileAttachment::class.java,
                            )
                        }
                    if (!list.isNullOrEmpty()) {
                        notallyModel.deleteImages(list)
                    }
                }
            }
        selectLabelsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val list =
                        result.data?.getStringArrayListExtra(
                            SelectLabelsActivity.EXTRA_SELECTED_LABELS
                        )
                    if (list != null && list != notallyModel.labels) {
                        notallyModel.setLabels(list)
                        binding.LabelGroup.bindLabels(
                            notallyModel.labels,
                            notallyModel.textSize,
                            paddingTop = true,
                            colorInt,
                        )
                    }
                }
            }
        playAudioActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val audio =
                        result.data?.let {
                            IntentCompat.getParcelableExtra(
                                it,
                                PlayAudioActivity.EXTRA_AUDIO,
                                Audio::class.java,
                            )
                        }
                    if (audio != null) {
                        notallyModel.deleteAudio(audio)
                    }
                }
            }
        attachFilesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    val clipData = result.data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        notallyModel.addFiles(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        notallyModel.addFiles(uris)
                    }
                }
            }

        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> baseModel.exportSelectedFileToUri(uri) }
                }
            }
        exportNotesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        baseModel.exportNotesToFolder(uri, listOf(notallyModel.getBaseNote()))
                    }
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (
                    grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    startRecordAudioActivity()
                } else handleRejection()
            }
        }
    }

    protected open fun initChangeHistory() {
        changeHistory =
            ChangeHistory().apply {
                canUndo.observe(this@EditActivity) { canUndo -> undo?.isEnabled = canUndo }
                canRedo.observe(this@EditActivity) { canRedo -> redo?.isEnabled = canRedo }
            }
    }

    protected open fun setupToolbars() {
        // Khởi tạo toolbar vẽ (back + undo + redo + draw + search + pin)
        initDrawToolbar()

        // Menu trên toolbar giờ chỉ dùng cho search/prev/next khi đang ở search mode.
        // Mặc định không thêm search/pin ở menu nữa vì đã có icon riêng trong layout.

        search.results.mergeSkipFirst(search.resultPos).observe(this) { (amount, pos) ->
            val hasResults = amount > 0
            binding.SearchResults.text = if (hasResults) "${pos + 1}/$amount" else "0"
            search.nextMenuItem?.isEnabled = hasResults
            search.prevMenuItem?.isEnabled = hasResults
        }

        search.resultPos.observeSkipFirst(this) { pos -> selectSearchResult(pos) }

        binding.EnterSearchKeyword.apply {
            doAfterTextChanged { text ->
                this@EditActivity.search.query = text.toString()
                updateSearchResults(this@EditActivity.search.query)
            }
        }
        initBottomMenu()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Padding cho toolbar để tránh status bar
            binding.Toolbar.updatePadding(top = statusBars.top)
            
            // Điều chỉnh margin bottom cho BottomAppBar để tránh navigation bar
            binding.BottomAppBarLayout.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = navBars.bottom
            }
            
            // Điều chỉnh margin bottom cho FAB AI để nằm trên bottom bar (giống MainFab)
            binding.root.findViewWithTag<CardView>("ai_fab")?.let { fab ->
                (fab.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { params ->
                    val bottomBarHeight = 56.dp // Chiều cao bottom bar
                    val fabMargin = 16.dp // Margin từ bottom bar
                    params.bottomMargin = bottomBarHeight + fabMargin + navBars.bottom
                    fab.layoutParams = params
                }
            }
            
            insets
        }
    }

    protected fun updateSearchResults(query: String) {
        val amountBefore = search.results.value
        val amount = highlightSearchResults(query)
        this.search.results.value = amount
        if (amount > 0) {
            search.resultPos.value =
                when {
                    amountBefore < 1 -> 0
                    search.resultPos.value >= amount -> amount - 1
                    else -> search.resultPos.value
                }
        }
    }

    /**
     * Visibly highlights found search results in the UI.
     *
     * @return amount of search results found
     */
    abstract fun highlightSearchResults(search: String): Int

    abstract fun selectSearchResult(resultPos: Int)

    private var navigationIconBeforeSearch: Drawable? = null

    protected fun startSearch() {
        binding.Toolbar.apply {
            menu.clear()
            search.nextMenuItem =
                menu
                    .add(R.string.previous, R.drawable.arrow_upward) {
                        search.resultPos.apply {
                            if (value > 0) {
                                value -= 1
                            } else {
                                value = search.results.value - 1
                            }
                        }
                    }
                    .setEnabled(false)
            search.prevMenuItem =
                menu
                    .add(R.string.next, R.drawable.arrow_downward) {
                        search.resultPos.apply {
                            if (value < search.results.value - 1) {
                                value += 1
                            } else {
                                value = 0
                            }
                        }
                    }
                    .setEnabled(false)
            setNavigationOnClickListener { endSearch() }
            navigationIconBeforeSearch = navigationIcon
            setNavigationIcon(R.drawable.close)
            setControlsContrastColorForAllViews(colorInt, overwriteBackground = false)
        }
        binding.EnterSearchKeyword.apply {
            visibility = VISIBLE
            requestFocus()
            showKeyboard(this)
        }
        binding.SearchResults.apply {
            text = ""
            visibility = VISIBLE
        }

        // Khi vào search mode, có thể ẩn bớt icon vẽ nếu bạn muốn (tuỳ chỉnh sau)
    }

    protected fun isInSearchMode(): Boolean = binding.EnterSearchKeyword.visibility == VISIBLE

    protected fun endSearch() {
        binding.EnterSearchKeyword.apply {
            visibility = GONE
            setText("")
        }
        binding.SearchResults.apply {
            visibility = GONE
            text = ""
        }
        // Khôi phục lại menu search/pin mặc định
        setupToolbars()
        binding.Toolbar.navigationIcon = navigationIconBeforeSearch
        binding.Toolbar.setControlsContrastColorForAllViews(colorInt, overwriteBackground = false)
    }

    protected open fun initBottomMenu() {
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            addIconButton(R.string.adding_files, R.drawable.add, marginStart = 0) {
                AddBottomSheet(this@EditActivity, colorInt)
                    .show(supportFragmentManager, AddBottomSheet.TAG)
            }
        }
        binding.BottomAppBarCenter.apply {
            removeAllViews()
            undo =
                addIconButton(R.string.undo, R.drawable.undo, marginStart = 2) {
                        try {
                            changeHistory.undo()
                        } catch (e: ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canUndo.value }

            redo =
                addIconButton(R.string.redo, R.drawable.redo, marginStart = 2) {
                        try {
                            changeHistory.redo()
                        } catch (e: ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canRedo.value }
            addIconButton(R.string.draw, R.drawable.ic_pen_pencil, marginStart = 2) {
                openDrawingScreen()
            }
        }
        binding.BottomAppBarRight.apply {
            removeAllViews()
            // Nút "more" được giữ lại ở right
        }
        setBottomAppBarColor(colorInt)
    }
    
    /**
     * Setup FAB AI gradient ở góc dưới bên phải màn hình
     * Dùng CardView để có elevation và corner radius tốt hơn
     */
    protected fun setupAIFloatingButton() {
        // Xóa FAB cũ nếu có (dùng tag để tìm)
        val existingFab = binding.root.findViewWithTag<CardView>("ai_fab")
        existingFab?.let {
            binding.root.removeView(it)
        }
        
        // Tạo CardView để bọc nút AI
        val cardView = CardView(this@EditActivity).apply {
            tag = "ai_fab"
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                56.dp, // Kích thước chuẩn FAB
                56.dp
            ).apply {
                // Đặt ở góc dưới bên phải, trên bottom bar
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                // Margin: 16dp từ cạnh phải, margin bottom sẽ được tính trong setupWindowInsets()
                setMargins(0, 0, 16.dp, 0)
            }
            
            // Corner radius = một nửa chiều rộng để tạo hình tròn hoàn hảo
            radius = 28.dp.toFloat()
            
            // Elevation để nổi bật
            cardElevation = 6.dp.toFloat()
            
            // Prevent card padding
            setContentPadding(0, 0, 0, 0)
            
            // Prevent card background
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Tạo ImageView bên trong CardView
        val aiButton = androidx.appcompat.widget.AppCompatImageView(this@EditActivity).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // Background gradient
            setBackgroundResource(R.drawable.bg_ai_gradient)
            
            // Icon sparkles
            setImageResource(R.drawable.ai_sparkle)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            
            // Scale type
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            
            // Clickable
            isClickable = true
            isFocusable = true
            
            // Ripple effect - dùng TypedValue để lấy drawable
            val typedValue = android.util.TypedValue()
            this@EditActivity.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            foreground = ContextCompat.getDrawable(this@EditActivity, typedValue.resourceId)
            
            // Content description
            contentDescription = this@EditActivity.getString(R.string.ai_assistant)
            
            // Click listener
            setOnClickListener {
                // Animation bounce khi click
                animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                // Gọi method abstract để mở AI menu (EditNoteActivity/EditListActivity sẽ override)
                openAIActionsMenu()
            }
        }
        
        // Thêm ImageView vào CardView
        cardView.addView(aiButton)
        
        // Animation khi view được add (bounce effect)
        cardView.alpha = 0f
        cardView.scaleX = 0f
        cardView.scaleY = 0f
        cardView.post {
            cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                .start()
        }
        
        // Thêm CardView vào CoordinatorLayout (root)
        binding.root.addView(cardView)
    }
    
    /**
     * Setup nút AI gradient đẹp thay thế nút AI đen ở center (deprecated - dùng setupAIFloatingButton thay thế)
     * Dùng CardView để có elevation và corner radius tốt hơn
     */
    @Deprecated("Use setupAIFloatingButton() instead")
    protected fun android.widget.FrameLayout.setupAIGradientButton() {
        // Tạo CardView để bọc nút AI
        val cardView = CardView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                56.dp, // Kích thước chuẩn FAB
                56.dp,
                android.view.Gravity.CENTER
            )
            
            // Corner radius = một nửa chiều rộng để tạo hình tròn hoàn hảo
            radius = 28.dp.toFloat()
            
            // Elevation để nổi bật
            cardElevation = 6.dp.toFloat()
            
            // Prevent card padding
            setContentPadding(0, 0, 0, 0)
            
            // Prevent card background
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Tạo ImageView bên trong CardView
        val aiButton = androidx.appcompat.widget.AppCompatImageView(this@EditActivity).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // Background gradient
            setBackgroundResource(R.drawable.bg_ai_gradient)
            
            // Icon sparkles
            setImageResource(R.drawable.ai_sparkle)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            
            // Scale type
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            
            // Clickable
            isClickable = true
            isFocusable = true
            
            // Ripple effect - dùng TypedValue để lấy drawable
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
            
            // Content description
            contentDescription = context.getString(R.string.ai_assistant)
            
            // Click listener
            setOnClickListener {
                // Animation bounce khi click
                animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                // Gọi method abstract để mở AI menu (EditNoteActivity/EditListActivity sẽ override)
                openAIActionsMenu()
            }
        }
        
        // Thêm ImageView vào CardView
        cardView.addView(aiButton)
        
        // Animation khi view được add (bounce effect)
        cardView.alpha = 0f
        cardView.scaleX = 0f
        cardView.scaleY = 0f
        cardView.post {
            cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                .start()
        }
        
        // Thêm CardView vào FrameLayout
        addView(cardView)
    }
    
    /**
     * Abstract method để mở AI actions menu
     * EditNoteActivity và EditListActivity sẽ override method này
     */
    protected abstract fun openAIActionsMenu()

    protected fun openDrawingScreen() {
        // Load v� merge brushes: default brushes + custom brushes t? SharedPreferences
        val defaultBrushes = ArrayList(drawTools)
        val savedCustomBrushes = appSharePrefs.drawToolBrushes

        // Merge: th�m custom brushes v�o cu?i danh s�ch
        val toolsToShow = ArrayList(defaultBrushes)
        toolsToShow.addAll(savedCustomBrushes)

        // Setup listener cho DrawToolPickerView
        binding.DrawToolPickerView.listener =
            object : DrawToolPickerView.OnItemClickListener {
                override fun onDoneClick() {
                    // ?�ng DrawToolPickerView (?n ?i)
                    hideDrawingToolPicker()
                }

                override fun onItemClick(tool: DrawToolBrush) {
                    // B??C 1: L?u brush ?� ch?n
                    currentDrawTool = tool

                    // B??C 2: T? ??ng hi?n th? divider v� canvas n?u ch?a hi?n th?
                    if (!isDrawingModeActive) {
                        showDrawingArea()
                    }

                    // B??C 3: �p d?ng brush config v�o canvas ngay l?p t?c
                    // ?i?u n�y ??m b?o khi user touch canvas, n� s? v? v?i brush ?� ch?n
                    binding.DrawingCanvas.setBrush(tool)

                    log(
                        "DrawTool selected: ${tool.brush}, color: ${tool.color}, size: ${tool.sliderSize}, opacity: ${tool.opacity}"
                    )
                }

                override fun onSave(tool: DrawToolBrush) {
                    // L?u pen custom v�o SharedPreferences (ch? l?u custom brushes)
                    val currentCustomBrushes = appSharePrefs.drawToolBrushes
                    val existingIndex = currentCustomBrushes.indexOfFirst { it.id == tool.id }

                    // ??m b?o tool l� custom type
                    val customTool = tool.copy(type = DrawToolPenType.CUSTOM)

                    if (existingIndex >= 0) {
                        // Update existing custom brush
                        currentCustomBrushes[existingIndex] = customTool
                    } else {
                        // Add new custom brush
                        currentCustomBrushes.add(customTool)
                    }

                    appSharePrefs.drawToolBrushes = currentCustomBrushes

                    // Reload v� merge l?i brushes ?? hi?n th?
                    val defaultBrushes = ArrayList(drawTools)
                    val updatedTools = ArrayList(defaultBrushes)
                    updatedTools.addAll(currentCustomBrushes)
                    binding.DrawToolPickerView.applyTools(updatedTools)

                    showToast(getString(R.string.saved_to_device))
                }

                override fun onDelete(tool: DrawToolBrush) {
                    // X�a pen custom kh?i SharedPreferences
                    val currentCustomBrushes = appSharePrefs.drawToolBrushes
                    currentCustomBrushes.removeAll { it.id == tool.id }
                    appSharePrefs.drawToolBrushes = currentCustomBrushes

                    // Reload v� merge l?i brushes ?? hi?n th?
                    val defaultBrushes = ArrayList(drawTools)
                    val updatedTools = ArrayList(defaultBrushes)
                    updatedTools.addAll(currentCustomBrushes)
                    binding.DrawToolPickerView.applyTools(updatedTools)

                    showToast(getString(R.string.deleted))
                }

                override fun onPaletteClick() {
                    // L?y brush ?ang ???c ch?n (t? tools list ho?c currentDrawTool)
                    val currentBrush =
                        binding.DrawToolPickerView.tools.firstOrNull { it.isSelected }
                            ?: currentDrawTool
                            ?: run {
                                // N?u kh�ng c� brush n�o ???c ch?n, hi?n th? th�ng b�o
                                showToast("Vui l�ng ch?n b�t v? tr??c")
                                return
                            }

                    // M? color picker (ColorPickerDialog s? t? ??ng l?u/load m�u cu?i c�ng t?
                    // preference)
                    showMoreColor { colorInt ->
                        val newColorHex = colorInt.rawColor()

                        // Update brush v?i m�u m?i
                        val updatedBrush =
                            currentBrush.copy(
                                color = newColorHex,
                                isSelected = true, // Gi? selected state
                            )

                        // Update brush trong tools list (n?u brush c� trong list)
                        val tools = binding.DrawToolPickerView.tools
                        val index = tools.indexOfFirst { it.id == currentBrush.id }
                        if (index >= 0) {
                            tools[index] = updatedBrush
                            binding.DrawToolPickerView.applyTools(tools)
                        }

                        // Update currentDrawTool
                        currentDrawTool = updatedBrush

                        // G?i callback ?? update UI v� canvas
                        // ?i?u n�y s? trigger onItemClick() ?? �p d?ng brush m?i v�o canvas
                        binding.DrawToolPickerView.listener?.onItemClick(updatedBrush)
                    }
                }

                override fun onEyeDropperClick() {
                    // B?t eyedropper tool ?? pick color t? canvas
                    if (!isDrawingModeActive) {
                        showToast("Vui l�ng ch?n b�t v? tr??c")
                        return
                    }

                    enableEyeDropperMode()
                }

                override fun onBackgroundClick() {
                    // M? bottom sheet ch?n background cho canvas
                    val initialColor = Color.WHITE
                    val sheet = BackgroundBottomSheet.newInstance(initialColor)
                    sheet.setListener(
                        object : BackgroundBottomSheet.Listener {
                            override fun onBackgroundSelected(colorInt: Int) {
                                // ??i n?n logic b�n trong canvas
                                binding.DrawingCanvas.setCanvasBackgroundColor(colorInt)
                                // ??i lu�n background view ?? user th?y r�
                                binding.DrawingCanvas.setBackgroundColor(colorInt)
                            }
                        }
                    )
                    sheet.show(supportFragmentManager, "BackgroundBottomSheet")
                }
            }

        // �p d?ng tools v�o DrawToolPickerView
        binding.DrawToolPickerView.applyTools(toolsToShow)

        // Hi?n th? DrawToolPickerView
        showDrawingToolPicker()
    }

    private fun showDrawingToolPicker() {
        binding.DrawToolPickerView.visibility = View.VISIBLE
    }

    private fun hideDrawingToolPicker() {
        binding.DrawToolPickerView.visibility = View.GONE
    }

    private fun initDrawToolbar() {
        // Toolbar luôn hiển thị layout mới (back + undo + redo + draw + search + pin)
        val toolbar = binding.Toolbar
        val ivBack = toolbar.findViewById<ImageView>(R.id.ivBack)
        val ivUndo = toolbar.findViewById<ImageView>(R.id.ivUndo)
        val ivRedo = toolbar.findViewById<ImageView>(R.id.ivRedo)
        val ivDraw = toolbar.findViewById<ImageView>(R.id.ivDraw)
        val ivSearch = toolbar.findViewById<ImageView>(R.id.ivSearch)
        val ivPin = toolbar.findViewById<ImageView>(R.id.ivPin)

        ivBack.setOnClickListener { finish() }

        ivUndo.setOnClickListener {
            try {
                changeHistory.undo()
            } catch (e: ChangeHistory.ChangeHistoryException) {
                application.log(TAG, throwable = e)
            }
        }

        ivRedo.setOnClickListener {
            try {
                changeHistory.redo()
            } catch (e: ChangeHistory.ChangeHistoryException) {
                application.log(TAG, throwable = e)
            }
        }

        ivDraw.setOnClickListener {
            openDrawingScreen()
        }

        // Search icon: mở search như toolbar cũ
        ivSearch.setOnClickListener {
            startSearch()
        }

        // Pin icon: giữ logic pin/unpin gốc
        ivPin.setOnClickListener {
            pin()
        }
    }

    private fun showDrawingArea() {
        // Hi?n th? divider v� canvas
        binding.DrawingDivider.visibility = View.VISIBLE
        binding.DrawingCanvas.visibility = View.VISIBLE
        isDrawingModeActive = true

        // Load strokes t? notallyModel n?u c�
        if (notallyModel.drawingStrokes.isNotEmpty()) {
            binding.DrawingCanvas.loadStrokes(notallyModel.drawingStrokes)
        }

        // Set divider position (v? tr� ???ng ph�n c�ch - relative trong canvas)
        binding.ScrollView.post {
            // T�nh v? tr� divider relative trong canvas
            val dividerTop = binding.DrawingDivider.top
            val canvasTop = binding.DrawingCanvas.top
            val dividerYRelative = (dividerTop - canvasTop).toFloat()
            binding.DrawingCanvas.setDividerY(dividerYRelative)

            // Tính chiều cao màn hình hiện tại (chiều cao ScrollView - toolbar - padding)
            val screenHeight = binding.ScrollView.height
            val toolbarHeight = binding.Toolbar.height
            val availableHeight = screenHeight - toolbarHeight
            
            // Nếu đã có chiều cao đã mở rộng từ lần trước, dùng nó; nếu không thì dùng chiều cao màn hình
            val targetHeight = if (canvasFixedHeight > 0) {
                canvasFixedHeight // Dùng chiều cao đã mở rộng từ lần trước
            } else {
                availableHeight.coerceAtLeast(400) // Tối thiểu 400dp
            }
            
            // Set canvas height cố định
            val layoutParams = binding.DrawingCanvas.layoutParams
            layoutParams.height = targetHeight
            binding.DrawingCanvas.layoutParams = layoutParams
            
            // Disable scroll hoàn toàn của ScrollView khi đang ở draw mode
            // Sử dụng custom NonScrollableNestedScrollView để chặn scroll hoàn toàn
            if (binding.ScrollView is com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView) {
                (binding.ScrollView as com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView).setScrollEnabled(false)
            }

            // Scroll ??n canvas ?? user th?y ngay
            binding.ScrollView.smoothScrollTo(0, binding.DrawingCanvas.top)
        }
    }

    private fun hideDrawingArea() {
        // L?u strokes v�o notallyModel tr??c khi ?n (n?u c� strokes)
        val strokes = binding.DrawingCanvas.getStrokes()
        if (strokes.isNotEmpty()) {
            notallyModel.drawingStrokes = ArrayList(strokes)
        } else {
            // N?u kh�ng c� strokes, x�a strokes c?
            notallyModel.drawingStrokes.clear()
        }

        // QUAN TRỌNG: Clear brush để không thể vẽ được nữa khi đóng bộ vẽ
        binding.DrawingCanvas.setBrush(null)
        currentDrawTool = null

        // Mở rộng canvas x2 chiều cao trước khi ẩn (để có khoảng trắng cho lần vẽ tiếp)
        binding.ScrollView.post {
            // Lấy chiều cao canvas hiện tại (chiều cao đã set khi vào draw mode)
            val currentHeight = binding.DrawingCanvas.height
            if (currentHeight > 0) {
                val layoutParams = binding.DrawingCanvas.layoutParams
                // Mở rộng x2 chiều cao
                val expandedHeight = currentHeight * 2
                layoutParams.height = expandedHeight
                binding.DrawingCanvas.layoutParams = layoutParams
                
                // Lưu lại chiều cao đã mở rộng để lần sau mở lại sẽ dùng chiều cao này
                canvasFixedHeight = expandedHeight
            }
            
            // Enable lại scroll của ScrollView
            if (binding.ScrollView is com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView) {
                (binding.ScrollView as com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView).setScrollEnabled(true)
            }
        }

        // ?n divider v� canvas
        binding.DrawingDivider.visibility = View.GONE
        binding.DrawingCanvas.visibility = View.GONE
        isDrawingModeActive = false
        binding.DrawingCanvas.setZoomModeEnabled(false)
        binding.DrawingCanvas.setEyeDropperMode(false)
        // ?n DrawToolPickerView lu�n
        hideDrawingToolPicker()

    }

    /** B?t ch? ?? eyedropper ?? pick color t? canvas */
    private fun enableEyeDropperMode() {
        binding.DrawingCanvas.setEyeDropperMode(true)
        binding.DrawingCanvas.setOnColorPickedListener { color ->
            // �p d?ng m�u v�o brush hi?n t?i
            currentDrawTool?.let { tool ->
                val colorHex = String.format("#%06X", 0xFFFFFF and color)
                val updatedTool = tool.copy(color = colorHex)
                currentDrawTool = updatedTool
                binding.DrawingCanvas.setBrush(updatedTool)
                showToast("?� ch?n m�u: $colorHex")
            }
        }
        showToast("Tap tr�n canvas ?? ch?n m�u")
    }

    /** Toggle zoom mode cho canvas */
    private fun toggleZoomMode() {
        val isZoomMode = binding.DrawingCanvas.isZoomModeEnabled()
        binding.DrawingCanvas.setZoomModeEnabled(!isZoomMode)

        // Update UI icon n?u c?n (c� th? th�m visual feedback)
        if (!isZoomMode) {
            showToast("?� b?t ch? ?? zoom - D�ng 2 ng�n tay ?? zoom, 1 ng�n ?? pan")
        } else {
            showToast("?� t?t ch? ?? zoom")
        }
    }

    protected fun createFolderActions() =
        when (notallyModel.folder) {
            Folder.NOTES ->
                listOf(
                    Action(R.string.archive, R.drawable.archive) { _ ->
                        archive()
                        true
                    },
                    Action(R.string.delete, R.drawable.delete) { _ ->
                        delete()
                        true
                    },
                )

            Folder.DELETED ->
                listOf(
                    Action(R.string.delete_forever, R.drawable.delete) { _ ->
                        deleteForever()
                        true
                    },
                    Action(R.string.restore, R.drawable.restore) { _ ->
                        restore()
                        true
                    },
                )

            Folder.ARCHIVED ->
                listOf(
                    Action(R.string.delete, R.drawable.delete) { _ ->
                        delete()
                        true
                    },
                    Action(R.string.unarchive, R.drawable.unarchive) { _ ->
                        restore()
                        true
                    },
                )
        }

    abstract fun configureUI()

    open fun setupListeners() {
        binding.EnterTitle.initHistory(changeHistory) { text ->
            notallyModel.title = text.trim().toString()
        }
    }

    open fun setStateFromModel(savedInstanceState: Bundle?) {
        val (date, datePrefixResId) =
            when (preferences.notesSorting.value.sortedBy) {
                NotesSortBy.CREATION_DATE -> Pair(notallyModel.timestamp, R.string.creation_date)
                NotesSortBy.MODIFIED_DATE ->
                    Pair(notallyModel.modifiedTimestamp, R.string.modified_date)
                else -> Pair(null, null)
            }
        val dateFormat =
            if (preferences.applyDateFormatInNoteView.value) {
                preferences.dateFormat.value
            } else DateFormat.ABSOLUTE
        binding.Date.displayFormattedTimestamp(date, dateFormat, datePrefixResId)
        binding.EnterTitle.setText(notallyModel.title)
        binding.LabelGroup.bindLabels(
            notallyModel.labels,
            notallyModel.textSize,
            paddingTop = true,
            colorInt,
        )
        setColor()

        // Load drawing strokes v�o canvas n?u c�
        if (notallyModel.drawingStrokes.isNotEmpty()) {
            binding.DrawingCanvas.loadStrokes(notallyModel.drawingStrokes)
            // Hi?n th? divider v� canvas n?u c� strokes
            binding.DrawingDivider.visibility = View.VISIBLE
            binding.DrawingCanvas.visibility = View.VISIBLE
            isDrawingModeActive = true

            // Set divider position
            binding.ScrollView.post {
                val dividerTop = binding.DrawingDivider.top
                val canvasTop = binding.DrawingCanvas.top
                val dividerYRelative = (dividerTop - canvasTop).toFloat()
                binding.DrawingCanvas.setDividerY(dividerYRelative)
            }
        }
    }

    private fun handleSharedNote() {
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val string = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (string != null) {
            notallyModel.body = Editable.Factory.getInstance().newEditable(string)
        }
        if (title != null) {
            notallyModel.title = title
        }
    }

    @RequiresApi(24)
    override fun recordAudio() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_audio)
                    .setCancelButton()
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
                    }
                    .show()
            } else requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
        } else startRecordAudioActivity()
    }

    private fun startRecordAudioActivity() {
        if (notallyModel.audioRoot != null) {
            val intent = Intent(this, RecordAudioActivity::class.java)
            recordAudioActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_audio)
    }

    private fun handleRejection() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.to_record_audio)
            .setCancelButton()
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
            .show()
    }

    override fun addImages() {
        if (notallyModel.imageRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT)
                    .apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    .wrapWithChooser(this)
            addImagesActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_images)
    }

    override fun attachFiles() {
        if (notallyModel.filesRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT)
                    .apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    .wrapWithChooser(this)
            attachFilesActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_files)
    }

    override fun changeColor() {
        lifecycleScope.launch {
            val colors =
                withContext(Dispatchers.IO) {
                        NotallyDatabase.getDatabase(this@EditActivity, observePreferences = false)
                            .value
                            .getBaseNoteDao()
                            .getAllColors()
                    }
                    .toMutableList()
            if (colors.none { it == notallyModel.color }) {
                colors.add(notallyModel.color)
            }
            showColorSelectDialog(
                colors,
                notallyModel.color,
                colorInt.isLightColor(),
                { selectedColor, oldColor ->
                    if (oldColor != null) {
                        baseModel.changeColor(oldColor, selectedColor)
                    }
                    notallyModel.color = selectedColor
                    setColor()
                },
            ) { colorToDelete, newColor ->
                baseModel.changeColor(colorToDelete, newColor)
                if (colorToDelete == notallyModel.color) {
                    notallyModel.color = newColor
                    setColor()
                }
            }
        }
    }

    override fun changeReminders() {
        val intent = Intent(this, RemindersActivity::class.java)
        intent.putExtra(RemindersActivity.NOTE_ID, notallyModel.id)
        startActivity(intent)
    }

    override fun changeLabels() {
        val intent = Intent(this, SelectLabelsActivity::class.java)
        intent.putStringArrayListExtra(EXTRA_SELECTED_LABELS, notallyModel.labels)
        selectLabelsActivityResultLauncher.launch(intent)
    }

    override fun share() {
        val body =
            when (type) {
                Type.NOTE -> notallyModel.body
                Type.LIST -> notallyModel.items.toMutableList().toText()
            }
        this.shareNote(notallyModel.title, body)
    }

    override fun export(mimeType: ExportMimeType) {
        exportNotes(
            mimeType,
            listOf(notallyModel.getBaseNote()),
            exportFileActivityResultLauncher,
            exportNotesActivityResultLauncher,
        )
    }

    private fun delete() {
        moveNote(Folder.DELETED)
    }

    private fun restore() {
        moveNote(Folder.NOTES)
    }

    private fun archive() {
        moveNote(Folder.ARCHIVED)
    }

    private fun moveNote(toFolder: Folder) {
        val resultIntent =
            Intent().apply {
                putExtra(EXTRA_NOTE_ID, notallyModel.id)
                putExtra(EXTRA_FOLDER_FROM, notallyModel.folder.name)
                putExtra(EXTRA_FOLDER_TO, toFolder.name)
            }
        notallyModel.folder = toFolder
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_note_forever)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    notallyModel.deleteBaseNote()
                    super.finish()
                }
            }
            .setCancelButton()
            .show()
    }

    fun pin() {
        notallyModel.pinned = !notallyModel.pinned
        bindPinned()
    }

    private fun setupImages() {
        val imageAdapter =
            PreviewImageAdapter(notallyModel.imageRoot) { position ->
                val intent =
                    Intent(this, ViewImageActivity::class.java).apply {
                        putExtra(ViewImageActivity.EXTRA_POSITION, position)
                        putExtra(EXTRA_SELECTED_BASE_NOTE, notallyModel.id)
                    }
                viewImagesActivityResultLauncher.launch(intent)
            }

        imageAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    binding.ImagePreview.scrollToPosition(positionStart)
                    binding.ImagePreviewPosition.text =
                        "${positionStart + 1}/${imageAdapter.itemCount}"
                }
            }
        )
        binding.ImagePreview.apply {
            setHasFixedSize(true)
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(this@EditActivity, RecyclerView.HORIZONTAL, false)

            val pagerSnapHelper = PagerSnapHelper()
            pagerSnapHelper.attachToRecyclerView(this)
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val snappedView = pagerSnapHelper.findSnapView(layoutManager)
                            if (snappedView != null) {
                                val position = recyclerView.getChildAdapterPosition(snappedView)
                                binding.ImagePreviewPosition.text =
                                    "${position + 1}/${imageAdapter.itemCount}"
                            }
                        }
                    }
                }
            )
        }

        notallyModel.images.observe(this) { list ->
            imageAdapter.submitList(list)
            binding.ImagePreview.isVisible = list.isNotEmpty()
            binding.ImagePreviewPosition.isVisible = list.size > 1
        }
    }

    private fun setupFiles() {
        fileAdapter =
            PreviewFileAdapter({ fileAttachment ->
                if (notallyModel.filesRoot == null) {
                    return@PreviewFileAdapter
                }
                val intent =
                    Intent(Intent.ACTION_VIEW)
                        .apply {
                            val file = File(notallyModel.filesRoot, fileAttachment.localName)
                            val uri = this@EditActivity.getUriForFile(file)
                            setDataAndType(uri, fileAttachment.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        .wrapWithChooser(this@EditActivity)
                startActivity(intent)
            }) { fileAttachment ->
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.delete_file, fileAttachment.originalName))
                    .setCancelButton()
                    .setPositiveButton(R.string.delete) { _, _ ->
                        notallyModel.deleteFiles(arrayListOf(fileAttachment))
                    }
                    .show()
                return@PreviewFileAdapter true
            }

        binding.FilesPreview.apply {
            setHasFixedSize(true)
            adapter = fileAdapter
            layoutManager =
                LinearLayoutManager(this@EditActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        notallyModel.files.observe(this) { list ->
            fileAdapter.submitList(list)
            val visible = list.isNotEmpty()
            binding.FilesPreview.apply {
                isVisible = visible
                if (visible) {
                    post {
                        scrollToPosition(fileAdapter.itemCount)
                        requestLayout()
                    }
                }
            }
        }
    }

    private fun displayFileErrors(errors: List<FileError>) {
        val recyclerView =
            RecyclerView(this).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                adapter = ErrorAdapter(errors)
                layoutManager = LinearLayoutManager(this@EditActivity)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    scrollIndicators = View.SCROLL_INDICATOR_TOP or View.SCROLL_INDICATOR_BOTTOM
                }
            }

        val message =
            if (errors.isNotEmpty() && errors[0].fileType == NotallyModel.FileType.IMAGE) {
                R.plurals.cant_add_images
            } else {
                R.plurals.cant_add_files
            }
        val title = getQuantityString(message, errors.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(recyclerView)
            .setCancelButton()
            .setCancelable(false)
            .show()
    }

    private fun setupAudios() {
        audioAdapter = AudioAdapter { position: Int ->
            if (position != -1) {
                val audio = notallyModel.audios.value[position]
                val intent = Intent(this, PlayAudioActivity::class.java)
                intent.putExtra(PlayAudioActivity.EXTRA_AUDIO, audio)
                playAudioActivityResultLauncher.launch(intent)
            }
        }
        binding.AudioRecyclerView.adapter = audioAdapter

        notallyModel.audios.observe(this) { list ->
            audioAdapter.submitList(list)
            binding.AudioHeader.isVisible = list.isNotEmpty()
            binding.AudioRecyclerView.isVisible = list.isNotEmpty()
        }
    }

    protected open fun setColor() {
        colorInt = extractColor(notallyModel.color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = colorInt
            window.navigationBarColor = colorInt
            window.setLightStatusAndNavBar(colorInt.isLightColor())
        }
        binding.apply {
            ScrollView.apply {
                setBackgroundColor(colorInt)
                setControlsContrastColorForAllViews(colorInt)
            }
            root.setBackgroundColor(colorInt)
            RecyclerView.setBackgroundColor(colorInt)
            Toolbar.backgroundTintList = ColorStateList.valueOf(colorInt)
            Toolbar.setControlsContrastColorForAllViews(colorInt)
        }
        setBottomAppBarColor(colorInt)
        fileAdapter.setColor(colorInt)
        audioAdapter.setColor(colorInt)
    }

    protected fun setBottomAppBarColor(@ColorInt color: Int) {
        binding.apply {
            BottomAppBar.setBackgroundColor(color)
            BottomAppBar.setControlsContrastColorForAllViews(color)
            BottomAppBarLayout.backgroundTint = ColorStateList.valueOf(color)
        }
    }

    private fun initialiseBinding() {
        binding = ActivityEditBinding.inflate(layoutInflater)
        when (type) {
            Type.NOTE -> {
                binding.AddItem.visibility = GONE
                binding.RecyclerView.visibility = GONE
            }
            Type.LIST -> {
                binding.EnterBody.visibility = GONE
            }
        }

        val title = notallyModel.textSize.editTitleSize
        val date = notallyModel.textSize.displayBodySize
        val body = notallyModel.textSize.editBodySize

        binding.EnterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.EnterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        setupImages()
        setupFiles()
        setupAudios()
        notallyModel.addingFiles.setupProgressDialog(this, R.string.adding_files)
        notallyModel.eventBus.observe(this) { event ->
            event.handle { errors -> displayFileErrors(errors) }
        }

        binding.root.isSaveFromParentEnabled = false
    }

    private fun bindPinned() {
        val icon: Int =
        if (notallyModel.pinned) {
                R.drawable.unpin
        } else {
                R.drawable.pin
            }
        // Cập nhật icon cho ivPin trên toolbar mới
        val ivPin = binding.Toolbar.findViewById<ImageView>(R.id.ivPin)
        ivPin.setImageResource(icon)
    }

    data class Search(
        var query: String = "",
        var prevMenuItem: MenuItem? = null,
        var nextMenuItem: MenuItem? = null,
        var resultPos: NotNullLiveData<Int> = NotNullLiveData(-1),
        var results: NotNullLiveData<Int> = NotNullLiveData(-1),
    )

    companion object {
        private const val TAG = "EditActivity"
        private const val REQUEST_AUDIO_PERMISSION = 36

        const val EXTRA_SELECTED_BASE_NOTE = "notallyx.intent.extra.SELECTED_BASE_NOTE"
        const val EXTRA_NOTE_ID = "notallyx.intent.extra.NOTE_ID"
        const val EXTRA_FOLDER_FROM = "notallyx.intent.extra.FOLDER_FROM"
        const val EXTRA_FOLDER_TO = "notallyx.intent.extra.FOLDER_TO"
    }
}
