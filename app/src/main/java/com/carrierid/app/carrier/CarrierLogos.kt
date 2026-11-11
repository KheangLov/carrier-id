package com.carrierid.app.carrier

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import kotlin.math.max

/**
 * Where carrier logos come from, in order:
 *  1. A logo the user picked inside the app (Settings → Networks → tap a carrier),
 *     saved as files/logos/<logoName>.png
 *  2. A logo bundled in res/drawable (logo_smart.png etc.)
 *  3. Otherwise the UI falls back to a coloured letter circle.
 */
object CarrierLogos {

    /** Bumped whenever a logo is added/removed so Compose screens redraw. */
    val version = mutableIntStateOf(0)

    private val cache = HashMap<Carrier, Bitmap?>()

    private fun file(ctx: Context, carrier: Carrier) =
        File(File(ctx.filesDir, "logos").apply { mkdirs() }, "${carrier.logoName}.png")

    @SuppressLint("DiscouragedApi")
    fun drawableId(context: Context, carrier: Carrier): Int =
        context.resources.getIdentifier(carrier.logoName, "drawable", context.packageName)

    fun hasCustom(ctx: Context, carrier: Carrier) = file(ctx, carrier).exists()

    /** The logo as a bitmap (user-picked first, then bundled), or null if none. */
    @Synchronized
    fun bitmap(ctx: Context, carrier: Carrier): Bitmap? = cache.getOrPut(carrier) {
        val f = file(ctx, carrier)
        when {
            f.exists() -> BitmapFactory.decodeFile(f.absolutePath)
            drawableId(ctx, carrier) != 0 ->
                BitmapFactory.decodeResource(ctx.resources, drawableId(ctx, carrier))
            else -> null
        }
    }

    /** Copies an image the user picked into app storage (max 384 px, on white). */
    fun save(ctx: Context, carrier: Carrier, uri: Uri): Boolean = try {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        }
        val scale = 384f / max(src.width, src.height)
        val w = (src.width * minOf(1f, scale)).toInt().coerceAtLeast(1)
        val h = (src.height * minOf(1f, scale)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        // Flatten transparent logos onto white so they look right in dark mode too
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
        file(ctx, carrier).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        invalidate(carrier)
        true
    } catch (e: Exception) {
        false
    }

    fun remove(ctx: Context, carrier: Carrier) {
        file(ctx, carrier).delete()
        invalidate(carrier)
    }

    @Synchronized
    private fun invalidate(carrier: Carrier) {
        cache.remove(carrier)
        version.intValue++
    }
}
