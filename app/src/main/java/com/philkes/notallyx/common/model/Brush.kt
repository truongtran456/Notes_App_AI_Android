package com.philkes.notallyx.common.model

import android.content.Context
import com.philkes.notallyx.R

enum class Brush(
    val group: BrushGroup = BrushGroup.Default,
    val stampRes: Int = 0
) {
    Pencil,
    Pen,
    Calligraphy,
    AirBrush,
    Marker,
    DashLine,
    NeonLine,
    HardEraser,
    Ruler,
    SoftEraser,
    FountainPen,
    Text,
    Image,

    Amber3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_amber),
    LightPink3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_lightpink),
    Sapphire3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_sapphire),
    Coral3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_coral),
    Emerald3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_emerald),
    Garnet3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_garnet),
    Rainbow3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_rainbow),
    Strawberry3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_strawberry),
    Sunrise3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_sunrise),
    Violet3D(group = BrushGroup.Pen3D, stampRes = R.drawable.stamp_3d_violet)
}

val Brush.brushId: String
    get() = brushName.lowercase().replace(" ", "_")

val Brush.brushName: String
    get() = when (this) {
        Brush.Pencil -> "Pencil"
        Brush.Pen -> "Crayon"
        Brush.Calligraphy -> "Highlighter Pen"
        Brush.AirBrush -> "Air Brush"
        Brush.Marker -> "Market"
        Brush.DashLine -> "Dash Line"
        Brush.NeonLine -> "Neon Line"
        Brush.HardEraser -> "Hard Eraser"
        Brush.Ruler -> "Ruler"
        Brush.SoftEraser -> "Soft Eraser"
        Brush.FountainPen -> "Fountain Pen"
        Brush.Amber3D -> "3D Amber"
        Brush.LightPink3D -> "3D Light Pink"
        Brush.Sapphire3D -> "3D Sapphire"
        Brush.Coral3D -> "3D Coral"
        Brush.Emerald3D -> "3D Emerald"
        Brush.Garnet3D -> "3D Garnet"
        Brush.Rainbow3D -> "3D Rainbow"
        Brush.Strawberry3D -> "3D Strawberry"
        Brush.Sunrise3D -> "3D Sunrise"
        Brush.Violet3D -> "3D Violet"
        Brush.Text -> "text"
        Brush.Image -> "image"
    }

fun Brush.getDescription(context: Context): String {
    return when (this) {
        Brush.Pencil -> "Pencil"
        Brush.Pen -> "Crayon"
        Brush.Calligraphy -> "Highlighter Pen"
        Brush.AirBrush -> "Air Brush"
        Brush.Marker -> "Market"
        Brush.DashLine -> "Dash Line"
        Brush.NeonLine -> "Neon Line"
        Brush.HardEraser -> "Hard Eraser"
        Brush.Ruler -> "Ruler"
        Brush.SoftEraser -> "Soft Eraser"
        Brush.FountainPen -> "Fountain Pen"
        Brush.Amber3D -> "3D Amber"
        Brush.LightPink3D -> "3D Light Pink"
        Brush.Sapphire3D -> "3D Sapphire"
        Brush.Coral3D -> "3D Coral"
        Brush.Emerald3D -> "3D Emerald"
        Brush.Garnet3D -> "3D Garnet"
        Brush.Rainbow3D -> "3D Rainbow"
        Brush.Strawberry3D -> "3D Strawberry"
        Brush.Sunrise3D -> "3D Sunrise"
        Brush.Violet3D -> "3D Violet"
        Brush.Text -> "text"
        Brush.Image -> "image"
    }
}