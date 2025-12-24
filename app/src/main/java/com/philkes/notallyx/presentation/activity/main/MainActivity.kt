package com.philkes.notallyx.presentation.activity.main

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.philkes.notallyx.presentation.view.main.DrawerAdapter
import com.philkes.notallyx.presentation.view.main.DrawerEntry
import com.philkes.notallyx.presentation.view.main.PinnedNoteAdapter

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
        // Dùng gradient thống nhất cho toàn app
        window.setBackgroundDrawableResource(R.drawable.bg_app_gradient)
        binding.root.setBackgroundResource(R.drawable.bg_app_gradient)
        binding.DrawerLayout.setBackgroundResource(R.drawable.bg_app_gradient)
        // Đồng bộ màu thanh điều hướng với nền app để tránh ám đen
        val navColor = ContextCompat.getColor(this, R.color.md_theme_background)
        window.navigationBarColor = navColor
    }

    private fun setupWindowInsets() {
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
            
            // Padding cho tất cả toolbar để tránh status bar (nằm dưới status bar)
            binding.Toolbar.updatePadding(top = statusBars.top)
            binding.HomeToolbar.updatePadding(top = statusBars.top)
            binding.NotesToolbar.updatePadding(top = statusBars.top)
            binding.ActionMode.updatePadding(top = statusBars.top)
            
            // Điều chỉnh margin bottom cho bottom bar theo chiều cao navigation bar
            // Chỉ set bottomMargin, không thay đổi margin khác
            val bottomBarBottomMargin = navBars.bottom
            // FAB cần cao hơn bottom bar (68dp minHeight + 16dp margin + 16dp padding = 100dp) + nav bar
            val fabMargin = resources.getDimensionPixelSize(R.dimen.dp_100) + navBars.bottom
            
            // BottomBarContainer là MaterialCardView trong ConstraintLayout
            val bottomBarContainer = binding.root.findViewById<ViewGroup>(R.id.BottomBarContainer)
            bottomBarContainer?.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = bottomBarBottomMargin
            }
            
            binding.FabContainer?.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
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
        setupActionMode()
        setupNavigation()
        setupExpandableBottomBar()
        setupHomeTopBar()
        setupNotesTopBar()
        
        // Set visibility ban đầu cho toolbars
        val currentDestination = navController.currentDestination?.id
        if (currentDestination == R.id.Home) {
            binding.Toolbar?.isVisible = false
            binding.HomeToolbar?.isVisible = true
            binding.NotesToolbar?.isVisible = false
        } else if (currentDestination == R.id.Notes || currentDestination == R.id.DisplayLabel || currentDestination == R.id.Unlabeled || currentDestination == R.id.Checklist) {
            binding.Toolbar?.isVisible = false
            binding.HomeToolbar?.isVisible = false
            binding.NotesToolbar?.isVisible = true
        } else {
            binding.Toolbar?.isVisible = true
            binding.HomeToolbar?.isVisible = false
            binding.NotesToolbar?.isVisible = false
        }

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
            START_VIEW_DEFAULT -> Pair(R.id.Home, Bundle()) // Home là màn hình đầu tiên
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
    
    // Track postDelayed runnables for cleanup
    private val fabPostRunnables = mutableListOf<Runnable>()
    
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
            val hideTakeNoteRunnable = Runnable { binding.TakeNote.visibility = View.INVISIBLE }
            val hideMakeListRunnable = Runnable { binding.MakeList.visibility = View.INVISIBLE }
            fabPostRunnables.add(hideTakeNoteRunnable)
            fabPostRunnables.add(hideMakeListRunnable)
            binding.TakeNote.postDelayed(hideTakeNoteRunnable, 300)
            binding.MakeList.postDelayed(hideMakeListRunnable, 300)
            
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

    private var labels: List<String> = listOf()
    private var labelsLiveData: LiveData<List<String>>? = null
    private var drawerAdapter: DrawerAdapter? = null
    private var drawerEntries: List<DrawerEntry> = emptyList()
    private var selectedDrawerId: String = "notes"
    private var isSearchExpanded = false
    private var isNotesSearchExpanded = false
    private var homeSearchListener: ((String) -> Unit)? = null
    private var homeAddListener: (() -> Unit)? = null
    private var notesSearchListener: ((String) -> Unit)? = null

    private fun setupDrawerRecycler() {
        val drawerRecycler = binding.root.findViewById<RecyclerView>(R.id.DrawerRecyclerView)
        val themeToggle = binding.root.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.ThemeToggle)
        val btnSystem = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.BtnThemeSystem)
        val btnLight = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.BtnThemeLight)
        val btnDark = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.BtnThemeDark)
        val navHomeBtn = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.NavSettingsBtn)
        navHomeBtn?.setOnClickListener {
            // Đi tới Home Today khi ấn icon ở header
            navigateWithAnimation(R.id.Home)
            binding.DrawerLayout.closeDrawer(GravityCompat.START)
        }

        drawerAdapter =
            DrawerAdapter(
                onItemClick = { entry ->
                    when (entry) {
                        is DrawerEntry.Item -> handleDrawerItemClick(entry.id)
                        is DrawerEntry.Child -> handleDrawerItemClick(entry.id)
                        is DrawerEntry.Section -> Unit
                    }
                },
            )
        drawerRecycler?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = drawerAdapter
            setHasFixedSize(false)
        }

            NotallyDatabase.getDatabase(application).observe(this@MainActivity) { database ->
                labelsLiveData?.removeObservers(this@MainActivity)
                labelsLiveData =
                    database.getLabelDao().getAll().also {
                        it.observe(this@MainActivity) { labels ->
                            this@MainActivity.labels = labels
                        refreshDrawer()
                        }
                    }
            }

        fun applyToggle(theme: Theme) {
            val systemBtn = btnSystem ?: return
            val lightBtn = btnLight ?: return
            val darkBtn = btnDark ?: return
            // Phối màu toggle theo nền kem của app
            val selectedTint = ColorStateList.valueOf(Color.parseColor("#FFE8C2"))
            val unselectedTint = ColorStateList.valueOf(Color.TRANSPARENT)
            val selectedText = Color.parseColor("#B06A1F")
            val unselectedText = Color.parseColor("#6E6E6E")

            fun style(btn: com.google.android.material.button.MaterialButton, selected: Boolean) {
                btn.backgroundTintList = if (selected) selectedTint else unselectedTint
                btn.setTextColor(if (selected) selectedText else unselectedText)
                btn.iconTint = ColorStateList.valueOf(if (selected) selectedText else unselectedText)
            }

            style(systemBtn, theme == Theme.FOLLOW_SYSTEM)
            style(lightBtn, theme == Theme.LIGHT)
            style(darkBtn, theme == Theme.DARK)
            when (theme) {
                Theme.FOLLOW_SYSTEM -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    themeToggle?.check(R.id.BtnThemeSystem)
                }
                Theme.LIGHT -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    themeToggle?.check(R.id.BtnThemeLight)
                }
                Theme.DARK -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    themeToggle?.check(R.id.BtnThemeDark)
                }
            }
            baseModel.savePreference(preferences.theme, theme)
        }

        themeToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.BtnThemeSystem -> applyToggle(Theme.FOLLOW_SYSTEM)
                R.id.BtnThemeLight -> applyToggle(Theme.LIGHT)
                R.id.BtnThemeDark -> applyToggle(Theme.DARK)
            }
        }

        // Default to system; respect stored preference if present
        when (preferences.theme.value) {
            Theme.LIGHT -> applyToggle(Theme.LIGHT)
            Theme.DARK -> applyToggle(Theme.DARK)
            else -> applyToggle(Theme.FOLLOW_SYSTEM)
        }
            }

    private val searchDebounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private fun setupHomeTopBar() {
        binding.HomeMenuButton?.setOnClickListener {
            binding.DrawerLayout.openDrawer(GravityCompat.START)
        }
        binding.HomeSearchButton?.setOnClickListener { toggleSearch() }
        binding.HomeAddButton?.setOnClickListener { homeAddListener?.invoke() }
        
        // Debounce search input to avoid lag when typing
        binding.HomeSearchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Remove previous pending search
                searchDebounceHandler.removeCallbacksAndMessages(null)
                // Debounce search by 300ms
                val query = s?.toString().orEmpty()
                searchDebounceHandler.postDelayed({
                    homeSearchListener?.invoke(query)
                }, 300)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        collapseSearch(clearText = true, hideKeyboard = false)
    }

    private fun setupNotesTopBar() {
        // NotesMenuButton và NotesSearchButton sẽ được setup trong setupActionMode()
        // để xử lý cả normal mode và action mode
        
        // Setup more button cho normal mode (appearance menu)
        binding.NotesMoreButton?.setOnClickListener {
            if (baseModel.actionMode.isEnabled()) {
                // Trong action mode, menu sẽ được setup trong setupActionModeMoreMenu
                // Nhưng để đảm bảo, ta check lại
                return@setOnClickListener
            } else {
                // Normal mode: mở appearance popup
                showAppearancePopup(it)
            }
        }
        // Đảm bảo icon more luôn hiển thị và có màu đen
        binding.NotesMoreButton?.visibility = View.VISIBLE
        binding.NotesMoreButton?.imageTintList = ColorStateList.valueOf(Color.BLACK)
        
        // Debounce search input to avoid lag when typing
        binding.NotesSearchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Remove previous pending search
                searchDebounceHandler.removeCallbacksAndMessages(null)
                // Debounce search by 300ms
                val query = s?.toString().orEmpty()
                searchDebounceHandler.postDelayed({
                    notesSearchListener?.invoke(query)
                }, 300)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        collapseNotesSearch(clearText = true, hideKeyboard = false)
    }

    private fun toggleSearch() {
        val input = binding.HomeSearchInput
        if (!isSearchExpanded) {
            expandSearch()
        } else {
            if (input?.text.isNullOrEmpty()) {
                collapseSearch(clearText = false, hideKeyboard = true)
            } else {
                input?.setText("")
            }
        }
    }

    private fun expandSearch() {
        isSearchExpanded = true
        binding.HomeSearchHint?.visibility = View.GONE
        binding.HomeSearchInput?.visibility = View.VISIBLE
        binding.HomeSearchInput?.requestFocus()
        binding.HomeSearchButton?.setImageResource(R.drawable.close)
        showKeyboard(binding.HomeSearchInput)
    }

    private fun collapseSearch(clearText: Boolean = true, hideKeyboard: Boolean = true) {
        isSearchExpanded = false
        if (clearText) {
            binding.HomeSearchInput?.setText("")
            homeSearchListener?.invoke("")
        }
        binding.HomeSearchHint?.visibility = View.VISIBLE
        binding.HomeSearchInput?.visibility = View.GONE
        binding.HomeSearchButton?.setImageResource(R.drawable.ic_search)
        if (hideKeyboard) {
            hideKeyboard(binding.HomeSearchInput)
        }
    }

    private fun showKeyboard(editText: EditText?) {
        editText ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }

    private fun hideKeyboard(view: View?) {
        view ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toggleNotesSearch() {
        val input = binding.NotesSearchInput
        if (!isNotesSearchExpanded) {
            expandNotesSearch()
        } else {
            if (input?.text.isNullOrEmpty()) {
                collapseNotesSearch(clearText = false, hideKeyboard = true)
            } else {
                input?.setText("")
            }
        }
    }

    private fun expandNotesSearch() {
        isNotesSearchExpanded = true
        binding.NotesSearchHint?.visibility = View.GONE
        binding.NotesSearchInput?.visibility = View.VISIBLE
        binding.NotesSearchInput?.requestFocus()
        binding.NotesSearchButton?.setImageResource(R.drawable.close)
        showKeyboard(binding.NotesSearchInput)
    }

    private fun collapseNotesSearch(clearText: Boolean = true, hideKeyboard: Boolean = true) {
        isNotesSearchExpanded = false
        if (clearText) {
            binding.NotesSearchInput?.setText("")
            notesSearchListener?.invoke("")
        }
        binding.NotesSearchHint?.visibility = View.VISIBLE
        binding.NotesSearchInput?.visibility = View.GONE
        binding.NotesSearchButton?.setImageResource(R.drawable.ic_search)
        if (hideKeyboard) {
            hideKeyboard(binding.NotesSearchInput)
        }
            }

    private fun refreshDrawer() {
        drawerEntries = buildDrawerEntries(labels)
        drawerAdapter?.submitList(drawerEntries)
        drawerAdapter?.selectedId = selectedDrawerId
    }

    private fun buildDrawerEntries(labels: List<String>): List<DrawerEntry> {
        val list = mutableListOf<DrawerEntry>()
        // Notes
        list.add(DrawerEntry.Item("notes", getString(R.string.notes), R.drawable.notebook))
        // Study Sets (học từ vựng) ngay dưới Notes
        list.add(
            DrawerEntry.Item(
                "studysets",
                getString(R.string.study_sets_title),
                R.drawable.checkbox,
            )
        )
        list.add(
            DrawerEntry.Item(
                "unlabeled",
                getString(R.string.unlabeled),
                R.drawable.label_off,
            )
        )
        list.add(
            DrawerEntry.Item(
                "reminders",
                getString(R.string.reminders),
                R.drawable.notifications,
            )
        )
        if (labels.isNotEmpty()) {
            list.add(DrawerEntry.Section("labels_header", getString(R.string.labels)))
            list.addAll(labels.map { DrawerEntry.Child(id = "label:$it", title = it) })
        }
        list.add(DrawerEntry.Section("storage_header", getString(R.string.nav_storage)))
        list.add(
            DrawerEntry.Item(
                "archived",
                getString(R.string.archived),
                R.drawable.archive,
            )
        )
        list.add(
            DrawerEntry.Item(
                "deleted",
                getString(R.string.deleted),
                R.drawable.delete,
            )
        )
        list.add(DrawerEntry.Section("settings_header", getString(R.string.settings)))
        list.add(
            DrawerEntry.Item(
                "settings",
                getString(R.string.settings),
                R.drawable.settings,
            )
        )
        list.add(
            DrawerEntry.Item(
                "logout",
                getString(R.string.logout),
                R.drawable.ic_arrow_right,
            )
        )
        return list
    }

    private fun handleDrawerItemClick(id: String) {
        when {
            id == "notes" -> navigateWithAnimation(R.id.Notes)
            id == "studysets" -> navigateWithAnimation(R.id.Checklist)
            id == "unlabeled" -> navigateWithAnimation(R.id.Unlabeled)
            id.startsWith("label:") -> {
                val label = id.removePrefix("label:")
                            navigateToLabel(label)
            }
            id == "deleted" -> navigateWithAnimation(R.id.Deleted)
            id == "archived" -> navigateWithAnimation(R.id.Archived)
            id == "reminders" -> navigateWithAnimation(R.id.Reminders)
            id == "settings" -> navigateWithAnimation(R.id.Settings)
            id == "logout" -> {
                // Placeholder: sau này sẽ xử lý đăng xuất (Google, v.v.)
                // Hiện tại chỉ đóng drawer và về Home
                navigateWithAnimation(R.id.Home)
            }
        }
        selectedDrawerId = id
        drawerAdapter?.selectedId = selectedDrawerId
        binding.DrawerLayout.closeDrawer(GravityCompat.START)
    }
    private fun setupMenu() {
        setupDrawerRecycler()
    }

    private fun navigateToLabel(label: String) {
        val bundle = Bundle().apply { putString(EXTRA_DISPLAYED_LABEL, label) }
        navController.navigate(R.id.DisplayLabel, bundle)
    }

    private fun setupActionMode() {
        // Ẩn ActionMode toolbar cũ - không dùng nữa
        binding.ActionMode.visibility = View.GONE
        
        // Setup click listeners cho các nút action mode trong NotesToolbar
        binding.NotesMenuButton?.setOnClickListener {
            if (baseModel.actionMode.isEnabled()) {
                // Trong action mode: nút X để close
                baseModel.actionMode.close(true)
            } else {
                // Normal mode: mở drawer
                binding.DrawerLayout.openDrawer(GravityCompat.START)
            }
        }
        
        binding.NotesLabelButton?.setOnClickListener {
            label()
        }
        
        binding.NotesSelectAllButton?.setOnClickListener {
            getCurrentFragmentNotes?.invoke()?.let { baseModel.actionMode.add(it) }
        }
        
        binding.NotesSearchButton?.setOnClickListener {
            if (baseModel.actionMode.isEnabled()) {
                // Trong action mode: delete
                moveNotes(Folder.DELETED)
            } else {
                // Normal mode: toggle search
                toggleNotesSearch()
            }
        }
        
        // More button sẽ được setup trong setupNotesTopBar và update khi action mode

        val transition =
            MaterialFade().apply {
                secondaryAnimatorProvider = null
                excludeTarget(binding.NavHostFragment, true)
                excludeChildren(binding.NavHostFragment, true)
                excludeTarget(binding.TakeNote, true)
                excludeTarget(binding.MakeList, true)
                excludeTarget(binding.MainFab, true)
                excludeTarget(binding.FabContainer, true)
            }

        baseModel.actionMode.enabled.observe(this) { enabled ->
            TransitionManager.beginDelayedTransition(binding.RelativeLayout, transition)
            if (enabled) {
                // Chuyển NotesToolbar sang action mode
                switchNotesToolbarToActionMode()
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                // Trả NotesToolbar về normal mode
                switchNotesToolbarToNormalMode()
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
            actionModeCancelCallback.isEnabled = enabled
        }

        // Setup action mode menu cho more button khi folder thay đổi
        baseModel.folder.observe(this@MainActivity) { folder ->
            // Chỉ update menu nếu đang trong action mode
            if (baseModel.actionMode.isEnabled()) {
                setupActionModeMoreMenu(folder)
            }
        }
        
        // Update count trong toolbar (có thể hiển thị ở đâu đó nếu cần)
        baseModel.actionMode.count.observe(this) { count ->
            // Có thể hiển thị count ở đâu đó nếu cần
        }
    }
    
    private fun switchNotesToolbarToActionMode() {
        // Đổi icon menu thành X
        binding.NotesMenuButton?.setImageResource(R.drawable.close)
        binding.NotesMenuButton?.contentDescription = getString(R.string.cancel)
        
        // Ẩn search hint và input - đảm bảo hoàn toàn ẩn
        binding.NotesSearchHint?.visibility = View.GONE
        binding.NotesSearchHint?.alpha = 0f
        binding.NotesSearchInput?.visibility = View.GONE
        binding.NotesSearchInput?.alpha = 0f
        collapseNotesSearch(clearText = true, hideKeyboard = true)
        
        // Hiển thị label và select all buttons
        binding.NotesLabelButton?.visibility = View.VISIBLE
        binding.NotesSelectAllButton?.visibility = View.VISIBLE
        
        // Đổi icon search thành delete
        binding.NotesSearchButton?.setImageResource(R.drawable.delete)
        binding.NotesSearchButton?.contentDescription = getString(R.string.delete)
        
        // More button: setup action mode menu
        setupActionModeMoreMenu(baseModel.folder.value ?: Folder.NOTES)
    }
    
    private fun switchNotesToolbarToNormalMode() {
        // Đổi icon X thành menu
        binding.NotesMenuButton?.setImageResource(R.drawable.menu)
        binding.NotesMenuButton?.contentDescription = getString(R.string.navigation_drawer_open)
        
        // Hiển thị search hint
        binding.NotesSearchHint?.visibility = View.VISIBLE
        binding.NotesSearchHint?.alpha = 1f
        binding.NotesSearchInput?.visibility = View.GONE
        binding.NotesSearchInput?.alpha = 1f
        
        // Ẩn label và select all buttons
        binding.NotesLabelButton?.visibility = View.GONE
        binding.NotesSelectAllButton?.visibility = View.GONE
        
        // Đổi icon delete thành search
        binding.NotesSearchButton?.setImageResource(R.drawable.ic_search)
        binding.NotesSearchButton?.contentDescription = getString(R.string.search)
        
        // More button trở về menu appearance
        binding.NotesMoreButton?.setOnClickListener {
            showAppearancePopup(it)
        }
    }
    
    private fun setupActionModeMoreMenu(folder: Folder) {
        // Update more button click listener khi folder thay đổi
        // Chỉ update nếu đang trong action mode
        if (!baseModel.actionMode.isEnabled()) {
            return
        }
        
        // Tạo PopupMenu cho more button trong action mode
        binding.NotesMoreButton?.setOnClickListener {
            val popup = PopupMenu(this, it)
            val menu = popup.menu
            
            when (folder) {
                Folder.NOTES -> {
                    val pinnedItem = menu.add(R.string.pin, R.drawable.pin) {
                        val baseNotes = baseModel.actionMode.selectedNotes.values
                        if (baseNotes.any { !it.pinned }) {
                            baseModel.pinBaseNotes(true)
                        } else {
                            baseModel.pinBaseNotes(false)
                        }
                    }
                    // Update pin/unpin text và icon dựa trên selected notes
                    val baseNotes = baseModel.actionMode.selectedNotes.values
                    if (baseNotes.any { !it.pinned }) {
                        pinnedItem.setTitle(R.string.pin).setIcon(R.drawable.pin)
                    } else {
                        pinnedItem.setTitle(R.string.unpin).setIcon(R.drawable.unpin)
                    }
                    menu.add(R.string.archive, R.drawable.archive) {
                        moveNotes(Folder.ARCHIVED)
                    }
                    menu.add(R.string.change_color, R.drawable.change_color) {
                        lifecycleScope.launch {
                            val colors = withContext(Dispatchers.IO) {
                                NotallyDatabase.getDatabase(
                                    this@MainActivity,
                                    observePreferences = false,
                                ).value.getBaseNoteDao().getAllColors()
                            }
                            val currentColor = baseModel.actionMode.selectedNotes.values
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
                                        baseModel.changeColor(oldColor, selectedColor)
                                    }
                                    baseModel.colorBaseNote(selectedColor)
                                },
                            ) { colorToDelete, newColor ->
                                baseModel.changeColor(colorToDelete, newColor)
                            }
                        }
                    }
                    menu.add(R.string.share, R.drawable.share) {
                        if (baseModel.actionMode.selectedNotes.size == 1) {
                            share()
                        }
                    }.isVisible = baseModel.actionMode.selectedNotes.size == 1
                    // Export - hiện dialog với các options
                    menu.add(R.string.export, R.drawable.export) {
                        val exportOptions = ExportMimeType.entries.map { it.name }.toTypedArray()
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.export)
                            .setItems(exportOptions) { _, which ->
                                exportSelectedNotes(ExportMimeType.entries[which])
                            }
                            .show()
                    }
                }
                Folder.ARCHIVED -> {
                    menu.add(R.string.unarchive, R.drawable.unarchive) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.add(R.string.change_color, R.drawable.change_color) {
                        lifecycleScope.launch {
                            val colors = withContext(Dispatchers.IO) {
                                NotallyDatabase.getDatabase(
                                    this@MainActivity,
                                    observePreferences = false,
                                ).value.getBaseNoteDao().getAllColors()
                            }
                            val currentColor = baseModel.actionMode.selectedNotes.values
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
                                        baseModel.changeColor(oldColor, selectedColor)
                                    }
                                    baseModel.colorBaseNote(selectedColor)
                                },
                            ) { colorToDelete, newColor ->
                                baseModel.changeColor(colorToDelete, newColor)
                            }
                        }
                    }
                    menu.add(R.string.share, R.drawable.share) {
                        if (baseModel.actionMode.selectedNotes.size == 1) {
                            share()
                        }
                    }.isVisible = baseModel.actionMode.selectedNotes.size == 1
                }
                Folder.DELETED -> {
                    menu.add(R.string.restore, R.drawable.restore) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.add(R.string.delete_forever, R.drawable.delete) {
                        deleteForever()
                    }
                    menu.add(R.string.change_color, R.drawable.change_color) {
                        lifecycleScope.launch {
                            val colors = withContext(Dispatchers.IO) {
                                NotallyDatabase.getDatabase(
                                    this@MainActivity,
                                    observePreferences = false,
                                ).value.getBaseNoteDao().getAllColors()
                            }
                            val currentColor = baseModel.actionMode.selectedNotes.values
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
                                        baseModel.changeColor(oldColor, selectedColor)
                                    }
                                    baseModel.colorBaseNote(selectedColor)
                                },
                            ) { colorToDelete, newColor ->
                                baseModel.changeColor(colorToDelete, newColor)
                            }
                        }
                    }
                    menu.add(R.string.share, R.drawable.share) {
                        if (baseModel.actionMode.selectedNotes.size == 1) {
                            share()
                        }
                    }.isVisible = baseModel.actionMode.selectedNotes.size == 1
                }
            }
            
            popup.show()
        }
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

        configuration =
            AppBarConfiguration(
                setOf(
                    R.id.Home,
                    R.id.Notes,
                    R.id.DisplayLabel,
                    R.id.Unlabeled,
                    R.id.Deleted,
                    R.id.Archived,
                    R.id.Reminders,
                    R.id.Settings,
                ),
                binding.DrawerLayout,
            )
        setupActionBarWithNavController(navController, configuration)

        navController.addOnDestinationChangedListener { _, destination, bundle ->
            // Ẩn hamburger menu ở Home
            if (destination.id == R.id.Home) {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                binding.Toolbar.navigationIcon = null
            } else {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    }
            
            selectedDrawerId =
                when (destination.id) {
                    R.id.DisplayLabel -> {
                        val label = bundle?.getString(EXTRA_DISPLAYED_LABEL)
                        baseModel.currentLabel = label ?: CURRENT_LABEL_EMPTY
                        label?.let { "label:$it" } ?: "labels"
                    }
                R.id.Unlabeled -> {
                    baseModel.currentLabel = CURRENT_LABEL_NONE
                        "unlabeled"
                }
                    R.id.Deleted -> "deleted"
                    R.id.Archived -> "archived"
                    R.id.Reminders -> "reminders"
                    R.id.Settings -> "settings"
                else -> {
                    baseModel.currentLabel = CURRENT_LABEL_EMPTY
                        "notes"
                }
            }
            drawerAdapter?.selectedId = selectedDrawerId
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

            // Toolbar luôn transparent để background full màn hình hiển thị - suôn sẽ mượt mà
            binding.Toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.Toolbar.elevation = 0f
            binding.Toolbar.background = null
            binding.ActionMode.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.ActionMode.elevation = 0f
            binding.ActionMode.background = null
            
            // Ẩn/hiện toolbar tùy theo fragment
            if (destination.id == R.id.Home) {
                supportActionBar?.setDisplayShowTitleEnabled(false)
                binding.Toolbar?.isVisible = false // Ẩn toolbar mặc định
                binding.HomeToolbar?.isVisible = true // Hiển thị Home toolbar
                binding.NotesToolbar?.isVisible = false
                collapseNotesSearch(clearText = true, hideKeyboard = true)
            } else if (destination.id == R.id.Notes || destination.id == R.id.DisplayLabel || destination.id == R.id.Unlabeled || destination.id == R.id.Checklist) {
                // Hiển thị Notes toolbar (giống Home nhưng không có avatar và có nút more)
                supportActionBar?.setDisplayShowTitleEnabled(false)
                binding.Toolbar?.isVisible = false // Ẩn toolbar mặc định
                binding.HomeToolbar?.isVisible = false // Đảm bảo HomeToolbar bị ẩn
                binding.NotesToolbar?.isVisible = true // Hiển thị Notes toolbar
                // Đảm bảo icon more luôn hiển thị
                binding.NotesMoreButton?.visibility = View.VISIBLE
                binding.NotesMoreButton?.isVisible = true
                collapseSearch(clearText = true, hideKeyboard = true)
                collapseNotesSearch(clearText = true, hideKeyboard = true)
            } else {
                supportActionBar?.setDisplayShowTitleEnabled(true)
                binding.Toolbar?.isVisible = true // Hiển thị toolbar mặc định
                binding.HomeToolbar?.isVisible = false
                binding.NotesToolbar?.isVisible = false
                collapseSearch(clearText = true, hideKeyboard = true)
                collapseNotesSearch(clearText = true, hideKeyboard = true)
            }
            
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

        // Set background drawable glassy bo tròn cho chính ExpandableBottomBar
        bottomBar.setBackgroundResource(R.drawable.bg_bottom_bar_app)

        // Map menu item IDs với destination IDs
        val menuItemToDestinationMap = mapOf(
            R.id.icon_home to R.id.Home,
            R.id.icon_notes to R.id.Notes,
            R.id.icon_list to R.id.Checklist,
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
            R.id.Home to R.id.icon_home,
            R.id.Notes to R.id.icon_notes,
            R.id.Checklist to R.id.icon_list,
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

    fun setupHomeAvatarButton(onClick: () -> Unit) {
        binding.HomeAppAvatarCard?.setOnClickListener { onClick() }
    }
    
    fun setupHomeSearch(onQueryChange: (String) -> Unit) {
        homeSearchListener = onQueryChange
    }
    
    fun setupNotesSearch(onQueryChange: (String) -> Unit) {
        notesSearchListener = onQueryChange
    }
    
    fun setupHomeAddButton(onClick: () -> Unit) {
        homeAddListener = onClick
        binding.HomeAddButton?.setOnClickListener { homeAddListener?.invoke() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup Handler to prevent memory leaks
        searchDebounceHandler.removeCallbacksAndMessages(null)
        // Cleanup FAB postDelayed runnables
        fabPostRunnables.forEach { runnable ->
            try {
                binding.root.removeCallbacks(runnable)
                binding.TakeNote?.removeCallbacks(runnable)
                binding.MakeList?.removeCallbacks(runnable)
            } catch (e: Exception) {
                // Ignore if views are already destroyed
            }
        }
        fabPostRunnables.clear()
        // Cancel animations
        try {
            binding.MainFab?.clearAnimation()
            binding.TakeNote?.clearAnimation()
            binding.MakeList?.clearAnimation()
        } catch (e: Exception) {
            // Ignore if views are already destroyed
        }
        // Clear listeners to prevent leaks
        homeSearchListener = null
        homeAddListener = null
        notesSearchListener = null
        getCurrentFragmentNotes = null
        // Remove observers
        labelsLiveData?.removeObservers(this)
        labelsLiveData = null
    }

    companion object {
        const val EXTRA_FRAGMENT_TO_OPEN = "notallyx.intent.extra.FRAGMENT_TO_OPEN"
    }
}
