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
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.VISIBLE
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addFastScroll
import com.philkes.notallyx.presentation.bindLabels
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.dp
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
import com.philkes.notallyx.presentation.view.note.action.MoreActions
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
import kotlin.math.max
import kotlin.math.min
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
    private lateinit var fullScreenDrawingActivityResultLauncher: ActivityResultLauncher<Intent>

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
    private var canvasFixedHeight: Int = 0

    protected var colorInt: Int = -1
    protected var inputMethodManager: InputMethodManager? = null

    // Store animations to cancel on destroy
    private val activeAnimators = mutableListOf<android.animation.Animator>()
    private val postRunnables = mutableListOf<Runnable>()

    override fun onDestroy() {
        // Cancel all active animations
        activeAnimators.forEach { animator ->
            try {
                if (animator.isRunning) {
                    animator.cancel()
                }
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
        }
        activeAnimators.clear()

        // Cancel all post operations
        try {
            postRunnables.forEach { runnable -> binding.root.removeCallbacks(runnable) }
        } catch (e: Exception) {
            // Ignore errors if binding is already destroyed
        }
        postRunnables.clear()

        // Clear references
        try {
            binding.DrawingCanvas.setBrush(null)
            binding.DrawingCanvas.setOnColorPickedListener {}
        } catch (e: Exception) {
            // Ignore errors if view is already destroyed
        }

        super.onDestroy()
    }

    override fun finish() {
        if (isFinishing || isDestroyed) return

        lifecycleScope.launch(Dispatchers.Main) {
            if (isFinishing || isDestroyed) return@launch

            try {
                if (notallyModel.isEmpty()) {
                    notallyModel.deleteBaseNote(checkAutoSave = false)
                } else if (notallyModel.isModified()) {
                    saveNote()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!isDestroyed) {
                    super.finish()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_FULL_SCREEN_DRAWING && resultCode == RESULT_OK) {
            val strokesJson = data?.getStringExtra(FullScreenDrawingActivity.RESULT_STROKES)
            handleDrawingStrokesResult(strokesJson)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure background is reapplied when returning to this screen
        applySavedBackgroundToCanvas()
    }

    override fun onPause() {
        super.onPause()
        // Persist current background state for this note
        persistCurrentBackground()
    }

    private fun handleDrawingStrokesResult(strokesJson: String?) {
        if (strokesJson == null) return

        try {
            val gson = Gson()
            val strokesType =
                object :
                        TypeToken<
                            List<com.philkes.notallyx.draw.ui.newdraw.view.canvas.DrawingStroke>
                        >() {}
                    .type
            val strokes =
                gson.fromJson<List<com.philkes.notallyx.draw.ui.newdraw.view.canvas.DrawingStroke>>(
                    strokesJson,
                    strokesType,
                )
            if (strokes != null) {
                notallyModel.drawingStrokes = ArrayList(strokes)

                if (!isDrawingModeActive) {
                    showDrawingArea()
                }
                binding.DrawingCanvas.loadStrokes(strokes)
                notallyModel.modifiedTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error loading drawing")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", notallyModel.id)
        if (notallyModel.isModified()) {
            lifecycleScope.launch {
                if (!isDestroyed && !isFinishing) {
                    saveNote()
                }
            }
        }
    }

    open suspend fun saveNote() {
        if (isDestroyed || isFinishing) return

        // L?u strokes v?o notallyModel tr??c khi save
        if (isDrawingModeActive) {
            try {
                val strokes = binding.DrawingCanvas.getStrokes()
                notallyModel.drawingStrokes = ArrayList(strokes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        notallyModel.modifiedTimestamp = System.currentTimeMillis()
        notallyModel.saveNote()
        // Persist background after note is saved (ensures correct noteId key)
        saveDrawingBackgroundPreference(
            notallyModel.drawingBackgroundColor,
            notallyModel.drawingBackgroundDrawableResId,
        )
        WidgetProvider.sendBroadcast(application, longArrayOf(notallyModel.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        inputMethodManager =
            ContextCompat.getSystemService(baseContext, InputMethodManager::class.java)
        notallyModel.type = type
        initialiseBinding()
        setContentView(binding.root)

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
            applySavedBackgroundToCanvas()
            setupCanvasStrokeListener()

            configureUI()
            binding.ScrollView.apply {
                visibility = View.VISIBLE
                addFastScroll(this@EditActivity)
            }

            setupInlineAIButton()
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
        fullScreenDrawingActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val strokesJson =
                        result.data?.getStringExtra(FullScreenDrawingActivity.RESULT_STROKES)
                    handleDrawingStrokesResult(strokesJson)
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
                canUndo.observe(this@EditActivity) { canUndo ->
                    if (!isDrawingModeActive) {
                        undo?.isEnabled = canUndo
                        undo?.alpha = if (canUndo) 1f else 0.5f
                    }
                }
                canRedo.observe(this@EditActivity) { canRedo ->
                    if (!isDrawingModeActive) {
                        redo?.isEnabled = canRedo
                        redo?.alpha = if (canRedo) 1f else 0.5f
                    }
                }
            }
    }

    protected open fun setupToolbars() {
        initDrawToolbar()

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

            binding.Toolbar.updatePadding(top = statusBars.top)

            binding.BottomAppBarLayout.updateLayoutParams<
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            > {
                bottomMargin = navBars.bottom
            }

            binding.JellyFabComposeView.updateLayoutParams<
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            > {
                val fabMargin = 16.dp
                bottomMargin = fabMargin + navBars.bottom
            }

            binding.root.findViewWithTag<CardView>("ai_fab")?.let { fab ->
                (fab.layoutParams
                        as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)
                    ?.let { params ->
                        val fabSize = 56.dp
                        val fabSpacing = 16.dp
                        params.bottomMargin = fabSize + fabSpacing + navBars.bottom
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
        setupToolbars()
        binding.Toolbar.navigationIcon = navigationIconBeforeSearch
        binding.Toolbar.setControlsContrastColorForAllViews(colorInt, overwriteBackground = false)
    }

    protected open fun initBottomMenu() {
        binding.BottomAppBarLayout.visibility = View.GONE
        // AI FAB thay b?ng n�t inline tr�n header, kh�ng t?o FAB n?i n?a
        setupJellyFab()
    }

    protected fun setupAIFloatingButton() {
        val existingFab = binding.root.findViewWithTag<CardView>("ai_fab")
        existingFab?.let { binding.root.removeView(it) }

        val cardView =
            CardView(this@EditActivity).apply {
                tag = "ai_fab"
                layoutParams =
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(56.dp, 56.dp)
                        .apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                            setMargins(0, 0, 0, 0)
                        }

                radius = 28.dp.toFloat()
                cardElevation = 6.dp.toFloat()
                setContentPadding(0, 0, 0, 0)
                setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

        val aiButton =
            androidx.appcompat.widget.AppCompatImageView(this@EditActivity).apply {
                layoutParams =
                    android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    )

                setBackgroundResource(R.drawable.bg_ai_gradient)
                setImageResource(R.drawable.ai_sparkle)
                imageTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
                isClickable = true
                isFocusable = true

                val typedValue = android.util.TypedValue()
                this@EditActivity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    typedValue,
                    true,
                )
                foreground = ContextCompat.getDrawable(this@EditActivity, typedValue.resourceId)

                contentDescription = this@EditActivity.getString(R.string.ai_assistant)

                setOnClickListener {
                    animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
                        .start()

                    openAIActionsMenu()
                }
            }

        cardView.addView(aiButton)

        cardView.alpha = 0f
        cardView.scaleX = 0f
        cardView.scaleY = 0f
        val postRunnable = Runnable {
            if (isDestroyed || isFinishing) return@Runnable
            cardView
                .animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                .start()
        }
        postRunnables.add(postRunnable)
        cardView.post(postRunnable)

        binding.root.addView(cardView)
    }

    protected open fun openAddItemMenu() {
        // Override trong EditNoteActivity/EditListActivity
    }

    protected open fun openTextFormattingMenu() {
        // Override trong EditNoteActivity
    }

    protected open fun openAddFilesMenu() {
        attachFiles()
    }

    protected open fun linkNote() {}

    private fun setupJellyFab() {
        fun getAiFabView(): CardView? = null

        binding.JellyFabComposeView.setContent {
            androidx.compose.material3.MaterialTheme {
                com.philkes.notallyx.presentation.compose.JellyFabMenu(
                    onDrawClick = {
                        try {
                            openFullScreenDrawing()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onTextFormatClick = { openTextFormattingMenu() },
                    onMoreClick = {},
                    onAddFilesClick = { attachFiles() },
                    onAddImagesClick = { addImages() },
                    onRecordAudioClick = { recordAudio() },
                    onLinkNoteClick =
                        if (
                            this is com.philkes.notallyx.presentation.activity.note.EditNoteActivity
                        ) {
                            { linkNote() }
                        } else null,
                    showLinkNote =
                        this is com.philkes.notallyx.presentation.activity.note.EditNoteActivity,
                    onMainFabClick = {},
                )
            }
        }
    }

    private fun setupInlineAIButton() {
        binding.InlineAiButton?.apply {
            // Re-apply styling to ensure gradient + white icon
            setBackgroundResource(R.drawable.bg_ai_gradient)
            setImageResource(R.drawable.ai_sparkle)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(80)
                    .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                    .start()
                openAIActionsMenu()
            }
        }
    }

    private fun hideAIFab(aiFabView: CardView?) {
        if (isDestroyed || isFinishing) return
        aiFabView?.let {
            it.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (!isDestroyed && !isFinishing) {
                        it.visibility = View.INVISIBLE
                    }
                }
                .start()
        }
    }

    private fun showAIFab(aiFabView: CardView?) {
        if (isDestroyed || isFinishing) return
        aiFabView?.let {
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(200).start()
        }
    }

    protected abstract fun openAIActionsMenu()

    protected fun openDrawingScreen() {
        // Load v? merge brushes: default brushes + custom brushes t? SharedPreferences
        val defaultBrushes = ArrayList(drawTools)
        val savedCustomBrushes = appSharePrefs.drawToolBrushes

        // Merge: th?m custom brushes v?o cu?i danh s?ch
        val toolsToShow = ArrayList(defaultBrushes)
        toolsToShow.addAll(savedCustomBrushes)

        // Setup listener cho DrawToolPickerView
        binding.DrawToolPickerView.listener =
            object : DrawToolPickerView.OnItemClickListener {
                override fun onDoneClick() {
                    // ??ng DrawToolPickerView (?n ?i)
                    hideDrawingToolPicker()
                }

                override fun onItemClick(tool: DrawToolBrush) {
                    // B??C 1: L?u brush ?? ch?n
                    currentDrawTool = tool

                    // B??C 2: T? ??ng hi?n th? divider v? canvas n?u ch?a hi?n th?
                    if (!isDrawingModeActive) {
                        showDrawingArea()
                    }

                    // B??C 3: ?p d?ng brush config v?o canvas ngay l?p t?c
                    // ?i?u n?y ??m b?o khi user touch canvas, n? s? v? v?i brush ?? ch?n
                    binding.DrawingCanvas.setBrush(tool)

                    log(
                        "DrawTool selected: ${tool.brush}, color: ${tool.color}, size: ${tool.sliderSize}, opacity: ${tool.opacity}"
                    )
                }

                override fun onSave(tool: DrawToolBrush) {
                    // L?u pen custom v?o SharedPreferences (ch? l?u custom brushes)
                    val currentCustomBrushes = appSharePrefs.drawToolBrushes
                    val existingIndex = currentCustomBrushes.indexOfFirst { it.id == tool.id }

                    // ??m b?o tool l? custom type
                    val customTool = tool.copy(type = DrawToolPenType.CUSTOM)

                    if (existingIndex >= 0) {
                        // Update existing custom brush
                        currentCustomBrushes[existingIndex] = customTool
                    } else {
                        // Add new custom brush
                        currentCustomBrushes.add(customTool)
                    }

                    appSharePrefs.drawToolBrushes = currentCustomBrushes

                    // Reload v? merge l?i brushes ?? hi?n th?
                    val defaultBrushes = ArrayList(drawTools)
                    val updatedTools = ArrayList(defaultBrushes)
                    updatedTools.addAll(currentCustomBrushes)
                    binding.DrawToolPickerView.applyTools(updatedTools)

                    showToast(getString(R.string.saved_to_device))
                }

                override fun onDelete(tool: DrawToolBrush) {
                    // X?a pen custom kh?i SharedPreferences
                    val currentCustomBrushes = appSharePrefs.drawToolBrushes
                    currentCustomBrushes.removeAll { it.id == tool.id }
                    appSharePrefs.drawToolBrushes = currentCustomBrushes

                    // Reload v? merge l?i brushes ?? hi?n th?
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
                                showToast("Please select a brush first")
                                return
                            }

                    // M? color picker (ColorPickerDialog s? t? ??ng l?u/load m?u cu?i c?ng t?
                    // preference)
                    showMoreColor { colorInt ->
                        val newColorHex = colorInt.rawColor()

                        // Update brush v?i m?u m?i
                        val updatedBrush =
                            currentBrush.copy(
                                color = newColorHex,
                                isSelected = true, // Gi? selected state
                            )

                        // Update brush trong tools list (n?u brush c? trong list)
                        val tools = binding.DrawToolPickerView.tools
                        val index = tools.indexOfFirst { it.id == currentBrush.id }
                        if (index >= 0) {
                            tools[index] = updatedBrush
                            binding.DrawToolPickerView.applyTools(tools)
                        }

                        // Update currentDrawTool
                        currentDrawTool = updatedBrush

                        // G?i callback ?? update UI v? canvas
                        // ?i?u n?y s? trigger onItemClick() ?? ?p d?ng brush m?i v?o canvas
                        binding.DrawToolPickerView.listener?.onItemClick(updatedBrush)
                    }
                }

                override fun onEyeDropperClick() {
                    if (!isDrawingModeActive) {
                        showToast("Please select a brush first")
                        return
                    }

                    enableEyeDropperMode()
                }

                override fun onBackgroundClick() {
                    // M? bottom sheet ch?n background cho canvas
                    val initialColor = notallyModel.drawingBackgroundColor
                    val sheet = BackgroundBottomSheet.newInstance(initialColor)
                    sheet.setListener(
                        object : BackgroundBottomSheet.Listener {
                            override fun onBackgroundSelected(colorInt: Int, drawableResId: Int?) {
                                applyAndPersistBackground(colorInt, drawableResId)
                            }
                        }
                    )
                    sheet.show(supportFragmentManager, "BackgroundBottomSheet")
                }
            }

        // ?p d?ng tools v?o DrawToolPickerView
        binding.DrawToolPickerView.applyTools(toolsToShow)

        // Hi?n th? DrawToolPickerView
        showDrawingToolPicker()
    }

    /**
     * Hi?n th? canvas v� bottom bar ngay trong EditActivity Toolbar gi? nguy�n, ch? show canvas v�
     * bottom bar v?i animation
     */
    private fun openFullScreenDrawing() {
        if (!isDrawingModeActive) {
            showDrawingArea()
        }

        if (
            notallyModel.drawingStrokes.isNotEmpty() && binding.DrawingCanvas.getStrokes().isEmpty()
        ) {
            binding.DrawingCanvas.loadStrokes(notallyModel.drawingStrokes)
        }

        if (binding.DrawToolPickerView.listener == null) {
            val drawTools = DrawToolData.getDefault()
            val defaultBrushes = ArrayList(drawTools)
            val savedCustomBrushes = appSharePrefs.drawToolBrushes

            val toolsToShow = ArrayList(defaultBrushes)
            toolsToShow.addAll(savedCustomBrushes)

            binding.DrawToolPickerView.listener =
                object : DrawToolPickerView.OnItemClickListener {
                    override fun onDoneClick() {
                        hideDrawingArea()
                    }

                    override fun onItemClick(tool: DrawToolBrush) {
                        currentDrawTool = tool

                        if (!isDrawingModeActive) {
                            showDrawingArea()
                        }

                        binding.DrawingCanvas.setBrush(tool)
                    }

                    override fun onSave(tool: DrawToolBrush) {
                        val currentCustomBrushes = appSharePrefs.drawToolBrushes
                        val existingIndex = currentCustomBrushes.indexOfFirst { it.id == tool.id }
                        val customTool = tool.copy(type = DrawToolPenType.CUSTOM)

                        if (existingIndex >= 0) {
                            currentCustomBrushes[existingIndex] = customTool
                        } else {
                            currentCustomBrushes.add(customTool)
                        }

                        appSharePrefs.drawToolBrushes = currentCustomBrushes

                        val defaultBrushes = ArrayList(drawTools)
                        val updatedTools = ArrayList(defaultBrushes)
                        updatedTools.addAll(currentCustomBrushes)
                        binding.DrawToolPickerView.applyTools(updatedTools)

                        showToast(getString(R.string.saved_to_device))
                    }

                    override fun onDelete(tool: DrawToolBrush) {
                        val currentCustomBrushes = appSharePrefs.drawToolBrushes
                        currentCustomBrushes.removeAll { it.id == tool.id }
                        appSharePrefs.drawToolBrushes = currentCustomBrushes

                        val defaultBrushes = ArrayList(drawTools)
                        val updatedTools = ArrayList(defaultBrushes)
                        updatedTools.addAll(currentCustomBrushes)
                        binding.DrawToolPickerView.applyTools(updatedTools)

                        showToast(getString(R.string.deleted))
                    }

                    override fun onPaletteClick() {
                        val currentBrush =
                            binding.DrawToolPickerView.tools.firstOrNull { it.isSelected }
                                ?: currentDrawTool
                                ?: run {
                                    showToast("Please select a brush first")
                                    return
                                }

                        showMoreColor { colorInt ->
                            val newColorHex = colorInt.rawColor()
                            val updatedBrush =
                                currentBrush.copy(color = newColorHex, isSelected = true)

                            val tools = binding.DrawToolPickerView.tools
                            val index = tools.indexOfFirst { it.id == currentBrush.id }
                            if (index >= 0) {
                                tools[index] = updatedBrush
                                binding.DrawToolPickerView.applyTools(tools)
                            }

                            currentDrawTool = updatedBrush
                            binding.DrawToolPickerView.listener?.onItemClick(updatedBrush)
                        }
                    }

                    override fun onEyeDropperClick() {
                        if (!isDrawingModeActive) {
                            showToast("Please select a brush first")
                            return
                        }
                        enableEyeDropperMode()
                    }

                    override fun onBackgroundClick() {
                        val initialColor = notallyModel.drawingBackgroundColor
                        val sheet = BackgroundBottomSheet.newInstance(initialColor)
                        sheet.setListener(
                            object : BackgroundBottomSheet.Listener {
                                override fun onBackgroundSelected(
                                    colorInt: Int,
                                    drawableResId: Int?,
                                ) {
                                    applyAndPersistBackground(colorInt, drawableResId)
                                }
                            }
                        )
                        sheet.show(supportFragmentManager, "BackgroundBottomSheet")
                    }
                }

            binding.DrawToolPickerView.applyTools(toolsToShow)
        }

        val postRunnable = Runnable {
            if (isDestroyed || isFinishing) return@Runnable

            val marginPx = dpToPx(20)
            val bottomBarHeight = measureBottomBarHeight()
            val availableHeight = computeAvailableCanvasHeight(bottomBarHeight, marginPx)
            val contentHeight =
                computeContentHeightFromStrokes(notallyModel.drawingStrokes, dpToPx(50))
            val targetHeight = max(availableHeight, contentHeight)
            applyCanvasLayout(targetHeight, marginPx)

            binding.JellyFabComposeView.visibility = View.GONE
            binding.root.findViewWithTag<CardView>("ai_fab")?.let { aiFab -> hideAIFab(aiFab) }

            val scrollRunnable = Runnable {
                if (!isDestroyed && !isFinishing) {
                    binding.ScrollView.smoothScrollTo(0, binding.DrawingCanvas.top)
                }
            }
            postRunnables.add(scrollRunnable)
            binding.ScrollView.post(scrollRunnable)

            startDrawingModeAnimations()
        }
        postRunnables.add(postRunnable)
        binding.root.post(postRunnable)
    }

    /** Animation cho canvas v� bottom bar khi m? drawing mode */
    private fun startDrawingModeAnimations() {
        if (isDestroyed || isFinishing) return

        binding.DrawingCanvas.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            visibility = View.VISIBLE

            android.animation.ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                duration = 1500
                interpolator = android.view.animation.DecelerateInterpolator()
                activeAnimators.add(this)
                start()
            }
            android.animation.ObjectAnimator.ofFloat(this, "scaleX", 0.8f, 1f).apply {
                duration = 2500
                interpolator = android.view.animation.DecelerateInterpolator()
                activeAnimators.add(this)
                start()
            }
            android.animation.ObjectAnimator.ofFloat(this, "scaleY", 0.8f, 1f).apply {
                duration = 2500
                interpolator = android.view.animation.DecelerateInterpolator()
                activeAnimators.add(this)
                start()
            }
        }

        val postRunnable = Runnable {
            if (isDestroyed || isFinishing) return@Runnable

            val toolbarHeight = binding.DrawToolPickerView.height.toFloat()
            binding.DrawToolPickerView.apply {
                translationY = toolbarHeight
                alpha = 0f
                visibility = View.VISIBLE

                android.animation.ObjectAnimator.ofFloat(this, "translationY", toolbarHeight, 0f)
                    .apply {
                        duration = 1500
                        interpolator = android.view.animation.AnticipateOvershootInterpolator(2.0f)
                        activeAnimators.add(this)
                        start()
                    }
                android.animation.ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 1500
                    interpolator = android.view.animation.DecelerateInterpolator()
                    activeAnimators.add(this)
                    start()
                }
            }
        }
        postRunnables.add(postRunnable)
        binding.DrawToolPickerView.post(postRunnable)
    }

    private fun showDrawingToolPicker() {
        binding.DrawToolPickerView.visibility = View.VISIBLE
    }

    private fun hideDrawingToolPicker() {
        binding.DrawToolPickerView.visibility = View.GONE
        showFABs()
    }

    private fun showFABs() {
        binding.JellyFabComposeView.visibility = View.VISIBLE
        val aiFabView = binding.root.findViewWithTag<CardView>("ai_fab")
        aiFabView?.let { aiFab -> showAIFab(aiFab) }
    }

    private fun setupCanvasStrokeListener() {
        binding.DrawingCanvas.setOnStrokesChangedListener {
            if (isDrawingModeActive) {
                val ivUndo = binding.Toolbar.findViewById<View>(R.id.ivUndo) as? ImageView
                val ivRedo = binding.Toolbar.findViewById<View>(R.id.ivRedo) as? ImageView
                if (ivUndo != null && ivRedo != null) {
                    updateDrawingUndoRedoButtons(ivUndo, ivRedo)
                }
            }
        }
    }

    private fun updateDrawingUndoRedoButtons(ivUndo: ImageView, ivRedo: ImageView) {
        val canUndoDrawing = binding.DrawingCanvas.canUndo()
        val canRedoDrawing = binding.DrawingCanvas.canRedo()
        ivUndo.isEnabled = canUndoDrawing
        ivUndo.alpha = if (canUndoDrawing) 1f else 0.5f
        ivRedo.isEnabled = canRedoDrawing
        ivRedo.alpha = if (canRedoDrawing) 1f else 0.5f
    }

    private fun initDrawToolbar() {
        val toolbar = binding.Toolbar
        val ivBack = toolbar.findViewById<View>(R.id.ivBack)
        val ivUndo = toolbar.findViewById<View>(R.id.ivUndo)
        val ivRedo = toolbar.findViewById<View>(R.id.ivRedo)
        val ivPin = toolbar.findViewById<View>(R.id.ivPin)
        val ivMore = toolbar.findViewById<View>(R.id.ivMore)

        ivBack.setOnClickListener { finish() }

        ivUndo.setOnClickListener {
            if (isDrawingModeActive) {
                if (binding.DrawingCanvas.undo()) {
                    updateDrawingUndoRedoButtons(ivUndo as ImageView, ivRedo as ImageView)
                }
            } else {
                try {
                    changeHistory.undo()
                } catch (
                    e: com.philkes.notallyx.utils.changehistory.ChangeHistory.ChangeHistoryException) {
                    application.log(TAG, throwable = e)
                }
            }
        }
        changeHistory.canUndo.observe(this) { canUndo ->
            if (isDrawingModeActive) {
                updateDrawingUndoRedoButtons(ivUndo as ImageView, ivRedo as ImageView)
            } else {
                ivUndo.isEnabled = canUndo
                ivUndo.alpha = if (canUndo) 1f else 0.5f
            }
        }

        ivRedo.setOnClickListener {
            if (isDrawingModeActive) {
                if (binding.DrawingCanvas.redo()) {
                    updateDrawingUndoRedoButtons(ivUndo as ImageView, ivRedo as ImageView)
                }
            } else {
                try {
                    changeHistory.redo()
                } catch (
                    e: com.philkes.notallyx.utils.changehistory.ChangeHistory.ChangeHistoryException) {
                    application.log(TAG, throwable = e)
                }
            }
        }
        changeHistory.canRedo.observe(this) { canRedo ->
            if (isDrawingModeActive) {
                updateDrawingUndoRedoButtons(ivUndo as ImageView, ivRedo as ImageView)
            } else {
                ivRedo.isEnabled = canRedo
                ivRedo.alpha = if (canRedo) 1f else 0.5f
            }
        }

        // ivBackground đã được loại bỏ khỏi toolbar, chức năng changeColor có thể được truy cập qua menu more

        ivPin.setOnClickListener { pin() }

        ivMore.setOnClickListener { openMoreMenu() }
    }

    protected open fun openMoreMenu() {}

    private fun showDrawingArea() {
        if (isDestroyed || isFinishing) return

        binding.DrawingDivider.visibility = View.VISIBLE
        binding.DrawingCanvas.visibility = View.VISIBLE
        applySavedBackgroundToCanvas()
        binding.DrawingCanvas.isEnabled = true
        isDrawingModeActive = true
        // Sync undo/redo state for drawing
        (binding.Toolbar.findViewById<View>(R.id.ivUndo) as? ImageView)?.let { ivUndo ->
            (binding.Toolbar.findViewById<View>(R.id.ivRedo) as? ImageView)?.let { ivRedo ->
                updateDrawingUndoRedoButtons(ivUndo, ivRedo)
            }
        }

        if (notallyModel.drawingStrokes.isNotEmpty()) {
            binding.DrawingCanvas.loadStrokes(notallyModel.drawingStrokes)
        }

        val postRunnable = Runnable {
            if (isDestroyed || isFinishing) return@Runnable

            val dividerTop = binding.DrawingDivider.top
            val canvasTop = binding.DrawingCanvas.top
            val dividerYRelative = (dividerTop - canvasTop).toFloat()
            binding.DrawingCanvas.setDividerY(dividerYRelative)

            val marginPx = dpToPx(20)
            val bottomBarHeight = measureBottomBarHeight()
            val availableHeight = computeAvailableCanvasHeight(bottomBarHeight, marginPx)
            applyCanvasLayout(availableHeight, marginPx)

            if (
                binding.ScrollView
                    is com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView
            ) {
                (binding.ScrollView
                        as
                        com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView)
                    .setScrollEnabled(false)
            }

            binding.ScrollView.smoothScrollTo(0, binding.DrawingCanvas.top)
        }
        postRunnables.add(postRunnable)
        binding.ScrollView.post(postRunnable)
    }

    private fun applyAndPersistBackground(colorInt: Int, drawableResId: Int?) {
        notallyModel.drawingBackgroundColor = colorInt
        notallyModel.drawingBackgroundDrawableResId = drawableResId

        if (drawableResId != null) {
            binding.DrawingCanvas.setCanvasBackgroundDrawable(drawableResId)
        } else {
            binding.DrawingCanvas.setCanvasBackgroundColor(colorInt)
        }
        persistCurrentBackground()
    }

    private fun applySavedBackgroundToCanvas() {
        // Load persisted preference (per note). If not found, fallback to draft key (id=0) then
        // migrate.
        loadDrawingBackgroundPreference()?.let { (color, drawableResId) ->
            notallyModel.drawingBackgroundColor = color
            notallyModel.drawingBackgroundDrawableResId = drawableResId
        }

        notallyModel.drawingBackgroundDrawableResId?.let { resId ->
            binding.DrawingCanvas.setCanvasBackgroundDrawable(resId)
        }
            ?: run {
                binding.DrawingCanvas.setCanvasBackgroundColor(notallyModel.drawingBackgroundColor)
            }
    }

    private fun persistCurrentBackground() {
        saveDrawingBackgroundPreference(
            notallyModel.drawingBackgroundColor,
            notallyModel.drawingBackgroundDrawableResId,
        )
    }

    private fun saveDrawingBackgroundPreference(colorInt: Int, drawableResId: Int?) {
        val prefs = getSharedPreferences(DRAWING_BG_PREFS, MODE_PRIVATE)
        val noteId = notallyModel.id
        with(prefs.edit()) {
            putInt(bgColorKey(noteId), colorInt)
            if (drawableResId != null) {
                putInt(bgResKey(noteId), drawableResId)
            } else {
                remove(bgResKey(noteId))
            }
            // Also save draft when note is new (id == 0) so it persists until first save
            if (noteId == 0L) {
                putInt(bgColorKey(0L), colorInt)
                if (drawableResId != null) {
                    putInt(bgResKey(0L), drawableResId)
                } else {
                    remove(bgResKey(0L))
                }
            }
            apply()
        }
    }

    private fun loadDrawingBackgroundPreference(): Pair<Int, Int?>? {
        val prefs = getSharedPreferences(DRAWING_BG_PREFS, MODE_PRIVATE)
        val noteId = notallyModel.id

        fun readFor(id: Long): Pair<Int, Int?>? {
            val colorKey = bgColorKey(id)
            if (!prefs.contains(colorKey)) return null
            val color = prefs.getInt(colorKey, Color.WHITE)
            val drawable = if (prefs.contains(bgResKey(id))) prefs.getInt(bgResKey(id), 0) else null
            return color to drawable
        }

        // Try current note id
        val current = readFor(noteId)
        if (current != null) return current

        // Fallback to draft (id=0) then migrate to current id if available
        val draft = readFor(0L)
        if (draft != null && noteId != 0L) {
            saveDrawingBackgroundPreference(draft.first, draft.second)
            // remove draft after migration
            with(prefs.edit()) {
                remove(bgColorKey(0L))
                remove(bgResKey(0L))
                apply()
            }
            return draft
        }
        return draft
    }

    private fun bgColorKey(id: Long) = "bg_color_$id"

    private fun bgResKey(id: Long) = "bg_res_$id"

    private fun hideDrawingArea() {
        val strokes = binding.DrawingCanvas.getStrokes()
        if (strokes.isNotEmpty()) {
            notallyModel.drawingStrokes = ArrayList(strokes)
        } else {
            notallyModel.drawingStrokes.clear()
        }

        binding.DrawingCanvas.setBrush(null)
        currentDrawTool = null
        binding.DrawingCanvas.setZoomModeEnabled(false)
        binding.DrawingCanvas.setEyeDropperMode(false)
        binding.DrawingCanvas.isEnabled = false

        // Re-enable scroll
        if (
            binding.ScrollView
                is com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView
        ) {
            (binding.ScrollView
                    as com.philkes.notallyx.presentation.view.misc.NonScrollableNestedScrollView)
                .setScrollEnabled(true)
        }

        // Keep canvas visible on note screen
        binding.DrawingDivider.visibility = View.VISIBLE
        binding.DrawingCanvas.visibility = View.VISIBLE

        // Hide tool picker and show FABs back
        hideDrawingToolPicker()
        showFABs()

        isDrawingModeActive = false
        // Restore undo/redo state for note content
        (binding.Toolbar.findViewById<View>(R.id.ivUndo) as? ImageView)?.let { ivUndo ->
            (binding.Toolbar.findViewById<View>(R.id.ivRedo) as? ImageView)?.let { ivRedo ->
                val canUndoNote = changeHistory.canUndo.value
                val canRedoNote = changeHistory.canRedo.value
                ivUndo.isEnabled = canUndoNote
                ivUndo.alpha = if (canUndoNote) 1f else 0.5f
                ivRedo.isEnabled = canRedoNote
                ivRedo.alpha = if (canRedoNote) 1f else 0.5f
            }
        }
    }

    private fun enableEyeDropperMode() {
        binding.DrawingCanvas.setEyeDropperMode(true)
        binding.DrawingCanvas.setOnColorPickedListener { color ->
            currentDrawTool?.let { tool ->
                val colorHex = String.format("#%06X", 0xFFFFFF and color)
                val updatedTool = tool.copy(color = colorHex)
                currentDrawTool = updatedTool
                binding.DrawingCanvas.setBrush(updatedTool)
                showToast("Color selected: $colorHex")
            }
        }
        showToast("Tap on canvas to pick color")
    }

    private fun toggleZoomMode() {
        val isZoomMode = binding.DrawingCanvas.isZoomModeEnabled()
        binding.DrawingCanvas.setZoomModeEnabled(!isZoomMode)

        if (!isZoomMode) {
            showToast("Zoom mode enabled")
        } else {
            showToast("Zoom mode disabled")
        }
    }

    private fun measureBottomBarHeight(): Int {
        binding.DrawToolPickerView.measure(
            View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return binding.DrawToolPickerView.measuredHeight
    }

    private fun computeAvailableCanvasHeight(bottomBarHeight: Int, marginPx: Int): Int {
        val screenHeight = binding.root.height
        val toolbarHeight = binding.Toolbar.height
        return (screenHeight - toolbarHeight - bottomBarHeight - (marginPx * 2)).coerceAtLeast(0)
    }

    private fun computeContentHeightFromStrokes(
        strokes: List<com.philkes.notallyx.draw.ui.newdraw.view.canvas.DrawingStroke>,
        paddingPx: Int,
    ): Int {
        if (strokes.isEmpty()) return 0
        var minTop = Float.MAX_VALUE
        var maxBottom = Float.MIN_VALUE
        strokes.forEach { stroke ->
            minTop = min(minTop, stroke.rectTop)
            maxBottom = max(maxBottom, stroke.rectBottom)
        }
        val contentHeight = (maxBottom - minTop).coerceAtLeast(0f)
        return contentHeight.toInt() + paddingPx * 2
    }

    private fun applyCanvasLayout(availableHeight: Int, marginPx: Int) {
        val layoutParams = binding.DrawingCanvas.layoutParams
        layoutParams.height = availableHeight
        binding.DrawingCanvas.layoutParams = layoutParams

        if (binding.DrawingCanvas.layoutParams is ViewGroup.MarginLayoutParams) {
            val marginParams = binding.DrawingCanvas.layoutParams as ViewGroup.MarginLayoutParams
            marginParams.topMargin = marginPx
            marginParams.bottomMargin = marginPx
            marginParams.marginStart = marginPx
            marginParams.marginEnd = marginPx
            binding.DrawingCanvas.layoutParams = marginParams
        } else {
            val newParams = ViewGroup.MarginLayoutParams(layoutParams)
            newParams.topMargin = marginPx
            newParams.bottomMargin = marginPx
            newParams.marginStart = marginPx
            newParams.marginEnd = marginPx
            binding.DrawingCanvas.layoutParams = newParams
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

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

        // Load drawing strokes v?o canvas n?u c?
        if (notallyModel.drawingStrokes.isNotEmpty()) {
            binding.DrawingCanvas.loadStrokes(notallyModel.drawingStrokes)
            // Hi?n th? divider v? canvas n?u c? strokes
            binding.DrawingDivider.visibility = View.VISIBLE
            binding.DrawingCanvas.visibility = View.VISIBLE
            isDrawingModeActive = true
            (binding.Toolbar.findViewById<View>(R.id.ivUndo) as? ImageView)?.let { ivUndo ->
                (binding.Toolbar.findViewById<View>(R.id.ivRedo) as? ImageView)?.let { ivRedo ->
                    updateDrawingUndoRedoButtons(ivUndo, ivRedo)
                }
            }

            // Set divider position
            val postRunnable = Runnable {
                if (isDestroyed || isFinishing) return@Runnable

                val dividerTop = binding.DrawingDivider.top
                val canvasTop = binding.DrawingCanvas.top
                val dividerYRelative = (dividerTop - canvasTop).toFloat()
                binding.DrawingCanvas.setDividerY(dividerYRelative)

                val marginPx = dpToPx(20)
                val bottomBarHeight = measureBottomBarHeight()
                val availableHeight = computeAvailableCanvasHeight(bottomBarHeight, marginPx)
                val contentHeight =
                    computeContentHeightFromStrokes(notallyModel.drawingStrokes, dpToPx(50))
                val targetHeight = max(availableHeight, contentHeight)
                applyCanvasLayout(targetHeight, marginPx)
            }
            postRunnables.add(postRunnable)
            binding.ScrollView.post(postRunnable)
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
                    if (!isDestroyed) {
                        super.finish()
                    }
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
        
        // Nếu là màu mặc định, sử dụng gradient thay vì màu trắng
        val isDefaultColor = notallyModel.color == com.philkes.notallyx.data.model.BaseNote.COLOR_DEFAULT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isDefaultColor) {
                // Dùng màu từ gradient cho status bar và navigation bar
                val gradientColor = ContextCompat.getColor(this, R.color.md_theme_background)
                window.statusBarColor = gradientColor
                window.navigationBarColor = gradientColor
                window.setLightStatusAndNavBar(true)
            } else {
            window.statusBarColor = colorInt
            window.navigationBarColor = colorInt
            window.setLightStatusAndNavBar(colorInt.isLightColor())
        }
        }
        
        binding.apply {
            if (isDefaultColor) {
                // Sử dụng gradient cho background mặc định
                ScrollView.setBackgroundResource(R.drawable.bg_edit_default_gradient)
                root.setBackgroundResource(R.drawable.bg_edit_default_gradient)
                RecyclerView.setBackgroundResource(R.drawable.bg_edit_default_gradient)
                // Toolbar trong suốt để hiển thị gradient
                Toolbar.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                Toolbar.setBackgroundColor(Color.TRANSPARENT)
            } else {
            ScrollView.apply {
                setBackgroundColor(colorInt)
                setControlsContrastColorForAllViews(colorInt)
            }
            root.setBackgroundColor(colorInt)
            RecyclerView.setBackgroundColor(colorInt)
            Toolbar.backgroundTintList = ColorStateList.valueOf(colorInt)
        }
        }
        
        if (!isDefaultColor) {
        setBottomAppBarColor(colorInt)
        fileAdapter.setColor(colorInt)
        audioAdapter.setColor(colorInt)
        } else {
            // Dùng màu từ gradient cho bottom bar
            val gradientColor = ContextCompat.getColor(this, R.color.md_theme_background)
            setBottomAppBarColor(gradientColor)
            fileAdapter.setColor(gradientColor)
            audioAdapter.setColor(gradientColor)
        }
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
        val ivPin = binding.Toolbar.findViewById<View>(R.id.ivPin) as? ImageButton
        ivPin?.setImageResource(icon)
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
        private const val REQUEST_CODE_FULL_SCREEN_DRAWING = 1001
        private const val DRAWING_BG_PREFS = "drawing_background_prefs"

        const val EXTRA_SELECTED_BASE_NOTE = "notallyx.intent.extra.SELECTED_BASE_NOTE"
        const val EXTRA_NOTE_ID = "notallyx.intent.extra.NOTE_ID"
        const val EXTRA_FOLDER_FROM = "notallyx.intent.extra.FOLDER_FROM"
        const val EXTRA_FOLDER_TO = "notallyx.intent.extra.FOLDER_TO"
    }
}
