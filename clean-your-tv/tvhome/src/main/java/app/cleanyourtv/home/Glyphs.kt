package app.cleanyourtv.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Simple line-and-fill symbols in an iOS-like style, drawn in code so they stay sharp at any size.
 * They are original drawings, not Apple's icons or SF Symbols.
 */
enum class GlyphKind { TV, WIFI, GEAR, SUN, MOON, CLOUD, CLOUD_SUN, CLOUD_MOON, RAIN, SNOW, THUNDER, FOG }

class Glyph(private val kind: GlyphKind, private val color: Int = Color.WHITE) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val ox = b.left + (b.width() - s) / 2f
        val oy = b.top + (b.height() - s) / 2f
        // Draw on a 100 x 100 grid.
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s / 100f, s / 100f)
        stroke.color = color
        stroke.strokeWidth = 7f
        fill.color = color
        when (kind) {
            GlyphKind.TV -> tv(canvas)
            GlyphKind.WIFI -> wifi(canvas)
            GlyphKind.GEAR -> gear(canvas)
            GlyphKind.SUN -> sun(canvas, 50f, 50f, 20f, Color.rgb(255, 214, 10))
            GlyphKind.MOON -> moon(canvas, 50f, 50f, 26f)
            GlyphKind.CLOUD -> cloud(canvas, 0f)
            GlyphKind.CLOUD_SUN -> { sun(canvas, 36f, 34f, 14f, Color.rgb(255, 214, 10)); cloud(canvas, 8f) }
            GlyphKind.CLOUD_MOON -> { moon(canvas, 36f, 32f, 16f); cloud(canvas, 8f) }
            GlyphKind.RAIN -> { cloud(canvas, -10f); drops(canvas) }
            GlyphKind.SNOW -> { cloud(canvas, -10f); flakes(canvas) }
            GlyphKind.THUNDER -> { cloud(canvas, -10f); bolt(canvas) }
            GlyphKind.FOG -> fog(canvas)
        }
        canvas.restore()
    }

    private fun tv(c: Canvas) {
        c.drawRoundRect(RectF(14f, 22f, 86f, 70f), 8f, 8f, stroke)
        c.drawLine(36f, 82f, 64f, 82f, stroke)
        c.drawLine(50f, 70f, 50f, 82f, stroke)
    }

    private fun wifi(c: Canvas) {
        c.drawCircle(50f, 76f, 6f, fill)
        for ((i, r) in listOf(18f, 34f, 50f).withIndex()) {
            stroke.strokeWidth = 8f - i
            c.drawArc(RectF(50f - r, 76f - r, 50f + r, 76f + r), 225f, 90f, false, stroke)
        }
    }

    private fun gear(c: Canvas) {
        fill.color = color
        c.save()
        for (i in 0 until 8) {
            c.drawRoundRect(RectF(44f, 8f, 56f, 26f), 3f, 3f, fill)
            c.rotate(45f, 50f, 50f)
        }
        c.restore()
        c.drawCircle(50f, 50f, 30f, fill)
        // Punch the hub with the background colour of the dock tile.
        val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 124, 132) }
        c.drawCircle(50f, 50f, 13f, hub)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, tint: Int) {
        fill.color = tint
        stroke.color = tint
        stroke.strokeWidth = r * 0.3f
        c.drawCircle(cx, cy, r, fill)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val r1 = r * 1.45f
            val r2 = r * 1.85f
            c.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
                cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), stroke)
        }
        fill.color = color
        stroke.color = color
        stroke.strokeWidth = 7f
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        fill.color = Color.rgb(255, 236, 160)
        c.drawPath(p, fill)
        fill.color = color
    }

    private fun cloud(c: Canvas, dy: Float) {
        val p = Path().apply {
            addCircle(36f, 58f + dy, 16f, Path.Direction.CW)
            addCircle(56f, 48f + dy, 22f, Path.Direction.CW)
            addCircle(72f, 60f + dy, 14f, Path.Direction.CW)
            addRoundRect(RectF(20f, 58f + dy, 86f, 74f + dy), 8f, 8f, Path.Direction.CW)
        }
        fill.color = color
        c.drawPath(p, fill)
    }

    private fun drops(c: Canvas) {
        stroke.color = Color.rgb(100, 190, 255)
        stroke.strokeWidth = 6f
        for (x in listOf(34f, 52f, 70f)) c.drawLine(x, 74f, x - 6f, 90f, stroke)
        stroke.color = color
    }

    private fun flakes(c: Canvas) {
        fill.color = color
        for (x in listOf(34f, 52f, 70f)) c.drawCircle(x - 3f, 84f, 4.5f, fill)
    }

    private fun bolt(c: Canvas) {
        val p = Path().apply {
            moveTo(54f, 66f); lineTo(40f, 84f); lineTo(50f, 84f); lineTo(44f, 98f); lineTo(62f, 78f); lineTo(52f, 78f); close()
        }
        fill.color = Color.rgb(255, 214, 10)
        c.drawPath(p, fill)
        fill.color = color
    }

    private fun fog(c: Canvas) {
        cloud(c, -18f)
        stroke.strokeWidth = 6f
        c.drawLine(20f, 70f, 80f, 70f, stroke)
        c.drawLine(28f, 84f, 72f, 84f, stroke)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
