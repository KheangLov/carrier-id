package com.carrierid.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Carrier ID's own icon set: 24 px grid, 1.8 px rounded strokes, with filled
 * versions for the selected tab. Tinted by Icon(), so the colour here is unused.
 */
object AppIcons {
    private fun icon(name: String, vararg parts: Pair<String, Boolean>): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        parts.forEach { (d, filled) ->
            if (filled) {
                b.addPath(addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }

    val Recents: ImageVector by lazy {
        icon("recents",
        "M12,3 a9,9 0 1,0 0.001,0 Z" to false,
        "M12,7.5 V12 L15,14" to false)
    }

    val RecentsFilled: ImageVector by lazy {
        icon("recents_f",
        "M12,2.5 a9.5,9.5 0 1,0 0.001,0 Z M11.1,7 H12.9 V11.45 L15.55,13.05 L14.62,14.59 L11.1,12.46 Z" to true)
    }

    val Person: ImageVector by lazy {
        icon("person",
        "M12,4.4 a3.6,3.6 0 1,0 0.001,0 Z" to false,
        "M4.8,20 C4.8,16.3 8,13.8 12,13.8 C16,13.8 19.2,16.3 19.2,20" to false)
    }

    val PersonFilled: ImageVector by lazy {
        icon("person_f",
        "M12,3.8 a4,4 0 1,0 0.001,0 Z" to true,
        "M4.3,19.9 C4.3,15.9 7.8,13.3 12,13.3 C16.2,13.3 19.7,15.9 19.7,19.9 Q19.7,20.9 18.7,20.9 H5.3 Q4.3,20.9 4.3,19.9 Z" to true)
    }

    val Sliders: ImageVector by lazy {
        icon("sliders",
        "M4,8 H6.6 M11.4,8 H20 M4,16 H12.6 M17.4,16 H20" to false,
        "M9,5.6 a2.4,2.4 0 1,0 0.001,0 Z" to false,
        "M15,13.6 a2.4,2.4 0 1,0 0.001,0 Z" to false)
    }

    val SlidersFilled: ImageVector by lazy {
        icon("sliders_f",
        "M4,8 H6.6 M11.4,8 H20 M4,16 H12.6 M17.4,16 H20" to false,
        "M9,5 a3,3 0 1,0 0.001,0 Z" to true,
        "M15,13 a3,3 0 1,0 0.001,0 Z" to true)
    }

    val Search: ImageVector by lazy {
        icon("search",
        "M10.8,4.5 a6.3,6.3 0 1,0 0.001,0 Z" to false,
        "M15.5,15.5 L20,20" to false)
    }

    val Phone: ImageVector by lazy {
        icon("phone",
        "M20.5,16.2 v2.6 a1.8,1.8 0 0,1 -1.96,1.8 a17.8,17.8 0 0,1 -7.77,-2.76 a17.5,17.5 0 0,1 -5.4,-5.4 a17.8,17.8 0 0,1 -2.76,-7.8 A1.8,1.8 0 0,1 4.4,2.7 h2.7 a1.8,1.8 0 0,1 1.8,1.55 a11.5,11.5 0 0,0 0.63,2.53 a1.8,1.8 0 0,1 -0.4,1.9 L8,9.8 a14.4,14.4 0 0,0 5.4,5.4 l1.14,-1.14 a1.8,1.8 0 0,1 1.9,-0.4 a11.5,11.5 0 0,0 2.53,0.63 a1.8,1.8 0 0,1 1.55,1.83 Z" to false)
    }

    val Close: ImageVector by lazy {
        icon("close",
        "M7,7 L17,17 M17,7 L7,17" to false)
    }

    val Layers: ImageVector by lazy {
        icon("layers",
        "M12,3.5 L20.5,8.2 L12,12.9 L3.5,8.2 Z" to false,
        "M3.5,12.2 L12,16.9 L20.5,12.2" to false,
        "M3.5,16.2 L12,20.9 L20.5,16.2" to false)
    }

    val Battery: ImageVector by lazy {
        icon("battery",
        "M5.2,7.5 H16.3 a2.2,2.2 0 0,1 2.2,2.2 V14.3 a2.2,2.2 0 0,1 -2.2,2.2 H5.2 a2.2,2.2 0 0,1 -2.2,-2.2 V9.7 a2.2,2.2 0 0,1 2.2,-2.2 Z" to false,
        "M21,10.5 V13.5" to false,
        "M11.3,9.6 L9.3,12.2 H12.3 L10.3,14.8" to false)
    }

    val Shield: ImageVector by lazy {
        icon("shield",
        "M12,3 L19,5.8 V11.2 C19,15.6 16,19.2 12,20.8 C8,19.2 5,15.6 5,11.2 V5.8 Z" to false,
        "M8.8,11.9 L11,14.1 L15.3,9.8" to false)
    }

    val Image: ImageVector by lazy {
        icon("image",
        "M6,4.5 H18 a2,2 0 0,1 2,2 V17.5 a2,2 0 0,1 -2,2 H6 a2,2 0 0,1 -2,-2 V6.5 a2,2 0 0,1 2,-2 Z" to false,
        "M9,8 a1.6,1.6 0 1,0 0.001,0 Z" to false,
        "M20,15 L15.5,10.5 L6,19.5" to false)
    }

    val Chevron: ImageVector by lazy {
        icon("chevron",
        "M9.5,6 L15.5,12 L9.5,18" to false)
    }

    val Missed: ImageVector by lazy {
        icon("missed",
        "M4,8 L10,14 L13.5,10.5 L18,15" to false,
        "M18,10.5 V15 H13.5" to false)
    }

    val Incoming: ImageVector by lazy {
        icon("incoming",
        "M17,7 L7,17" to false,
        "M7,10 V17 H14" to false)
    }

    val Outgoing: ImageVector by lazy {
        icon("outgoing",
        "M7,17 L17,7" to false,
        "M10,7 H17 V14" to false)
    }

    val Blocked: ImageVector by lazy {
        icon("blocked",
        "M12,3.5 a8.5,8.5 0 1,0 0.001,0 Z" to false,
        "M6,6 L18,18" to false)
    }

    val Back: ImageVector by lazy {
        icon("back",
        "M14.5,5.5 L8,12 L14.5,18.5" to false)
    }

    val Message: ImageVector by lazy {
        icon("message",
        "M5,5 H19 a1.5,1.5 0 0,1 1.5,1.5 V15 a1.5,1.5 0 0,1 -1.5,1.5 H10.5 L6.5,19.8 V16.5 H5 a1.5,1.5 0 0,1 -1.5,-1.5 V6.5 a1.5,1.5 0 0,1 1.5,-1.5 Z" to false)
    }

    val Note: ImageVector by lazy {
        icon("note",
        "M6.5,3.5 H14 L18.5,8 V19 a1.5,1.5 0 0,1 -1.5,1.5 H6.5 a1.5,1.5 0 0,1 -1.5,-1.5 V5 a1.5,1.5 0 0,1 1.5,-1.5 Z" to false,
        "M14,3.5 V8 H18.5" to false,
        "M8.5,12.5 H15 M8.5,16 H13" to false)
    }

    val Mic: ImageVector by lazy {
        icon("mic",
        "M12,3.5 a2.8,2.8 0 0,1 2.8,2.8 V11.5 a2.8,2.8 0 0,1 -5.6,0 V6.3 a2.8,2.8 0 0,1 2.8,-2.8 Z" to false,
        "M6.5,11 a5.5,5.5 0 0,0 11,0" to false,
        "M12,16.5 V20.5" to false)
    }

    val Warning: ImageVector by lazy {
        icon("warning",
        "M12,4 L21,19.5 H3 Z" to false,
        "M12,10 V13.8" to false,
        "M12,16.7 V16.8" to false)
    }

    val Globe: ImageVector by lazy {
        icon("globe",
        "M12,3.5 a8.5,8.5 0 1,0 0.001,0 Z" to false,
        "M3.5,12 H20.5" to false,
        "M12,3.5 C9.3,6.3 9.3,17.7 12,20.5 C14.7,17.7 14.7,6.3 12,3.5 Z" to false)
    }

    val Bell: ImageVector by lazy {
        icon("bell",
        "M6.5,16.5 V11 a5.5,5.5 0 0,1 11,0 V16.5 L19,18 H5 Z" to false,
        "M10,20.5 H14" to false)
    }

}
