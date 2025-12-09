package com.philkes.notallyx.draw.ui.background

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.FragmentChangeBackgroundBottomSheetBinding
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class BackgroundBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onBackgroundSelected(@ColorInt colorInt: Int, drawableResId: Int?)
    }

    private var _binding: FragmentChangeBackgroundBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var listener: Listener? = null

    @ColorInt
    private var selectedColor: Int = Color.WHITE

    @ColorInt
    private var selectedBackgroundColor: Int? = null
    private var selectedBackgroundDrawableResId: Int? = null

    private lateinit var sectionAdapter: BackgroundSectionAdapter
    private var customColors: ArrayList<ColorCustomItem> = arrayListOf()
    private val sections: MutableList<BackgroundSection> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding =
            FragmentChangeBackgroundBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedColor =
            arguments?.getInt(ARG_INITIAL_COLOR) ?: Color.WHITE

        setupToolbar()
        setupColorPalettes()
        setupBackgroundSections()
    }

    private fun setupToolbar() {
        binding.toolbar.ivBack.setOnClickListener {
            dismiss()
        }
        binding.toolbar.ivCheck.setOnClickListener {
            val colorToApply = selectedBackgroundColor ?: selectedColor
            listener?.onBackgroundSelected(colorToApply, selectedBackgroundDrawableResId)
            dismiss()
        }
    }

    private fun setupColorPalettes() {
        // Hide the header of CustomColorView since we have our own header
        val customHeader = binding.customColorView.findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.customID)
        customHeader?.visibility = GONE
        
        // Setup edit icon click listener
        binding.ivEditColor.setOnClickListener {
            // Trigger edit mode in CustomColorView
            binding.customColorView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.ivEditCustom)?.performClick()
            // Update our external UI
            binding.ivEditColor.visibility = GONE
            binding.tvCancelColor.visibility = VISIBLE
        }
        
        // Setup cancel button click listener
        binding.tvCancelColor.setOnClickListener {
            // Trigger cancel in CustomColorView
            binding.customColorView.findViewById<android.widget.TextView>(R.id.tvCancel)?.performClick()
            // Update our external UI
            binding.ivEditColor.visibility = View.VISIBLE
            binding.tvCancelColor.visibility = GONE
        }
        
        customColors = defaultColors()
        binding.customColorView.colors = customColors
        binding.customColorView.listener =
            object : CustomColorView.OnItemClickListener {
                override fun onItemClick(colorIndex: Int) {
                    updateCustomSelection(colorIndex)
                    val color = customColors.getOrNull(colorIndex) ?: return
                    selectedColor = parseColorSafely(color.colorString)
                    // Mutual exclusive: clear background selections
                    clearBackgroundSelections()
                    selectedBackgroundColor = null
                    selectedBackgroundDrawableResId = null
                }

                override fun onAddClick(colorString: String) {
                    customColors.add(
                        ColorCustom(
                            id = UUID.randomUUID().toString(),
                            colorString = colorString,
                        ),
                    )
                    // chọn luôn màu vừa thêm
                    updateCustomSelection(customColors.lastIndex)
                    binding.customColorView.colors = customColors
                    selectedColor = parseColorSafely(colorString)
                    // Mutual exclusive: clear background selections
                    clearBackgroundSelections()
                    selectedBackgroundColor = null
                    selectedBackgroundDrawableResId = null
                }

                override fun onMoreClick() {
                    // no-op
                }

                override fun onDeleteClick(colorIndex: Int) {
                    if (colorIndex in customColors.indices) {
                        customColors.removeAt(colorIndex)
                        binding.customColorView.colors = customColors
                    }
                }

                override fun onUpdateColorClick(
                    oldColor: ColorCustomItem,
                    newColorString: String,
                ) {
                    val index =
                        customColors.indexOfFirst {
                            it is ColorCustom && it.id == (oldColor as? ColorCustom)?.id
                        }
                    if (index != -1) {
                        customColors[index] =
                            (customColors[index] as ColorCustom).copy(colorString = newColorString)
                        binding.customColorView.colors = customColors
                        selectedColor = parseColorSafely(newColorString)
                        // Mutual exclusive: clear background selections
                        clearBackgroundSelections()
                        selectedBackgroundColor = null
                        selectedBackgroundDrawableResId = null
                    }
                }
            }
    }

    private fun updateCustomSelection(selectedIndex: Int) {
        if (customColors.isEmpty()) return
        customColors =
            customColors.mapIndexed { index, item ->
                if (item is ColorCustom) {
                    item.copy(isSelected = index == selectedIndex)
                } else {
                    item
                }
            }.toCollection(arrayListOf())
        binding.customColorView.colors = customColors
    }

    private fun clearBackgroundSelections() {
        if (sections.isEmpty()) return
        sections.forEach { section ->
            section.items.forEach { it.isSelected = false }
        }
        sectionAdapter.updateSections(sections)
        selectedBackgroundDrawableResId = null
        selectedBackgroundColor = null
    }

    private fun clearColorSelections() {
        // Reset color selection state
        customColors =
            customColors.map { item ->
                if (item is ColorCustom) item.copy(isSelected = false) else item
            }.toCollection(arrayListOf())
        binding.customColorView.colors = customColors
        selectedColor = Color.WHITE
        selectedBackgroundDrawableResId = null
    }

    private fun setupBackgroundSections() {
        sections.clear()
        sections.addAll(createDefaultSections())

        sectionAdapter =
            BackgroundSectionAdapter(sections.toMutableList()) { clickedItem ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    // Mutual exclusive: clear colors when selecting background
                    clearColorSelections()
                    selectedBackgroundColor = clickedItem.colorInt
                    selectedColor = Color.WHITE
                    selectedBackgroundDrawableResId = clickedItem.drawableResId

                    // 1. Clear tất cả selections trước (quan trọng!)
                    sections.forEach { section ->
                        section.items.forEach { bgItem ->
                            bgItem.isSelected = false
                        }
                    }

                    // 2. Tìm và set selected = true cho item được click
                    var foundItem: BackgroundItem? = null
                    var foundSectionIndex = -1
                    var foundItemIndex = -1

                    sections.forEachIndexed { sectionIndex, section ->
                        section.items.forEachIndexed { itemIndex, bgItem ->
                            if (bgItem.id == clickedItem.id) {
                                bgItem.isSelected = true
                                selectedBackgroundColor = bgItem.colorInt
                                foundItem = bgItem
                                foundSectionIndex = sectionIndex
                                foundItemIndex = itemIndex
                            }
                        }
                    }

                    // 3. Update adapter với toàn bộ sections mới (QUAN TRỌNG!)
                    if (foundItem != null && foundSectionIndex >= 0 && foundItemIndex >= 0) {
                        val updatedSections =
                            sections.mapIndexed { sectionIndex, section ->
                                val updatedItems =
                                    section.items.mapIndexed { itemIndex, item ->
                                        if (sectionIndex == foundSectionIndex && itemIndex == foundItemIndex) {
                                            item.copy(isSelected = true)
                                        } else {
                                            item.copy(isSelected = false)
                                        }
                                    }.toMutableList()

                                BackgroundSection(
                                    type = section.type,
                                    title = section.title,
                                    items = updatedItems,
                                )
                            }

                        sections.clear()
                        sections.addAll(updatedSections)

                        // ✅ Chỉ update UI, apply thật sẽ làm khi bấm tick
                        sectionAdapter.updateSections(updatedSections)
                    }
                }
            }

        binding.rvBackgroundSections.apply {
            setHasFixedSize(true) // Tối ưu performance
            setItemViewCacheSize(3) // Cache 3 views
            isDrawingCacheEnabled = false // Tắt drawing cache
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = sectionAdapter
        }
    }

    private fun parseColorSafely(colorString: String): Int =
        try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Color.WHITE
        }

    private fun defaultColors(): ArrayList<ColorCustomItem> {
        // Item đầu tiên: ô "More color"
        val items = arrayListOf<ColorCustomItem>()
        items +=
            ColorCustom(
                id = UUID.randomUUID().toString(),
                colorString = "#FFFFFF",
                isMore = true,
                isSelected = false,
            )

        // Các màu mặc định (10 màu) để ra layout:
        // Hàng 1: [More] + 5 màu đầu
        // Hàng 2: 5 màu sau + [Add]
        val rawColors =
            arrayListOf(
                "#FFC48A",
                "#F9A1C6",
                "#C4E8A4",
                "#F7E2A3",
                "#C0E7F9",
                "#F2B5FF",
                "#FFE0AA",
                "#FFB3B3",
                "#C8FFC8",
                "#D4C3FF",
            )

        items +=
            rawColors.mapIndexed { index, color ->
                ColorCustom(
                    id = UUID.randomUUID().toString(),
                    colorString = color,
                    isSelected = index == 0,
                )
            }

        // Item cuối: ô "+" thêm màu
        items +=
            ColorCustom(
                id = UUID.randomUUID().toString(),
                colorString = "#FFFFFF",
                isAddColor = true,
                isSelected = false,
            )

        return items
    }

    private fun createDefaultSections(): List<BackgroundSection> {
        // Nature backgrounds - sử dụng tất cả 10 ảnh
        val natureDrawables = listOf(
            R.drawable.ic_draw_nature_1,
            R.drawable.ic_draw_nature_2,
            R.drawable.ic_draw_nature_3,
            R.drawable.ic_draw_nature_4,
            R.drawable.ic_draw_nature_5,
            R.drawable.ic_draw_nature_6,
            R.drawable.ic_draw_nature_7,
            R.drawable.ic_draw_nature_8,
            R.drawable.ic_draw_nature_9,
            R.drawable.ic_draw_nature_10,
            )
        
        // Pastel backgrounds - sử dụng tất cả 10 ảnh
        val pastelDrawables = listOf(
            R.drawable.ic_draw_pastel_1,
            R.drawable.ic_draw_pastel_2,
            R.drawable.ic_draw_pastel_3,
            R.drawable.ic_draw_pastel_4,
            R.drawable.ic_draw_pastel_5,
            R.drawable.ic_draw_pastel_6,
            R.drawable.ic_draw_pastel_7,
            R.drawable.ic_draw_pastel_8,
            R.drawable.ic_draw_pastel_9,
            R.drawable.ic_draw_pastel_10,
            )
        
        // Texture backgrounds - sử dụng tất cả 10 ảnh
        val textureDrawables = listOf(
            R.drawable.ic_draw_texture_1,
            R.drawable.ic_draw_texture_2,
            R.drawable.ic_draw_texture_3,
            R.drawable.ic_draw_texture_4,
            R.drawable.ic_draw_texture_5,
            R.drawable.ic_draw_texture_6,
            R.drawable.ic_draw_texture_7,
            R.drawable.ic_draw_texture_8,
            R.drawable.ic_draw_texture_9,
            R.drawable.ic_draw_texture_10,
            )
        
        // Custom colors - vẫn dùng màu
        val customColors =
            listOf(
                "#FFE3B8",
            )

        fun toImageItems(drawableResIds: List<Int>, type: BackgroundCategoryType): MutableList<BackgroundItem> =
            drawableResIds.mapIndexed { index, resId ->
                BackgroundItem(
                    id = UUID.randomUUID().toString(),
                    colorInt = Color.WHITE, // Màu mặc định, không dùng khi có ảnh
                    category = type,
                    isSelected = false,
                    drawableResId = resId,
                )
            }.toMutableList()

        fun toColorItems(colors: List<String>, type: BackgroundCategoryType): MutableList<BackgroundItem> =
            colors.mapIndexed { index, hex ->
                BackgroundItem(
                    id = UUID.randomUUID().toString(),
                    colorInt = parseColorSafely(hex),
                    category = type,
                    isSelected = false,
                    drawableResId = null,
                )
            }.toMutableList()

        val sections =
            listOf(
                BackgroundSection(
                    type = BackgroundCategoryType.NATURE,
                    title = "Nature",
                    items = toImageItems(natureDrawables, BackgroundCategoryType.NATURE),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.PASTEL,
                    title = "Pastel",
                    items = toImageItems(pastelDrawables, BackgroundCategoryType.PASTEL),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.TEXTURE,
                    title = "Texture",
                    items = toImageItems(textureDrawables, BackgroundCategoryType.TEXTURE),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.CUSTOM,
                    title = "Custom",
                    items = toColorItems(customColors, BackgroundCategoryType.CUSTOM),
                ),
            )

        return sections
    }

    fun setListener(backgroundListener: Listener) {
        listener = backgroundListener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_COLOR = "arg_initial_color"

        fun newInstance(@ColorInt initialColor: Int): BackgroundBottomSheet {
            val fragment = BackgroundBottomSheet()
            fragment.arguments =
                Bundle().apply {
                    putInt(ARG_INITIAL_COLOR, initialColor)
                }
            return fragment
        }
    }
}


