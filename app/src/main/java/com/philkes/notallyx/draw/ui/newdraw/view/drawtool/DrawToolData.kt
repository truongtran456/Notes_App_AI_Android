package com.philkes.notallyx.draw.ui.newdraw.view.drawtool

import android.content.Context
import androidx.annotation.DrawableRes
import com.philkes.notallyx.R
import com.philkes.notallyx.common.model.Brush
import com.philkes.notallyx.common.model.BrushGroup
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.common.model.getDescription
import java.util.UUID

object DrawToolData {

    val eraserPen =
        DrawToolBrush(
            id = UUID.randomUUID(),
            brush = Brush.HardEraser,
            type = DrawToolPenType.ERASER,
            sliderSize = 100f,
        )

    val addPen =
        DrawToolBrush(
            id = UUID.randomUUID(),
            brush = Brush.Pen,
            isAdd = true,
            type = DrawToolPenType.CUSTOM,
        )

    fun getDefault(): ArrayList<DrawToolBrush> {
        val data =
            arrayListOf(
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Pen,
                    color = "#000000",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Pencil,
                    color = "#2B2928",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.FountainPen,
                    sliderSize = 5f,
                    color = "#000000",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.AirBrush,
                    color = "#54ECDC",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.DashLine,
                    color = "#fa6474",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.NeonLine,
                    sliderSize = 40f,
                    color = "#ffb47d",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Amber3D,
                    sliderSize = 20f,
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Calligraphy,
                    sliderSize = 40f,
                    color = "#d4ff32",
                    type = DrawToolPenType.DEFAULT_PEN,
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Ruler,
                    sliderSize = 0f,
                ),
            )

        if (data.none { it.isSelected }) {
            data.firstOrNull()?.isSelected = true
        }
        return data
    }

    fun getAllDrawBrush(brushSelected: Brush): ArrayList<DrawToolBrush> {
        val pen =
            arrayListOf(
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Pen,
                    sliderSize = 10f,
                    color = "#000000",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Pencil,
                    sliderSize = 10f,
                    color = "#2B2928",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.AirBrush,
                    sliderSize = 10f,
                    color = "#54ECDC",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.Calligraphy,
                    sliderSize = 10f,
                    color = "#000000",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.FountainPen,
                    sliderSize = 10f,
                    color = "#000000",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.DashLine,
                    sliderSize = 10f,
                    color = "#fa6474",
                ),
                DrawToolBrush(
                    id = UUID.randomUUID(),
                    brush = Brush.NeonLine,
                    sliderSize = 10f,
                    color = "#ffb47d",
                ),
            )

        pen.addAll(getDrawBrush3D(brushSelected))

        pen.firstOrNull { it.brush == brushSelected }?.let {
            it.isSelected = true
        }
        return pen
    }

    private fun getDrawBrush3D(brushSelected: Brush): ArrayList<DrawToolBrush> {
        val pen3D =
            arrayListOf(
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Amber3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.LightPink3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Coral3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Coral3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Garnet3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Emerald3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Strawberry3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Rainbow3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Violet3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Sapphire3D, sliderSize = 10f),
                DrawToolBrush(id = UUID.randomUUID(), brush = Brush.Sunrise3D, sliderSize = 10f),
            )
        pen3D.firstOrNull { it.brush == brushSelected }?.let {
            it.isSelected = true
        }
        return pen3D
    }

    @DrawableRes
    fun getBrushResId(brush: Brush, isAdd: Boolean = false): Int {
        if (brush.group == BrushGroup.Pen3D) {
            return R.drawable.bg_pen_3dline_selector
        }
        return when (brush) {
            Brush.Pencil -> R.drawable.bg_pen_pencil_selector
            Brush.Pen -> R.drawable.bg_pen_crayon_selector
            Brush.Calligraphy -> R.drawable.bg_pen_highlighter_selector
            Brush.AirBrush -> R.drawable.bg_pen_airbrush_selector
            Brush.DashLine -> R.drawable.bg_pen_dashline_selector
            Brush.NeonLine -> R.drawable.bg_pen_neonline_selector
            Brush.FountainPen -> R.drawable.bg_pen_fountain_selector
            Brush.HardEraser -> R.drawable.bg_eraser_selector
            Brush.Ruler -> R.drawable.bg_ruler_selector
            Brush.SoftEraser -> R.drawable.bg_eraser_selector
            Brush.Marker -> R.drawable.stamp_marker
            Brush.Text -> R.drawable.stamp_marker
            Brush.Image -> R.drawable.bg_pen_highlighter_selector
            Brush.Amber3D -> R.drawable.stamp_3d_amber
            Brush.LightPink3D -> R.drawable.stamp_3d_lightpink
            Brush.Sapphire3D -> R.drawable.stamp_3d_sapphire
            Brush.Coral3D -> R.drawable.stamp_3d_coral
            Brush.Emerald3D -> R.drawable.stamp_3d_emerald
            Brush.Garnet3D -> R.drawable.stamp_3d_garnet
            Brush.Rainbow3D -> R.drawable.stamp_3d_rainbow
            Brush.Strawberry3D -> R.drawable.stamp_3d_strawberry
            Brush.Sunrise3D -> R.drawable.stamp_3d_sunrise
            Brush.Violet3D -> R.drawable.stamp_3d_violet
        }
    }
}

fun DrawToolBrush.getBrushDescription(context: Context): String = brush.getDescription(context)

