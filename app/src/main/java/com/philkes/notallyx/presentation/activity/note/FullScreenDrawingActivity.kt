package com.philkes.notallyx.presentation.activity.note

import android.animation.ObjectAnimator
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.philkes.notallyx.R
import com.philkes.notallyx.common.datasource.AppSharePrefs
import com.philkes.notallyx.common.datasource.AppSharePrefsImpl
import com.philkes.notallyx.common.extension.rawColor
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.databinding.ActivityFullScreenDrawingBinding
import com.philkes.notallyx.draw.ui.background.BackgroundBottomSheet
import com.philkes.notallyx.draw.ui.newdraw.view.canvas.DrawingStroke
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolData
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolPickerView
import com.philkes.notallyx.presentation.showToast

class FullScreenDrawingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_STROKES = "extra_initial_strokes"
        const val RESULT_STROKES = "result_strokes"
    }

    private lateinit var binding: ActivityFullScreenDrawingBinding
    private val appSharePrefs: AppSharePrefs by lazy {
        val sharedPrefs =
            getSharedPreferences(AppSharePrefsImpl.Keys.SHARED_PREFS_NAME, MODE_PRIVATE)
        val gson = Gson()
        AppSharePrefsImpl(sharedPrefs, gson)
    }
    private val gson = Gson()
    private val strokesType = object : TypeToken<List<DrawingStroke>>() {}.type

    private var currentDrawTool: DrawToolBrush? = null
    private val drawTools by lazy { DrawToolData.getDefault() }
    private var drawingBackgroundColor: Int = Color.WHITE
    private var drawingBackgroundDrawableResId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable window transitions for Shared Element Transition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
            window.enterTransition = android.transition.Fade()
            window.exitTransition = android.transition.Fade()
        }

        // Full screen mode
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        enableEdgeToEdge()

        binding = ActivityFullScreenDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom,
                left = systemBars.left,
                right = systemBars.right,
            )
            insets
        }

        setupToolbar()
        setupCanvas()
        setupDrawToolPicker()

        // Start animations
        startEnterAnimations()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnUndo.setOnClickListener { showToast("Undo") }

        binding.btnRedo.setOnClickListener { showToast("Redo") }

        binding.btnGrid.setOnClickListener { showToast("Grid") }

        binding.btnSave.setOnClickListener { saveAndFinish() }
    }

    private fun startEnterAnimations() {
        binding.root.postDelayed(
            {
                if (binding.DrawingCanvas.alpha == 0f) {
                    binding.DrawingCanvas.alpha = 1f
                    binding.DrawingCanvas.scaleX = 1f
                    binding.DrawingCanvas.scaleY = 1f
                }

                val currentAlpha = binding.DrawingCanvas.alpha
                val currentScaleX = binding.DrawingCanvas.scaleX
                val currentScaleY = binding.DrawingCanvas.scaleY

                if (currentAlpha < 1f || currentScaleX < 1f || currentScaleY < 1f) {
                    val canvasAnimator =
                        ObjectAnimator.ofFloat(binding.DrawingCanvas, "alpha", currentAlpha, 1f)
                    canvasAnimator.duration = 1500
                    canvasAnimator.interpolator = DecelerateInterpolator()

                    val scaleXAnimator =
                        ObjectAnimator.ofFloat(binding.DrawingCanvas, "scaleX", currentScaleX, 1f)
                    scaleXAnimator.duration = 2500
                    scaleXAnimator.interpolator = DecelerateInterpolator()

                    val scaleYAnimator =
                        ObjectAnimator.ofFloat(binding.DrawingCanvas, "scaleY", currentScaleY, 1f)
                    scaleYAnimator.duration = 2500
                    scaleYAnimator.interpolator = DecelerateInterpolator()

                    canvasAnimator.start()
                    scaleXAnimator.start()
                    scaleYAnimator.start()
                }

                binding.DrawToolPickerView.post {
                    val toolbarHeight = binding.DrawToolPickerView.height
                    val translationY = if (toolbarHeight > 0) toolbarHeight.toFloat() else 300f

                    if (binding.DrawToolPickerView.translationY == 0f) {
                        binding.DrawToolPickerView.translationY = translationY
                    }
                    if (binding.DrawToolPickerView.alpha == 0f) {
                        binding.DrawToolPickerView.alpha = 0f
                    }

                    val slideAnimator =
                        ObjectAnimator.ofFloat(
                            binding.DrawToolPickerView,
                            "translationY",
                            binding.DrawToolPickerView.translationY,
                            0f,
                        )
                    slideAnimator.duration = 1500
                    slideAnimator.interpolator = AnticipateOvershootInterpolator(1.8f)

                    val fadeAnimator =
                        ObjectAnimator.ofFloat(
                            binding.DrawToolPickerView,
                            "alpha",
                            binding.DrawToolPickerView.alpha,
                            1f,
                        )
                    fadeAnimator.duration = 1500
                    fadeAnimator.interpolator = DecelerateInterpolator()

                    slideAnimator.start()
                    fadeAnimator.start()
                }

                binding.blurOverlay.visibility = View.VISIBLE
                val blurAnimator = ObjectAnimator.ofFloat(binding.blurOverlay, "alpha", 0f, 0.3f)
                blurAnimator.duration = 400
                blurAnimator.interpolator = DecelerateInterpolator()
                blurAnimator.start()
            },
            100,
        )
    }

    private fun setupCanvas() {
        val initialStrokesJson = intent.getStringExtra(EXTRA_INITIAL_STROKES)
        if (initialStrokesJson != null) {
            try {
                val strokes = gson.fromJson<List<DrawingStroke>>(initialStrokesJson, strokesType)
                if (strokes != null && strokes.isNotEmpty()) {
                    binding.DrawingCanvas.loadStrokes(strokes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupDrawToolPicker() {
        val defaultBrushes = ArrayList<DrawToolBrush>(drawTools)
        val savedCustomBrushes = appSharePrefs.drawToolBrushes

        val toolsToShow = ArrayList<DrawToolBrush>(defaultBrushes)
        toolsToShow.addAll(savedCustomBrushes)

        binding.DrawToolPickerView.listener =
            object : DrawToolPickerView.OnItemClickListener {
                override fun onDoneClick() {}

                override fun onItemClick(tool: DrawToolBrush) {
                    currentDrawTool = tool
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

                    val defaultBrushes = ArrayList<DrawToolBrush>(drawTools)
                    val updatedTools = ArrayList<DrawToolBrush>(defaultBrushes)
                    updatedTools.addAll(currentCustomBrushes)
                    binding.DrawToolPickerView.applyTools(updatedTools)

                    showToast(getString(R.string.saved_to_device))
                }

                override fun onDelete(tool: DrawToolBrush) {
                    val currentCustomBrushes = appSharePrefs.drawToolBrushes
                    currentCustomBrushes.removeAll { it.id == tool.id }
                    appSharePrefs.drawToolBrushes = currentCustomBrushes

                    val defaultBrushes = ArrayList<DrawToolBrush>(drawTools)
                    val updatedTools = ArrayList<DrawToolBrush>(defaultBrushes)
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
                        val updatedBrush = currentBrush.copy(color = newColorHex, isSelected = true)

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
                    enableEyeDropperMode()
                }

                override fun onBackgroundClick() {
                    val initialColor = Color.WHITE
                    val sheet = BackgroundBottomSheet.newInstance(initialColor)
                    sheet.setListener(
                        object : BackgroundBottomSheet.Listener {
                            override fun onBackgroundSelected(colorInt: Int, drawableResId: Int?) {
                                if (drawableResId != null) {
                                    binding.DrawingCanvas.setCanvasBackgroundDrawable(drawableResId)
                                    drawingBackgroundDrawableResId = drawableResId
                                } else {
                                    binding.DrawingCanvas.setCanvasBackgroundColor(colorInt)
                                    drawingBackgroundDrawableResId = null
                                }
                                drawingBackgroundColor = colorInt
                            }
                        }
                    )
                    sheet.show(supportFragmentManager, "BackgroundBottomSheet")
                }
            }

        binding.DrawToolPickerView.applyTools(toolsToShow)
        applySavedBackgroundToCanvas()
    }

    private fun enableEyeDropperMode() {
        binding.DrawingCanvas.setEyeDropperMode(true)
        binding.DrawingCanvas.setOnColorPickedListener { color ->
            currentDrawTool?.let { tool ->
                val colorHex = String.format("#%06X", 0xFFFFFF and color)
                val updatedTool = tool.copy(color = colorHex, isSelected = true)
                currentDrawTool = updatedTool
                binding.DrawingCanvas.setBrush(updatedTool)
                showToast("Color selected: $colorHex")
            }
        }
    }

    private fun saveAndFinish() {
        val strokes = binding.DrawingCanvas.getStrokes()
        val strokesJson = gson.toJson(strokes, strokesType)

        val resultIntent = Intent().apply { putExtra(RESULT_STROKES, strokesJson) }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        saveAndFinish()
    }

    private fun applySavedBackgroundToCanvas() {
        drawingBackgroundDrawableResId?.let {
            binding.DrawingCanvas.setCanvasBackgroundDrawable(it)
        } ?: run { binding.DrawingCanvas.setCanvasBackgroundColor(drawingBackgroundColor) }
    }
}
