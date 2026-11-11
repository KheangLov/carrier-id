package com.carrierid.app.carrier

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns any logo image into a square that fills a round badge with no empty ring:
 *
 * 1. Trims the plain margin around the logo (white, transparent or any flat colour).
 * 2. Square-ish logos (app-icon style, e.g. a coloured rounded square) are
 *    centre-cropped so the colour covers the whole circle.
 * 3. Wide wordmarks would lose letters if cropped, so they're centred on a square
 *    filled with the logo's own background colour instead.
 */
object LogoShaper {

    private const val OUT = 256
    private const val COVER_MAX_RATIO = 1.35f

    fun toBadge(src: Bitmap): Bitmap {
        val bg = edgeColor(src)
        val box = contentBounds(src, bg) ?: Rect(0, 0, src.width, src.height)
        val w = box.width()
        val h = box.height()
        val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        if (max(w, h).toFloat() / min(w, h) <= COVER_MAX_RATIO) {
            // Cover: centre-crop the trimmed logo to a square
            val side = min(w, h)
            val l = box.left + (w - side) / 2
            val t = box.top + (h - side) / 2
            canvas.drawBitmap(src, Rect(l, t, l + side, t + side), Rect(0, 0, OUT, OUT), paint)
        } else {
            // Wide wordmark: its own background colour, logo fitted across the middle
            canvas.drawColor(if (Color.alpha(bg) < 40) Color.WHITE else bg or 0xFF000000.toInt())
            val pad = OUT * 0.1f
            val scale = min((OUT - 2 * pad) / w, (OUT - 2 * pad) / h)
            val dw = w * scale
            val dh = h * scale
            val dst = RectF((OUT - dw) / 2, (OUT - dh) / 2, (OUT + dw) / 2, (OUT + dh) / 2)
            canvas.drawBitmap(src, box, dst, paint)
        }
        return out
    }

    /** Most common colour among the four corners = the margin colour. */
    private fun edgeColor(b: Bitmap): Int {
        val c = listOf(
            b.getPixel(0, 0), b.getPixel(b.width - 1, 0),
            b.getPixel(0, b.height - 1), b.getPixel(b.width - 1, b.height - 1),
        )
        return c.maxBy { x -> c.count { close(it, x) } }
    }

    /** Bounding box of pixels that differ from the margin colour, or null if none. */
    private fun contentBounds(b: Bitmap, bg: Int): Rect? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (!close(px[row + x], bg)) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        // Ignore specks: require a meaningful logo area
        if (right - left < w / 10 || bottom - top < h / 10) return null
        return Rect(left, top, right + 1, bottom + 1)
    }

    private fun close(a: Int, b: Int): Boolean {
        val aa = Color.alpha(a)
        val ba = Color.alpha(b)
        if (aa < 24 && ba < 24) return true          // both transparent
        if (abs(aa - ba) > 48) return false
        val d = abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))
        return d < 48
    }
}
