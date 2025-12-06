package com.philkes.notallyx.presentation.activity.main

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.Menu
import android.view.Menu.CATEGORY_CONTAINER
import android.view.Menu.CATEGORY_SYSTEM
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.appcompat.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFade
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import android.widget.Toast
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.databinding.ActivityMainBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.main.fragment.DisplayLabelFragment.Companion.EXTRA_DISPLAYED_LABEL
import com.philkes.notallyx.presentation.activity.main.fragment.NotallyFragment
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.movedToResId
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.TriStateCheckBox
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.setMultiChoiceTriStateItems
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel.Companion.CURRENT_LABEL_EMPTY
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel.Companion.CURRENT_LABEL_NONE
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_DEFAULT
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_UNLABELED
import com.philkes.notallyx.utils.backup.exportNotes
import com.philkes.notallyx.utils.shareNote
import com.philkes.notallyx.utils.showColorSelectDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import github.com.st235.lib_expandablebottombar.ExpandableBottomBar

class MainActivity : LockedActivity<ActivityMainBinding>() {

    private lateinit var navController: NavController
    private lateinit var configuration: AppBarConfiguration
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportNotesActivityResultLauncher: ActivityResultLauncher<Intent>

    private var isStartViewFragment = false
    private val actionModeCancelCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                baseModel.actionMode.close(true)
            }
        }

    var getCurrentFragmentNotes: (() -> Collection<BaseNote>?)? = null

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(configuration)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_toolbar, menu)
        // Gắn click cho custom action layout (nút 3 chấm có background)
        menu.findItem(R.id.action_appearance).actionView?.setOnClickListener {
            showAppearancePopup(it)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Chỉ hiển thị nút 3 chấm trên màn Notes / DisplayLabel / Unlabeled
        val destinationId = navController.currentDestination?.id
        val showAppearance =
            destinationId == R.id.Notes ||
                destinationId == R.id.DisplayLabel ||
                destinationId == R.id.Unlabeled
        menu.findItem(R.id.action_appearance)?.isVisible = showAppearance
        return super.onPrepareOptionsMenu(menu)
    }

    private fun showAppearancePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_appearance_popup, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.appearance_view -> {
                    showViewChoiceDialog()
                    true
                }
                R.id.appearance_theme -> {
                    showThemeChoiceDialog()
                    true
                }
                R.id.appearance_date_format -> {
                    // TODO: Date format giống Settings
                    true
                }
                R.id.appearance_text_size -> {
                    // TODO: Text size giống Settings
                    true
                }
                R.id.appearance_notes_sort -> {
                    // TODO: Notes sort order giống Settings
                    true
                }
                R.id.appearance_list_sort -> {
                    // TODO: List item sorting giống Settings
                    true
                }
                R.id.appearance_start_view -> {
                    // TODO: Start view giống Settings
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showViewChoiceDialog() {
        val current = preferences.notesView.value
        val items = arrayOf(getString(R.string.list), getString(R.string.grid))
        val values = arrayOf(NotesView.LIST, NotesView.GRID)
        var checkedItem = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.view)
            .setSingleChoiceItems(items, checkedItem) { _, which ->
                checkedItem = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = values[checkedItem]
                baseModel.savePreference(preferences.notesView, newValue)
                // Cập nhật ngay giao diện danh sách ghi chú
                navigateToStartView()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyBackgroundForTheme(theme: Theme) {
        if (theme == Theme.FOLLOW_SYSTEM) {
            // Khi chọn Follow system: dùng nền đặc biệt bg_background cho toàn màn Main + navigation
            window.setBackgroundDrawableResource(R.drawable.bg_background)
            binding.root.setBackgroundResource(R.drawable.bg_background)
            binding.DrawerLayout.setBackgroundResource(R.drawable.bg_background)
            binding.NavigationView.setBackgroundResource(android.R.color.transparent)
        } else {
            // Các theme khác: trả về nền mặc định của theme (màu background của Material 3)
            window.setBackgroundDrawable(null)
            val surfaceColor =
                MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorSurface
                )
            binding.root.setBackgroundColor(surfaceColor)
            binding.DrawerLayout.setBackgroundColor(surfaceColor)
            binding.NavigationView.setBackgroundColor(
                MaterialColors.getColor(
                    binding.NavigationView,
                    com.google.android.material.R.attr.colorSurfaceContainerLow
                )
            )
        }
    }

    private fun setupWindowInsets() {
        // Xử lý window insets cho NavigationDrawerContainer để drawer không bị cắt
        // DrawerLayout cần full screen để drawer có thể slide, nhưng container bên trong cần padding
        val navigationContainer = binding.root.findViewById<ViewGroup>(R.id.NavigationDrawerContainer)
        if (navigationContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navigationContainer) { view, insets ->
                val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                
                // Set padding top và bottom cho container để drawer không bị cắt bởi status bar và navigation bar
                view.updatePadding(
                    top = statusBars.top,
                    bottom = navBars.bottom,
                    left = view.paddingLeft,
                    right = view.paddingRight
                )
                
                insets
            }
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.RelativeLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Padding cho toolbar để tránh status bar
            binding.Toolbar.updatePadding(top = statusBars.top)
            
            // Điều chỉnh margin bottom cho bottom bar theo chiều cao navigation bar
            // Chỉ set bottomMargin, không thay đổi margin khác
            val bottomBarBottomMargin = navBars.bottom
            val fabMargin = resources.getDimensionPixelSize(R.dimen.dp_16) + navBars.bottom
            
            binding.expandableBottomBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bottomBarBottomMargin
            }
            binding.FabContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = fabMargin
            }
            
            insets
        }
    }

    private fun showThemeChoiceDialog() {
        val current = preferences.theme.value
        val values = Theme.values()
        val items = values.map { getString(it.textResId) }.toTypedArray()
        var checkedItem = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(items, checkedItem) { _, which ->
                checkedItem = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = values[checkedItem]
                baseModel.savePreference(preferences.theme, newValue)
                // Áp dụng theme mới ngay lập tức
                when (newValue) {
                    Theme.DARK ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    Theme.LIGHT ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    Theme.FOLLOW_SYSTEM ->
                        AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        )
                }
                applyBackgroundForTheme(newValue)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bật edge-to-edge mode để background full màn hình
        enableEdgeToEdge()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.Toolbar)

        // Áp dụng nền theo theme hiện tại ngay khi mở MainActivity
        applyBackgroundForTheme(preferences.theme.value)
        
        // Xử lý window insets để padding đúng cho content
        setupWindowInsets()
        
        setupFAB()
        setupMenu()
        setupDrawerFooter()
        setupActionMode()
        setupNavigation()
        setupExpandableBottomBar()

        setupActivityResultLaunchers()

        val fragmentIdToLoad = intent.getIntExtra(EXTRA_FRAGMENT_TO_OPEN, -1)
        if (fragmentIdToLoad != -1) {
            navController.navigate(fragmentIdToLoad, Bundle())
        } else if (savedInstanceState == null) {
            navigateToStartView()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (baseModel.actionMode.enabled.value) {
                        return
                    }
                    if (!isStartViewFragment) {
                        navigateToStartView()
                    } else {
                        finish()
                    }
                }
            },
        )
        onBackPressedDispatcher.addCallback(this, actionModeCancelCallback)
    }

    private fun getStartViewNavigation(): Pair<Int, Bundle> {
        return when (val startView = preferences.startView.value) {
            START_VIEW_DEFAULT -> Pair(R.id.Notes, Bundle())
            START_VIEW_UNLABELED -> Pair(R.id.Unlabeled, Bundle())
            else -> {
                val bundle = Bundle().apply { putString(EXTRA_DISPLAYED_LABEL, startView) }
                Pair(R.id.DisplayLabel, bundle)
            }
        }
    }

    private fun navigateToStartView() {
        val (id, bundle) = getStartViewNavigation()
        navController.navigate(id, bundle)
    }

    // Trạng thái FAB menu
    private var isFabOpen = false
    
    // Animations
    private val rotateForward by lazy { android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_forward) }
    private val rotateBackward by lazy { android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_backward) }
    private val fabOpen by lazy { android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fab_open) }
    private val fabClose by lazy { android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fab_close) }
    
    private fun setupFAB() {
        // Main FAB (dấu +) - click để toggle expand/collapse menu
        binding.MainFab.setOnClickListener {
            animateFAB()
        }
        
        // Child FAB 1: TakeNote - click để mở EditNoteActivity và collapse menu
        binding.TakeNote.setOnClickListener {
            animateFAB() // Đóng menu lại
            val intent = Intent(this, EditNoteActivity::class.java)
            startActivity(prepareNewNoteIntent(intent))
        }
        
        // Child FAB 2: MakeList - click để mở EditListActivity và collapse menu
        binding.MakeList.setOnClickListener {
            animateFAB() // Đóng menu lại
            val intent = Intent(this, EditListActivity::class.java)
            startActivity(prepareNewNoteIntent(intent))
        }
        
        // Ban đầu: Ẩn các FAB phụ và tắt clickable
        binding.TakeNote.visibility = View.INVISIBLE
        binding.MakeList.visibility = View.INVISIBLE
        binding.TakeNote.isClickable = false
        binding.MakeList.isClickable = false
    }
    
    private fun animateFAB() {
        if (isFabOpen) {
            // ĐANG MỞ -> Cần ĐÓNG lại
            binding.MainFab.startAnimation(rotateBackward)
            
            binding.TakeNote.startAnimation(fabClose)
            binding.MakeList.startAnimation(fabClose)
            
            // QUAN TRỌNG: Tắt khả năng click khi ẩn đi
            binding.TakeNote.isClickable = false
            binding.MakeList.isClickable = false
            
            // Ẩn sau khi animation xong
            binding.TakeNote.postDelayed({
                binding.TakeNote.visibility = View.INVISIBLE
            }, 300)
            binding.MakeList.postDelayed({
                binding.MakeList.visibility = View.INVISIBLE
            }, 300)
            
            // Cập nhật trạng thái
            isFabOpen = false
        } else {
            // ĐANG ĐÓNG -> Cần MỞ ra
            binding.MainFab.startAnimation(rotateForward)
            
            // Hiện FAB phụ trước khi animate
            binding.TakeNote.visibility = View.VISIBLE
            binding.MakeList.visibility = View.VISIBLE
            
            binding.TakeNote.startAnimation(fabOpen)
            binding.MakeList.startAnimation(fabOpen)
            
            // QUAN TRỌNG: Bật khả năng click khi hiện lên
            binding.TakeNote.isClickable = true
            binding.MakeList.isClickable = true
            
            // Cập nhật trạng thái
            isFabOpen = true
        }
    }

    private fun prepareNewNoteIntent(intent: Intent): Intent {
        return supportFragmentManager
            .findFragmentById(R.id.NavHostFragment)
            ?.childFragmentManager
            ?.fragments
            ?.firstOrNull()
            ?.let { fragment ->
                return if (fragment is NotallyFragment) {
                    fragment.prepareNewNoteIntent(intent)
                } else intent
            } ?: intent
    }

    private var labelsMenuItems: List<MenuItem> = listOf()
    private var labelsMoreMenuItem: MenuItem? = null
    private var labels: List<String> = listOf()
    private var labelsLiveData: LiveData<List<String>>? = null

    private fun setupMenu() {
        binding.NavigationView.menu.apply {
            add(0, R.id.Notes, 0, R.string.notes).setCheckable(true).setIcon(R.drawable.home)

            addStaticLabelsMenuItems()
            NotallyDatabase.getDatabase(application).observe(this@MainActivity) { database ->
                labelsLiveData?.removeObservers(this@MainActivity)
                labelsLiveData =
                    database.getLabelDao().getAll().also {
                        it.observe(this@MainActivity) { labels ->
                            this@MainActivity.labels = labels
                            setupLabelsMenuItems(labels, preferences.maxLabels.value)
                        }
                    }
            }

            add(2, R.id.Deleted, CATEGORY_SYSTEM + 1, R.string.deleted)
                .setCheckable(true)
                .setIcon(R.drawable.delete)
            add(2, R.id.Archived, CATEGORY_SYSTEM + 2, R.string.archived)
                .setCheckable(true)
                .setIcon(R.drawable.archive)
            add(3, R.id.Reminders, CATEGORY_SYSTEM + 3, R.string.reminders)
                .setCheckable(true)
                .setIcon(R.drawable.notifications)
            add(3, R.id.Settings, CATEGORY_SYSTEM + 4, R.string.settings)
                .setCheckable(true)
                .setIcon(R.drawable.settings)
        }
        baseModel.preferences.labelsHiddenInNavigation.observe(this) { hiddenLabels ->
            hideLabelsInNavigation(hiddenLabels, baseModel.preferences.maxLabels.value)
        }
        baseModel.preferences.maxLabels.observe(this) { maxLabels ->
            binding.NavigationView.menu.setupLabelsMenuItems(labels, maxLabels)
        }
    }

    private fun setupDrawerFooter() {
        // Setup theme toggle - find footer view from root
        val drawerFooter = binding.root.findViewById<View>(R.id.DrawerFooter)
        val themeLight = drawerFooter?.findViewById<com.google.android.material.button.MaterialButton>(R.id.ThemeLight)
        val themeDark = drawerFooter?.findViewById<com.google.android.material.button.MaterialButton>(R.id.ThemeDark)

        if (themeLight != null && themeDark != null) {
            // Update theme buttons state based on current theme
            preferences.theme.observe(this) { currentTheme ->
                when (currentTheme) {
                    Theme.LIGHT -> {
                        themeLight.isSelected = true
                        themeDark.isSelected = false
                        themeLight.backgroundTintList = ContextCompat.getColorStateList(this, R.color.md_theme_primaryContainer)
                        themeDark.backgroundTintList = null
                    }
                    Theme.DARK -> {
                        themeLight.isSelected = false
                        themeDark.isSelected = true
                        themeLight.backgroundTintList = null
                        themeDark.backgroundTintList = ContextCompat.getColorStateList(this, R.color.md_theme_primaryContainer)
                    }
                    Theme.FOLLOW_SYSTEM -> {
                        themeLight.isSelected = false
                        themeDark.isSelected = false
                        themeLight.backgroundTintList = null
                        themeDark.backgroundTintList = null
                    }
                }
            }

            themeLight.setOnClickListener {
                baseModel.savePreference(preferences.theme, Theme.LIGHT)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                recreate()
            }

            themeDark.setOnClickListener {
                baseModel.savePreference(preferences.theme, Theme.DARK)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                recreate()
            }
        }
    }

    private fun Menu.addStaticLabelsMenuItems() {
        add(1, R.id.Unlabeled, CATEGORY_CONTAINER + 1, R.string.unlabeled)
            .setCheckable(true)
            .setChecked(baseModel.currentLabel == CURRENT_LABEL_NONE)
            .setIcon(R.drawable.label_off)
        add(1, R.id.Labels, CATEGORY_CONTAINER + 2, R.string.labels)
            .setCheckable(true)
            .setIcon(R.drawable.label_more)
    }

    private fun Menu.setupLabelsMenuItems(labels: List<String>, maxLabelsToDisplay: Int) {
        removeGroup(1)
        addStaticLabelsMenuItems()
        labelsMenuItems =
            labels
                .mapIndexed { index, label ->
                    add(1, R.id.DisplayLabel, CATEGORY_CONTAINER + index + 3, label)
                        .setCheckable(true)
                        .setChecked(baseModel.currentLabel == label)
                        .setVisible(index < maxLabelsToDisplay)
                        .setIcon(R.drawable.label)
                        .setOnMenuItemClickListener {
                            navigateToLabel(label)
                            false
                        }
                }
                .toList()

        labelsMoreMenuItem =
            if (labelsMenuItems.size > maxLabelsToDisplay) {
                add(
                        1,
                        R.id.Labels,
                        CATEGORY_CONTAINER + labelsMenuItems.size + 2,
                        getString(R.string.more, labelsMenuItems.size - maxLabelsToDisplay),
                    )
                    .setCheckable(true)
                    .setIcon(R.drawable.label)
            } else null
        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)
        hideLabelsInNavigation(
            baseModel.preferences.labelsHiddenInNavigation.value,
            maxLabelsToDisplay,
        )
    }

    private fun navigateToLabel(label: String) {
        val bundle = Bundle().apply { putString(EXTRA_DISPLAYED_LABEL, label) }
        navController.navigate(R.id.DisplayLabel, bundle)
    }

    private fun hideLabelsInNavigation(hiddenLabels: Set<String>, maxLabelsToDisplay: Int) {
        var visibleLabels = 0
        labelsMenuItems.forEach { menuItem ->
            val visible =
                !hiddenLabels.contains(menuItem.title) && visibleLabels < maxLabelsToDisplay
            menuItem.setVisible(visible)
            if (visible) {
                visibleLabels++
            }
        }
        labelsMoreMenuItem?.setTitle(getString(R.string.more, labels.size - visibleLabels))
    }

    private fun setupActionMode() {
        binding.ActionMode.setNavigationOnClickListener { baseModel.actionMode.close(true) }

        val transition =
            MaterialFade().apply {
                secondaryAnimatorProvider = null
                excludeTarget(binding.NavHostFragment, true)
                excludeChildren(binding.NavHostFragment, true)
                excludeTarget(binding.TakeNote, true)
                excludeTarget(binding.MakeList, true)
                excludeTarget(binding.MainFab, true)
                excludeTarget(binding.FabContainer, true)
                excludeTarget(binding.NavigationView, true)
            }

        baseModel.actionMode.enabled.observe(this) { enabled ->
            TransitionManager.beginDelayedTransition(binding.RelativeLayout, transition)
            if (enabled) {
                binding.Toolbar.visibility = View.GONE
                binding.ActionMode.visibility = View.VISIBLE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.Toolbar.visibility = View.VISIBLE
                binding.ActionMode.visibility = View.GONE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
            actionModeCancelCallback.isEnabled = enabled
        }

        val menu = binding.ActionMode.menu
        baseModel.folder.observe(this@MainActivity, ModelFolderObserver(menu, baseModel))
    }

    private fun moveNotes(folderTo: Folder) {
        val folderFrom = baseModel.actionMode.getFirstNote().folder
        val ids = baseModel.moveBaseNotes(folderTo)
        Snackbar.make(
                findViewById(R.id.DrawerLayout),
                getQuantityString(folderTo.movedToResId(), ids.size),
                Snackbar.LENGTH_SHORT,
            )
            .apply { setAction(R.string.undo) { baseModel.moveBaseNotes(ids, folderFrom) } }
            .show()
    }

    private fun share() {
        val baseNote = baseModel.actionMode.getFirstNote()
        val body =
            when (baseNote.type) {
                Type.NOTE -> baseNote.body.applySpans(baseNote.spans)
                Type.LIST -> baseNote.items.toText()
            }
        this.shareNote(baseNote.title, body)
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_selected_notes)
            .setPositiveButton(R.string.delete) { _, _ -> baseModel.deleteSelectedBaseNotes() }
            .setCancelButton()
            .show()
    }

    private fun label() {
        val baseNotes = baseModel.actionMode.selectedNotes.values
        lifecycleScope.launch {
            val labels = baseModel.getAllLabels()
            if (labels.isNotEmpty()) {
                displaySelectLabelsDialog(labels, baseNotes)
            } else {
                baseModel.actionMode.close(true)
                navigateWithAnimation(R.id.Labels)
            }
        }
    }

    private fun displaySelectLabelsDialog(labels: Array<String>, baseNotes: Collection<BaseNote>) {
        val checkedPositions =
            labels
                .map { label ->
                    if (baseNotes.all { it.labels.contains(label) }) {
                        TriStateCheckBox.State.CHECKED
                    } else if (baseNotes.any { it.labels.contains(label) }) {
                        TriStateCheckBox.State.PARTIALLY_CHECKED
                    } else {
                        TriStateCheckBox.State.UNCHECKED
                    }
                }
                .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.labels)
            .setCancelButton()
            .setMultiChoiceTriStateItems(this, labels, checkedPositions) { idx, state ->
                checkedPositions[idx] = state
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val checkedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.CHECKED) {
                            labels[index]
                        } else null
                    }
                val uncheckedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.UNCHECKED) {
                            labels[index]
                        } else null
                    }
                val updatedBaseNotesLabels =
                    baseNotes.map { baseNote ->
                        val noteLabels = baseNote.labels.toMutableList()
                        checkedLabels.forEach { checkedLabel ->
                            if (!noteLabels.contains(checkedLabel)) {
                                noteLabels.add(checkedLabel)
                            }
                        }
                        uncheckedLabels.forEach { uncheckedLabel ->
                            if (noteLabels.contains(uncheckedLabel)) {
                                noteLabels.remove(uncheckedLabel)
                            }
                        }
                        noteLabels
                    }
                baseNotes.zip(updatedBaseNotesLabels).forEach { (baseNote, updatedLabels) ->
                    baseModel.updateBaseNoteLabels(updatedLabels, baseNote.id)
                }
            }
            .show()
    }

    private fun exportSelectedNotes(mimeType: ExportMimeType) {
        exportNotes(
            mimeType,
            baseModel.actionMode.selectedNotes.values,
            exportFileActivityResultLauncher,
            exportNotesActivityResultLauncher,
        )
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.NavHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Scrim tối cho drawer, tránh trong suốt
        binding.DrawerLayout.setScrimColor(Color.parseColor("#80000000"))

        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)

        var fragmentIdToLoad: Int? = null
        binding.NavigationView.setNavigationItemSelectedListener { item ->
            fragmentIdToLoad = item.itemId
            binding.DrawerLayout.closeDrawer(GravityCompat.START)
            return@setNavigationItemSelectedListener true
        }

        binding.DrawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {

                override fun onDrawerClosed(drawerView: View) {
                    if (
                        fragmentIdToLoad != null &&
                            navController.currentDestination?.id != fragmentIdToLoad
                    ) {
                        navigateWithAnimation(requireNotNull(fragmentIdToLoad))
                    }
                }
            }
        )

        navController.addOnDestinationChangedListener { _, destination, bundle ->
            fragmentIdToLoad = destination.id
            when (fragmentIdToLoad) {
                R.id.DisplayLabel ->
                    bundle?.getString(EXTRA_DISPLAYED_LABEL)?.let {
                        baseModel.currentLabel = it
                        binding.NavigationView.menu.children
                            .find { menuItem -> menuItem.title == it }
                            ?.let { menuItem -> menuItem.isChecked = true }
                    }
                R.id.Unlabeled -> {
                    baseModel.currentLabel = CURRENT_LABEL_NONE
                    binding.NavigationView.setCheckedItem(destination.id)
                }
                else -> {
                    baseModel.currentLabel = CURRENT_LABEL_EMPTY
                    binding.NavigationView.setCheckedItem(destination.id)
                }
            }
            when (destination.id) {
                R.id.Notes,
                R.id.DisplayLabel,
                R.id.Unlabeled -> {
                    // Show main FAB (dấu +)
                    binding.MainFab.show()
                }

                else -> {
                    // Hide main FAB và collapse menu nếu đang expand
                    if (isFabOpen) {
                        animateFAB() // Đóng menu nếu đang mở
                    }
                    binding.MainFab.hide()
                }
            }

            // Toolbar luôn transparent để background full màn hình hiển thị
            binding.Toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.Toolbar.elevation = 0f
            isStartViewFragment = isStartViewFragment(destination.id, bundle)
            // Cập nhật lại menu mỗi khi đổi màn hình (đảm bảo 3 chấm không xuất hiện ở Settings)
            invalidateOptionsMenu()
            
            // Đồng bộ trạng thái selected với ExpandableBottomBar
            updateBottomBarSelection(destination.id)
        }
    }

    private fun isStartViewFragment(id: Int, bundle: Bundle?): Boolean {
        val (startViewId, startViewBundle) = getStartViewNavigation()
        return startViewId == id &&
            startViewBundle.getString(EXTRA_DISPLAYED_LABEL) ==
                bundle?.getString(EXTRA_DISPLAYED_LABEL)
    }

    private fun navigateWithAnimation(id: Int) {
        val options = navOptions {
            launchSingleTop = true
            anim {
                exit = androidx.navigation.ui.R.anim.nav_default_exit_anim
                enter = androidx.navigation.ui.R.anim.nav_default_enter_anim
                popExit = androidx.navigation.ui.R.anim.nav_default_pop_exit_anim
                popEnter = androidx.navigation.ui.R.anim.nav_default_pop_enter_anim
            }
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
        }
        navController.navigate(id, null, options)
    }

    private var isUpdatingBottomBar = false // Flag để tránh loop khi update programmatically
    
    private fun setupExpandableBottomBar() {
        val bottomBar = binding.root.findViewById<ExpandableBottomBar>(R.id.expandable_bottom_bar)
            ?: return

        // Map menu item IDs với destination IDs
        val menuItemToDestinationMap = mapOf(
            R.id.icon_home to R.id.Notes,
            R.id.icon_archive to R.id.Archived,
            R.id.icon_trash to R.id.Deleted,
            R.id.icon_settings to R.id.Settings
        )
        
        // Dùng onItemSelectedListener của ExpandableBottomBar
        bottomBar.onItemSelectedListener = { view, menuItem, byUser ->
            // Chỉ xử lý nếu không đang update programmatically
            if (!isUpdatingBottomBar) {
                // Lấy itemId từ menuItem
                val itemId = try {
                    when {
                        menuItem is MenuItem -> menuItem.itemId
                        else -> {
                            // Thử dùng reflection để lấy itemId
                            try {
                                val getItemIdMethod = menuItem.javaClass.getMethod("getItemId")
                                getItemIdMethod.invoke(menuItem) as? Int
                            } catch (e: NoSuchMethodException) {
                                // Thử method khác
                                try {
                                    val getIdMethod = menuItem.javaClass.getMethod("getId")
                                    getIdMethod.invoke(menuItem) as? Int
                                } catch (e2: Exception) {
                                    null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                
                // Map menu item ID với destination ID và navigate
                val destinationId = itemId?.let { menuItemToDestinationMap[it] }
                if (destinationId != null) {
                    // Navigate ngay lập tức khi user click
                    navigateWithAnimation(destinationId)
                }
            }
        }
    }
    
    private fun updateBottomBarSelection(destinationId: Int?) {
        if (isUpdatingBottomBar) return // Đang update rồi thì bỏ qua
        
        val bottomBar = binding.root.findViewById<ExpandableBottomBar>(R.id.expandable_bottom_bar)
            ?: return
        
        // Map destination IDs với menu item IDs
        val destinationToMenuItemMap = mapOf(
            R.id.Notes to R.id.icon_home,
            R.id.Archived to R.id.icon_archive,
            R.id.Deleted to R.id.icon_trash,
            R.id.Settings to R.id.icon_settings
        )
        
        val menuItemId = destinationId?.let { destinationToMenuItemMap[it] }
        
        if (menuItemId != null) {
            bottomBar.post {
                try {
                    // Dùng reflection để gọi select() method với byUser = false
                    // Điều này sẽ highlight item mà không trigger listener
                    val selectMethod = bottomBar.javaClass.getMethod("select", Int::class.java, Boolean::class.java)
                    isUpdatingBottomBar = true
                    selectMethod.invoke(bottomBar, menuItemId, false) // false = không trigger listener
                    // Reset flag sau một chút
                    bottomBar.postDelayed({ isUpdatingBottomBar = false }, 100)
                } catch (e: Exception) {
                    // Nếu không có method select, thử cách khác
                    isUpdatingBottomBar = false
                    // Fallback: tìm view và trigger click programmatically
                    try {
                        // Tìm view bằng cách duyệt qua children
                        bottomBar.postDelayed({
                            val view = findViewByMenuItemId(bottomBar, menuItemId)
                            if (view != null && view.isClickable) {
                                isUpdatingBottomBar = true
                                view.performClick()
                                bottomBar.postDelayed({ isUpdatingBottomBar = false }, 200)
                            }
                        }, 50)
                    } catch (e2: Exception) {
                        // Ignore
                        e2.printStackTrace()
                    }
                }
            }
        }
    }
    
    private fun findViewByMenuItemId(parent: ViewGroup, menuItemId: Int): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.id == menuItemId) {
                return child
            }
            if (child is ViewGroup) {
                val found = findViewByMenuItemId(child, menuItemId)
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupActivityResultLaunchers() {
        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> baseModel.exportSelectedFileToUri(uri) }
                }
            }
        exportNotesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> baseModel.exportSelectedNotesToFolder(uri) }
                }
            }
    }

    private inner class ModelFolderObserver(
        private val menu: Menu,
        private val model: BaseNoteModel,
    ) : Observer<Folder> {
        override fun onChanged(value: Folder) {
            menu.clear()
            model.actionMode.count.removeObservers(this@MainActivity)

            menu.add(
                R.string.select_all,
                R.drawable.select_all,
                showAsAction = MenuItem.SHOW_AS_ACTION_ALWAYS,
            ) {
                getCurrentFragmentNotes?.invoke()?.let { model.actionMode.add(it) }
            }
            when (value) {
                Folder.NOTES -> {
                    val pinned = menu.addPinned(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addLabels(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addDelete(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(R.string.archive, R.drawable.archive) { moveNotes(Folder.ARCHIVED) }
                    menu.addChangeColor()
                    val share = menu.addShare()
                    menu.addExportMenu()
                    model.actionMode.count.observeCountAndPinned(this@MainActivity, share, pinned)
                }

                Folder.ARCHIVED -> {
                    menu.add(
                        R.string.unarchive,
                        R.drawable.unarchive,
                        MenuItem.SHOW_AS_ACTION_ALWAYS,
                    ) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.addDelete(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addExportMenu(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    val pinned = menu.addPinned()
                    menu.addLabels()
                    menu.addChangeColor()
                    val share = menu.addShare()
                    model.actionMode.count.observeCountAndPinned(this@MainActivity, share, pinned)
                }

                Folder.DELETED -> {
                    menu.add(R.string.restore, R.drawable.restore, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.add(
                        R.string.delete_forever,
                        R.drawable.delete,
                        MenuItem.SHOW_AS_ACTION_ALWAYS,
                    ) {
                        deleteForever()
                    }
                    menu.addExportMenu()
                    menu.addChangeColor()
                    val share = menu.add(R.string.share, R.drawable.share) { share() }
                    model.actionMode.count.observeCount(this@MainActivity, share)
                }
            }
        }

        private fun Menu.addPinned(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.pin, R.drawable.pin, showAsAction) {}
        }

        private fun Menu.addLabels(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.labels, R.drawable.label, showAsAction) { label() }
        }

        private fun Menu.addChangeColor(
            showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM
        ): MenuItem {
            return add(R.string.change_color, R.drawable.change_color, showAsAction) {
                lifecycleScope.launch {
                    val colors =
                        withContext(Dispatchers.IO) {
                            NotallyDatabase.getDatabase(
                                    this@MainActivity,
                                    observePreferences = false,
                                )
                                .value
                                .getBaseNoteDao()
                                .getAllColors()
                        }
                    // Show color as selected only if all selected notes have the same color
                    val currentColor =
                        model.actionMode.selectedNotes.values
                            .map { it.color }
                            .distinct()
                            .takeIf { it.size == 1 }
                            ?.firstOrNull()
                    showColorSelectDialog(
                        colors,
                        currentColor,
                        null,
                        { selectedColor, oldColor ->
                            if (oldColor != null) {
                                model.changeColor(oldColor, selectedColor)
                            }
                            model.colorBaseNote(selectedColor)
                        },
                    ) { colorToDelete, newColor ->
                        model.changeColor(colorToDelete, newColor)
                    }
                }
            }
        }

        private fun Menu.addDelete(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.delete, R.drawable.delete, showAsAction) {
                moveNotes(Folder.DELETED)
            }
        }

        private fun Menu.addShare(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.share, R.drawable.share, showAsAction) { share() }
        }

        private fun Menu.addExportMenu(
            showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM
        ): MenuItem {
            return addSubMenu(R.string.export)
                .apply {
                    setIcon(R.drawable.export)
                    item.setShowAsAction(showAsAction)
                    ExportMimeType.entries.forEach {
                        add(it.name).onClick { exportSelectedNotes(it) }
                    }
                }
                .item
        }

        fun MenuItem.onClick(function: () -> Unit) {
            setOnMenuItemClickListener {
                function()
                return@setOnMenuItemClickListener false
            }
        }

        private fun NotNullLiveData<Int>.observeCount(
            lifecycleOwner: LifecycleOwner,
            share: MenuItem,
            onCountChange: ((Int) -> Unit)? = null,
        ) {
            observe(lifecycleOwner) { count ->
                binding.ActionMode.title = count.toString()
                onCountChange?.invoke(count)
                share.setVisible(count == 1)
            }
        }

        private fun NotNullLiveData<Int>.observeCountAndPinned(
            lifecycleOwner: LifecycleOwner,
            share: MenuItem,
            pinned: MenuItem,
        ) {
            observeCount(lifecycleOwner, share) {
                val baseNotes = model.actionMode.selectedNotes.values
                if (baseNotes.any { !it.pinned }) {
                    pinned.setTitle(R.string.pin).setIcon(R.drawable.pin).onClick {
                        model.pinBaseNotes(true)
                    }
                } else {
                    pinned.setTitle(R.string.unpin).setIcon(R.drawable.unpin).onClick {
                        model.pinBaseNotes(false)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FRAGMENT_TO_OPEN = "notallyx.intent.extra.FRAGMENT_TO_OPEN"
    }
}
