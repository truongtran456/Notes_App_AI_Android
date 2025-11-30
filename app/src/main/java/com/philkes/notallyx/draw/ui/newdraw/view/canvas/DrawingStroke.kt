package com.philkes.notallyx.draw.ui.newdraw.view.canvas

import android.graphics.Path
import android.graphics.Paint
import android.os.Parcelable
import com.philkes.notallyx.common.model.Brush
import kotlinx.parcelize.Parcelize

/**
 * Data class để lưu thông tin một stroke đã vẽ
 * Sử dụng để persist drawing và restore lại sau
 */
@Parcelize
data class DrawingStroke(
    val id: String,
    val pathData: String, // Path được serialize thành String
    val brush: Brush, // Loại brush (Pen, Pencil, Eraser, etc.)
    val color: String, // Màu dạng hex string "#FF0000"
    val size: Float, // Kích thước stroke (0-100)
    val opacity: Float, // Độ trong suốt (0-1)
    val rectLeft: Float, // Boundary của stroke
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float
) : Parcelable {
    companion object {
        /**
         * Convert Path thành String để lưu
         * Sử dụng cách đơn giản: lưu tất cả các điểm của path
         */
        fun pathToString(path: Path): String {
            // Sử dụng PathMeasure để lấy tất cả các điểm
            val pathMeasure = android.graphics.PathMeasure(path, false)
            val length = pathMeasure.length
            
            if (length <= 0) return ""
            
            val points = mutableListOf<String>()
            val coords = FloatArray(2)
            val tan = FloatArray(2)
            
            // Sample mỗi 2px để đảm bảo độ chính xác
            var distance = 0f
            while (distance < length) {
                pathMeasure.getPosTan(distance, coords, tan)
                points.add("${coords[0]},${coords[1]}")
                distance += 2f
            }
            
            // Lấy điểm cuối cùng
            pathMeasure.getPosTan(length, coords, tan)
            points.add("${coords[0]},${coords[1]}")
            
            return points.joinToString("|")
        }
        
        /**
         * Convert String thành Path để restore
         */
        fun stringToPath(pathString: String): Path {
            val path = Path()
            if (pathString.isEmpty()) return path
            
            val points = pathString.split("|")
            if (points.isEmpty()) return path
            
            // Điểm đầu tiên
            val firstPoint = points[0].split(",")
            if (firstPoint.size == 2) {
                val x = firstPoint[0].toFloatOrNull() ?: 0f
                val y = firstPoint[1].toFloatOrNull() ?: 0f
                path.moveTo(x, y)
                
                // Các điểm tiếp theo
                for (i in 1 until points.size) {
                    val point = points[i].split(",")
                    if (point.size == 2) {
                        val x = point[0].toFloatOrNull() ?: 0f
                        val y = point[1].toFloatOrNull() ?: 0f
                        path.lineTo(x, y)
                    }
                }
            }
            
            return path
        }
    }
}

