package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker

import android.content.Context
import android.os.Parcelable
import com.philkes.notallyx.R
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class ColorType(val value: String) {
    COLOR("default"),
    COLOR_PALETTE("custom"),
}

fun ColorType.description(context: Context): String =
    when (this) {
        ColorType.COLOR -> context.getString(R.string.draw_color)
        ColorType.COLOR_PALETTE -> context.getString(R.string.draw_color_palette)
    }

@Parcelize
data class ColorPaletteOption(
    val id: String,
    override var colorString: String,
    override var isSelected: Boolean = false,
    var type: ColorType,
    var isDefault: Boolean = false,
    override val isNone: Boolean = false,
    override val isMore: Boolean = false,
    override val isAddColor: Boolean = false,
) : ColorPickerItem, Parcelable {

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun colorDefault(context: Context): ArrayList<ColorPickerItem> {
            val rawColors =
                arrayListOf(
                    "#FFD93A",
                    "#A6FFA4",
                    "#FF8FD9",
                    "#BD8AFF",
                    "#DDFF94",
                    "#FFA4A4",
                    "#DFCF96",
                    "#BFDF96",
                    "#DCA898",
                    "#CEDBAA",
                    "#D7A6DF",
                    "#A8A7D6",
                    "#AFE1E1",
                    "#B1E7B0",
                    "#DCB0A6",
                    "",
                )

            return rawColors
                .mapIndexed { index, color ->
                    ColorPaletteOption(
                        id = UUID.randomUUID().toString(),
                        colorString = color,
                        isSelected = index == 0,
                        isMore = index == rawColors.size - 1,
                        type = ColorType.COLOR,
                    )
                }.toArrayList()
        }

        @Suppress("UNUSED_PARAMETER")
        fun colorPaletteDefault(context: Context): ArrayList<ColorPickerItem> {
            val rawColors =
                arrayListOf(
                    "#91B7BE",
                    "#F1CAB0",
                    "#E8B4B1",
                    "#95A8B1",
                    "#F8BAB7",
                    "#F4D8B0",
                    "#C4DDD9",
                    "#C7C0E7",
                    "#DCDBA4",
                    "#E3CDC1",
                    "#CFB7AA",
                    "#D6BDDD",
                )

            return rawColors
                .map { color ->
                    ColorPaletteOption(
                        id = UUID.randomUUID().toString(),
                        colorString = color,
                        type = ColorType.COLOR_PALETTE,
                    )
                }.toArrayList()
        }
    }
}

fun <T> List<T>.toArrayList(): ArrayList<T> = ArrayList(this)

