package app.cleanyourtv.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Glass drawing for every skin. Android TV 11 has no live blur for views, so glass is a translucent
 * fill that is brighter at the top, a thin bright rim and a soft shine, over a wallpaper with glowing
 * orbs that show through it.
 */
object Crystal {
    fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density

    /** A glass panel in the skin's colours; [focused] adds the accent rim. */
    fun panel(context: Context, skin: Skin, radiusDp: Float, focused: Boolean = false): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(skin.panelTop, skin.panelBottom)).apply {
            cornerRadius = dp(context, radiusDp)
            if (focused) setStroke(dp(context, 2.5f).toInt(), skin.accent)
            else setStroke(dp(context, 1f).toInt().coerceAtLeast(1), skin.rim)
        }

    fun roundRect(rect: RectF, radius: Float): Path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
}

/** The skin's wallpaper: a gradient with soft glowing orbs. */
class SkinWallpaper(private val skin: Skin) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orb = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        base.shader = LinearGradient(0f, 0f, w, h, skin.base, null, Shader.TileMode.CLAMP)
        canvas.drawRect(b, base)
        for (o in skin.orbs) {
            val cx = b.left + o.x * w
            val cy = b.top + o.y * h
            val r = o.r * maxOf(w, h)
            orb.shader = RadialGradient(cx, cy, r, o.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, orb)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/**
 * An Android TV style app card: a landscape rectangle with gently rounded corners. Apps with a TV
 * banner fill the card with it; others show their icon and name on a glass card. A glass shine sits on
 * top, and the skin's accent ring shows focus.
 */
class AppCard(context: Context, private val skin: Skin) : View(context) {
    var banner: Drawable? = null
        set(value) { field = value; invalidate() }
    var icon: Drawable? = null
        set(value) { field = value; invalidate() }
    var label: String = ""
        set(value) { field = value; invalidate() }
    var highlighted = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val d = { v: Float -> Crystal.dp(context, v) }
        val pad = d(4f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = d(10f)
        val path = Crystal.roundRect(rect, radius)

        canvas.save()
        canvas.clipPath(path)
        val b = banner
        if (b != null) {
            b.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            b.draw(canvas)
        } else {
            fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, skin.cardTop, skin.cardBottom, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, fill)
            val size = rect.height() * 0.48f
            val cx = rect.centerX()
            val top = rect.top + rect.height() * 0.12f
            icon?.let {
                it.setBounds((cx - size / 2).toInt(), top.toInt(), (cx + size / 2).toInt(), (top + size).toInt())
                it.draw(canvas)
            }
            textPaint.color = skin.text
            textPaint.textSize = d(13f)
            val text = TextUtilsCompat.ellipsize(label, textPaint, rect.width() - d(12f))
            canvas.drawText(text, cx, rect.bottom - d(12f), textPaint)
        }
        // Glass shine over the top third.
        shine.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.4f,
            Color.argb(60, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.4f, shine)
        canvas.restore()

        rim.strokeWidth = d(1f)
        rim.color = skin.rim
        canvas.drawPath(path, rim)

        if (highlighted) {
            ring.strokeWidth = d(3f)
            ring.color = skin.accent
            val o = d(2.5f)
            canvas.drawPath(Crystal.roundRect(RectF(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o), radius + o), ring)
        }
    }
}

/** Shortens text with an ellipsis so it fits [maxWidth]. */
object TextUtilsCompat {
    fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}

/**
 * A rounded-square glyph tile for the dock and the weather card: a colour gradient (or clear glass
 * when [colors] is null) with a white symbol.
 */
class GlyphTile(context: Context, private val skin: Skin) : View(context) {
    var glyph: Drawable? = null
        set(value) { field = value; invalidate() }
    var colors: IntArray? = null
        set(value) { field = value; invalidate() }
    var highlighted = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = Crystal.dp(context, 4f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = rect.width() * 0.22f
        val path = Crystal.roundRect(rect, radius)
        val c = colors ?: intArrayOf(skin.cardTop, skin.cardBottom)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, c, null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        glyph?.let {
            val inset = rect.width() * 0.2f
            it.setBounds((rect.left + inset).toInt(), (rect.top + inset).toInt(), (rect.right - inset).toInt(), (rect.bottom - inset).toInt())
            it.draw(canvas)
        }
        canvas.save()
        canvas.clipPath(path)
        shine.shader = LinearGradient(0f, rect.top, 0f, rect.centerY(), Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.centerY(), shine)
        canvas.restore()
        rim.strokeWidth = Crystal.dp(context, 1f)
        rim.color = skin.rim
        canvas.drawPath(path, rim)
        if (highlighted) {
            ring.strokeWidth = Crystal.dp(context, 3f)
            ring.color = skin.accent
            val o = Crystal.dp(context, 2f)
            canvas.drawPath(Crystal.roundRect(RectF(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o), radius + o), ring)
        }
    }
}
