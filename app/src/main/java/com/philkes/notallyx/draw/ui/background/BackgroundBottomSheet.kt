package com.philkes.notallyx.draw.ui.background

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.FragmentChangeBackgroundBottomSheetBinding
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.UUID

class BackgroundBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onBackgroundSelected(@ColorInt colorInt: Int)
    }

    private var _binding: FragmentChangeBackgroundBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var listener: Listener? = null

    @ColorInt
    private var selectedColor: Int = Color.WHITE

    @ColorInt
    private var selectedBackgroundColor: Int? = null

    private lateinit var sectionAdapter: BackgroundSectionAdapter
    private var customColors: ArrayList<ColorCustomItem> = arrayListOf()

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
            listener?.onBackgroundSelected(colorToApply)
            dismiss()
        }
    }

    private fun setupColorPalettes() {
        customColors = defaultColors()
        binding.customColorView.colors = customColors
        binding.customColorView.listener =
            object : CustomColorView.OnItemClickListener {
                override fun onItemClick(colorIndex: Int) {
                    updateCustomSelection(colorIndex)
                    val color = customColors.getOrNull(colorIndex) ?: return
                    selectedColor = parseColorSafely(color.colorString)
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

    private fun setupBackgroundSections() {
        val sections = createDefaultSections()

        sectionAdapter =
            BackgroundSectionAdapter(sections.toMutableList()) { item ->
                // Clear previous selection
                sections.forEach { section ->
                    section.items.forEach { it.isSelected = false }
                }
                item.isSelected = true
                selectedBackgroundColor = item.colorInt
                sectionAdapter.updateSections(sections)
            }

        binding.rvBackgroundSections.apply {
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
        val natureColors =
            listOf(
                "#E3F5E1",
                "#D1F0D8",
                "#F2F8E9",
                "#E1F0FF",
            )
        val pastelColors =
            listOf(
                "#CDEDEB",
                "#FAD3CF",
                "#FBE6C9",
                "#D7ECFF",
            )
        val textureColors =
            listOf(
                "#E6E2DD",
                "#F0EEE8",
                "#E9E4DE",
                "#F3EFE9",
            )
        val customColors =
            listOf(
                "#FFE3B8",
            )

        fun toItems(colors: List<String>, type: BackgroundCategoryType): MutableList<BackgroundItem> =
            colors.mapIndexed { index, hex ->
                BackgroundItem(
                    id = UUID.randomUUID().toString(),
                    colorInt = parseColorSafely(hex),
                    category = type,
                    isSelected = false,
                )
            }.toMutableList()

        val sections =
            listOf(
                BackgroundSection(
                    type = BackgroundCategoryType.NATURE,
                    title = "Nature",
                    items = toItems(natureColors, BackgroundCategoryType.NATURE),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.PASTEL,
                    title = "Pastel",
                    items = toItems(pastelColors, BackgroundCategoryType.PASTEL),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.TEXTURE,
                    title = "Texture",
                    items = toItems(textureColors, BackgroundCategoryType.TEXTURE),
                ),
                BackgroundSection(
                    type = BackgroundCategoryType.CUSTOM,
                    title = "Custom",
                    items = toItems(customColors, BackgroundCategoryType.CUSTOM),
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


